package com.example.dk250403.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.dk250403.network.BalanceRequest
import com.example.dk250403.network.BalanceResponse
import com.example.dk250403.network.LsWebSocketManager
import com.example.dk250403.network.RetrofitClient
import com.example.dk250403.network.StockHolding
import com.example.dk250403.network.T0424InBlock
import com.example.dk250403.util.TokenManager
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

class AssetsViewModel(application: Application) : AndroidViewModel(application) {
    private val tokenManager = TokenManager.getInstance(application)
    private val auth = FirebaseAuth.getInstance()

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _registeredAccounts = MutableStateFlow<List<String>>(emptyList())
    val registeredAccounts: StateFlow<List<String>> = _registeredAccounts.asStateFlow()

    private val _selectedAccount = MutableStateFlow<String>("")
    val selectedAccount: StateFlow<String> = _selectedAccount.asStateFlow()

    private var fetchJob: Job? = null
    private var lastRequestTime = 0L

    private val subscribedCodes = mutableSetOf<String>() // 여기에는 순수 숫자 6자리 코드만 저장

    init {
        observeRealtimeWebSocket()
    }

    fun clearData() {
        _uiState.value = UiState.Idle
        _registeredAccounts.value = emptyList()
        _selectedAccount.value = ""
        fetchJob?.cancel()

        // 💡 구독 해지 (US3 통합 채널)
        subscribedCodes.forEach { code ->
            val trKey = "U$code   " // U + 6자리 + 공백3칸
            LsWebSocketManager.unsubscribe("US3", trKey)
        }
        subscribedCodes.clear()
    }

    fun initAssets() {
        val currentUid = auth.currentUser?.uid ?: ""

        if (currentUid.isEmpty()) {
            _uiState.value = UiState.Error("로그인 정보가 없습니다.")
            return
        }

        clearData()

        val accounts = tokenManager.getAccountNumbers(currentUid)
        _registeredAccounts.value = accounts

        if (accounts.isEmpty()) {
            _uiState.value = UiState.Error("등록된 계좌가 없습니다. 계정 설정에서 앱키와 계좌번호를 먼저 등록해주세요.")
            return
        }

        requestBalanceForAccount(accounts.first())
    }

    fun requestBalanceForAccount(accountNumber: String) {
        val currentUid = auth.currentUser?.uid ?: ""
        if (currentUid.isEmpty()) return

        val currentTime = System.currentTimeMillis()
        if (currentTime - lastRequestTime < 1000) {
            _uiState.value = UiState.Error("조회 요청이 너무 빠릅니다.\n잠시 후 다시 눌러주세요.")
            return
        }
        lastRequestTime = currentTime

        _selectedAccount.value = accountNumber

        if (!accountNumber.all { it.isDigit() } || accountNumber.length !in 10..11) {
            _uiState.value = UiState.Error("조회할 수 없는 계좌입니다.\n계좌번호를 다시 확인해 주세요.")
            return
        }

        val accountInfo = tokenManager.getCredentialsForAccount(currentUid, accountNumber)
        if (accountInfo == null || accountInfo.appKey.isEmpty() || accountInfo.secretKey.isEmpty()) {
            _uiState.value = UiState.Error("[$accountNumber] 계좌에 대한 앱키 설정이 누락되었습니다.")
            return
        }

        val token = tokenManager.getAccessToken(currentUid, accountNumber)

        if (!token.isNullOrEmpty()) {
            fetchBalance(currentUid, accountNumber, token)
        } else {
            issueAccessTokenAndFetch(currentUid, accountInfo.appKey, accountInfo.secretKey, accountNumber)
        }
    }

    private fun issueAccessTokenAndFetch(uid: String, appKey: String, appSecret: String, targetAccount: String) {
        fetchJob?.cancel()
        fetchJob = viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                val response = RetrofitClient.lsApi.getAccessToken(appKey = appKey, appSecretKey = appSecret)
                if (response.isSuccessful && response.body() != null) {
                    val token = response.body()!!.accessToken
                    tokenManager.saveAccessToken(uid, targetAccount, token)
                    fetchBalance(uid, targetAccount, token)
                } else {
                    _uiState.value = UiState.Error("[$targetAccount]\n토큰 발급 실패 (앱키/시크릿키를 확인하세요)")
                }
            } catch (e: Exception) {
                _uiState.value = UiState.Error("[$targetAccount]\n네트워크 오류: ${e.localizedMessage}")
            }
        }
    }

    private fun fetchBalance(uid: String, accountNumber: String, token: String) {
        fetchJob?.cancel()
        fetchJob = viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                val request = BalanceRequest(T0424InBlock(accno = accountNumber))
                val response = RetrofitClient.lsApi.getAccountBalance(token = "Bearer $token", request = request)

                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    if (body.rspCd != "00000" && body.rspCd.isNotEmpty()) {
                        val errorMsg = if (body.rspMsg.isNotEmpty()) body.rspMsg else "해당 계좌는 조회가 되지 않습니다."
                        _uiState.value = UiState.Error("[$accountNumber]\n$errorMsg")
                    } else {
                        _uiState.value = UiState.BalanceLoaded(body)

                        LsWebSocketManager.connect(token)
                        subscribeToHoldings(body.holdings ?: emptyList())
                    }
                } else {
                    val errorStr = response.errorBody()?.string() ?: ""
                    var parsedMsg = errorStr
                    try {
                        val jsonObj = JSONObject(errorStr)
                        parsedMsg = jsonObj.optString("rsp_msg", "조회에 실패했습니다.")
                    } catch (e: Exception) {}

                    if (response.code() == 401 || parsedMsg.contains("만료") || parsedMsg.contains("유효하지 않")) {
                        tokenManager.clearAccessToken(uid, accountNumber)
                        _uiState.value = UiState.Error("[$accountNumber]\n토큰이 만료되었습니다. 다시 탭을 눌러주세요.")
                    } else {
                        _uiState.value = UiState.Error("[$accountNumber] 잔고 조회 실패:\n$parsedMsg")
                    }
                }
            } catch (e: Exception) {
                _uiState.value = UiState.Error("[$accountNumber]\n통신 오류: ${e.localizedMessage}")
            }
        }
    }

    // 💡 수정: 통합 체결(US3) 규격에 맞추어 단축코드 7자리 + 공백 3자리 생성
    private fun subscribeToHoldings(holdings: List<StockHolding>) {
        subscribedCodes.forEach { code ->
            val trKey = "U$code   "
            LsWebSocketManager.unsubscribe("US3", trKey)
        }
        subscribedCodes.clear()

        holdings.forEach { stock ->
            val code = stock.itemCode.takeLast(6)
            val trKey = "U$code   "
            LsWebSocketManager.subscribe("US3", trKey)
            subscribedCodes.add(code)
        }

        // 2. 🚨 통신망 검증을 위한 삼성전자 강제 구독 (코스피 체결)
        //val testCode = "005930"
        //val testtrKey = "U$testCode   "
        //LsWebSocketManager.subscribe("US3", testtrKey)
        //subscribedCodes.add(testCode)
    }

    // 💡 수정: US3 데이터 수신 및 종목 코드 안전 추출
    private fun observeRealtimeWebSocket() {
        viewModelScope.launch {
            LsWebSocketManager.realtimeDataFlow.collect { json ->
                val currentState = _uiState.value
                if (currentState is UiState.BalanceLoaded) {
                    try {
                        val header = json.optJSONObject("header")
                        val body = json.optJSONObject("body")
                        if (header != null && body != null) {
                            val trCd = header.optString("tr_cd")

                            // 통합 체결 데이터 채널인 경우에만 처리
                            if (trCd == "US3") {
                                val rawStockCode = body.optString("shcode") // 서버에서 U058730 혹은 기타 형태로 내려올 수 있음
                                val currentPriceStr = body.optString("price")

                                // 숫자만 추출하여 뒷 6자리로 종목 코드 정규화
                                val stockCode = rawStockCode.filter { it.isDigit() }.takeLast(6)

                                if (stockCode.isNotEmpty() && currentPriceStr.isNotEmpty()) {
                                    val newPrice = currentPriceStr.replace(",", "").toLongOrNull() ?: return@collect
                                    updateStockPrice(currentState, stockCode, newPrice)
                                }
                            }
                        }
                    } catch (e: Exception) {}
                }
            }
        }
    }

    private fun updateStockPrice(currentState: UiState.BalanceLoaded, stockCode: String, newPrice: Long) {
        val oldBalance = currentState.balance
        val oldHoldings = oldBalance.holdings ?: return

        var isChanged = false
        val newHoldings = oldHoldings.map { stock ->
            if (stock.itemCode.takeLast(6) == stockCode && stock.currentPrice != newPrice) {
                isChanged = true

                val returnRate = if (stock.averagePrice > 0) {
                    ((newPrice - stock.averagePrice).toDouble() / stock.averagePrice.toDouble()) * 100.0
                } else {
                    stock.returnRate
                }

                stock.copy(currentPrice = newPrice, returnRate = returnRate)
            } else {
                stock
            }
        }

        if (isChanged) {
            val newBalance = oldBalance.copy(holdings = newHoldings)
            _uiState.value = UiState.BalanceLoaded(newBalance)
        }
    }

    sealed class UiState {
        object Idle : UiState()
        object Loading : UiState()
        data class BalanceLoaded(val balance: BalanceResponse) : UiState()
        data class Error(val message: String) : UiState()
    }
}