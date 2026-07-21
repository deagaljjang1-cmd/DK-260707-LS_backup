package com.example.dk250403.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.dk250403.network.BalanceRequest
import com.example.dk250403.network.BalanceResponse
import com.example.dk250403.network.CSPAT00701InBlock1
import com.example.dk250403.network.CSPAT00801InBlock1
import com.example.dk250403.network.CancelOrderRequest
import com.example.dk250403.network.LsWebSocketManager
import com.example.dk250403.network.ModifyOrderRequest
import com.example.dk250403.network.RetrofitClient
import com.example.dk250403.network.StockHolding
import com.example.dk250403.network.T0424InBlock
import com.example.dk250403.network.T8436InBlock
import com.example.dk250403.network.T8436Request
import com.example.dk250403.util.TokenManager
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.Calendar

// 💡 1. 정렬 기준 Enum 추가
enum class SortType {
    NAME_ASC,        // 종목명 순 (가나다)
    NAME_DESC,       // 종목명 내림차순
    RETURN_DESC,     // 수익률 순 (높은 순)
    RETURN_ASC,      // 수익률 오름차순 (낮은 순)
    PROFIT_DESC,      // 수익금 순 (많은 순)
    PROFIT_ASC       // 수익금 오름차순 (적은 순)
}

class AssetsViewModel(application: Application) : AndroidViewModel(application) {
    private val tokenManager = TokenManager.getInstance(application)
    private val auth = FirebaseAuth.getInstance()

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _registeredAccounts = MutableStateFlow<List<String>>(emptyList())
    val registeredAccounts: StateFlow<List<String>> = _registeredAccounts.asStateFlow()

    private val _selectedAccount = MutableStateFlow<String>("")
    val selectedAccount: StateFlow<String> = _selectedAccount.asStateFlow()

    // 💡 2. 현재 선택된 정렬 상태
    private val _sortType = MutableStateFlow(SortType.NAME_ASC)
    val sortType: StateFlow<SortType> = _sortType.asStateFlow()

    private var fetchJob: Job? = null
    private var lastRequestTime = 0L

    private val subscribedCodes = mutableSetOf<String>()

    // 💡 미체결 내역을 담아둘 상태 변수
    private val _unexecutedOrders = MutableStateFlow<List<com.example.dk250403.network.T0425OutBlock1>>(emptyList())
    val unexecutedOrders: StateFlow<List<com.example.dk250403.network.T0425OutBlock1>> = _unexecutedOrders.asStateFlow()

    private val _isUnexecutedLoading = MutableStateFlow(false)
    val isUnexecutedLoading: StateFlow<Boolean> = _isUnexecutedLoading.asStateFlow()

    // 클래스 상단 변수 선언부에 종목 사전을 저장할 StateFlow 추가
    private val _stockMasterMap = MutableStateFlow<Map<String, String>>(emptyMap())
    val stockMasterMap: StateFlow<Map<String, String>> = _stockMasterMap.asStateFlow()

    private val _executedOrders = MutableStateFlow<List<com.example.dk250403.network.T0425OutBlock1>>(emptyList())
    val executedOrders: StateFlow<List<com.example.dk250403.network.T0425OutBlock1>> = _executedOrders.asStateFlow()

    private val _isExecutedLoading = MutableStateFlow(false)
    val isExecutedLoading: StateFlow<Boolean> = _isExecutedLoading.asStateFlow()

    private val _todayTotalBuy = MutableStateFlow(0L)
    val todayTotalBuy: StateFlow<Long> = _todayTotalBuy.asStateFlow()

    private val _todayTotalSell = MutableStateFlow(0L)
    val todayTotalSell: StateFlow<Long> = _todayTotalSell.asStateFlow()

    init {
        observeRealtimeWebSocket()
    }

    // 💡 종목 마스터 데이터를 받아와 사전을 만드는 함수 (파라미터 추가)
    private fun fetchStockMaster(uid: String, account: String) {
        if (_stockMasterMap.value.isNotEmpty()) return

        val token = tokenManager.getAccessToken(uid, account)
        if (token.isNullOrEmpty()) return

        viewModelScope.launch {
            try {
                val request = T8436Request(T8436InBlock(gubun = "0"))
                val response = com.example.dk250403.network.RetrofitClient.lsApi.fetchStockMaster(
                    token = "Bearer $token",
                    request = request
                )

                if (response.isSuccessful) {
                    val outBlock = response.body()?.t8436OutBlock
                    if (outBlock != null) {
                        val tempMap = mutableMapOf<String, String>()
                        outBlock.forEach { stock ->
                            tempMap[stock.shcode] = stock.hname
                            tempMap[stock.expcode] = stock.hname
                        }
                        _stockMasterMap.value = tempMap
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun clearData() {
        _uiState.value = UiState.Idle
        _registeredAccounts.value = emptyList()
        _selectedAccount.value = ""
        fetchJob?.cancel()

        subscribedCodes.forEach { code ->
            val trKey = "U$code   "
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
        val firstAccount = accounts.first()
        requestBalanceForAccount(firstAccount)

        // 💡 여기에 추가: 앱 실행 직후 백그라운드에서 종목 사전 미리 다운로드!
        fetchStockMaster(currentUid, firstAccount)
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
                        // 💡 최초 조회 시점에 정렬 기준 적용
                        val sortedHoldings = sortHoldings(body.holdings ?: emptyList(), _sortType.value)
                        _uiState.value = UiState.BalanceLoaded(body.copy(holdings = sortedHoldings))

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

    // 💡 3. 정렬 상태 변경 함수
    fun setSortType(type: SortType) {
        _sortType.value = type
        val currentState = _uiState.value
        if (currentState is UiState.BalanceLoaded) {
            val oldBalance = currentState.balance
            val sortedHoldings = sortHoldings(oldBalance.holdings ?: emptyList(), type)
            _uiState.value = UiState.BalanceLoaded(oldBalance.copy(holdings = sortedHoldings))
        }
    }

    // 💡 4. 실제 정렬 처리 로직
    private fun sortHoldings(holdings: List<StockHolding>, type: SortType): List<StockHolding> {
        return when (type) {
            SortType.NAME_ASC -> holdings.sortedBy { it.itemName }
            SortType.NAME_DESC -> holdings.sortedByDescending { it.itemName }
            SortType.RETURN_DESC -> holdings.sortedByDescending { it.returnRate }
            SortType.RETURN_ASC -> holdings.sortedBy { it.returnRate }
            SortType.PROFIT_DESC -> holdings.sortedByDescending {
                (it.currentPrice - it.averagePrice) * it.quantity
            }
            SortType.PROFIT_ASC -> holdings.sortedBy { // 💡 오름차순 추가
                (it.currentPrice - it.averagePrice) * it.quantity
            }
        }
    }

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
    }

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

                            if (trCd == "US3") {
                                val rawStockCode = body.optString("shcode")
                                val currentPriceStr = body.optString("price")
                                val changeRateStr = body.optString("drate")
                                // 💡 추가: LS웹소켓에서 부호와 전일대비금액 추출
                                val signStr = body.optString("sign")
                                val changeStr = body.optString("change")
                                val stockCode = rawStockCode.filter { it.isDigit() }.takeLast(6)

                                if (stockCode.isNotEmpty() && currentPriceStr.isNotEmpty()) {
                                    val newPrice = currentPriceStr.replace(",", "").toLongOrNull() ?: return@collect
                                    val newChangeRate = changeRateStr.replace(",", "").toDoubleOrNull() ?: 0.0
                                    val changeAmt = changeStr.replace(",", "").toLongOrNull() ?: 0L

                                    // 💡 부호(1,2:상승 / 4,5:하락)에 따라 정확한 전일종가 역산
                                    val baseYesterdayPrice = when (signStr) {
                                        "1", "2" -> newPrice - changeAmt
                                        "4", "5" -> newPrice + changeAmt
                                        else -> newPrice
                                    }

                                    // 💡 baseYesterdayPrice 파라미터 추가 전송
                                    updateStockPrice(currentState, stockCode, newPrice, newChangeRate, baseYesterdayPrice)
                                }
                            }
                        }
                    } catch (e: Exception) {}
                }
            }
        }
    }

    // 💡 5. 총자산 실시간 계산 및 자동 정렬 적용
    private fun updateStockPrice(
        currentState: UiState.BalanceLoaded,
        stockCode: String,
        newPrice: Long,
        newChangeRate: Double,
        baseYesterdayPrice: Long
    ) {
        val oldBalance = currentState.balance
        val oldHoldings = oldBalance.holdings ?: return

        var isChanged = false
        var newTotalHoldingsValue = 0L // 보유 주식의 총 평가금액

        val newHoldings = oldHoldings.map { stock ->
            // 💡 수정: 가격이 다르거나, 등락률이 다를 때(초기 0.00%에서 확정값으로 바뀔 때) 모두 업데이트 허용
            if (stock.itemCode.takeLast(6) == stockCode && (stock.currentPrice != newPrice || stock.todayChangeRate != newChangeRate)) {
                isChanged = true

                val returnRate = if (stock.averagePrice > 0) {
                    ((newPrice - stock.averagePrice).toDouble() / stock.averagePrice.toDouble()) * 100.0
                } else {
                    stock.returnRate
                }

                // 💡 수정 2: stock.copy 괄호 안에 todayChangeRate = newChangeRate 추가
                val updatedStock = stock.copy(currentPrice = newPrice, returnRate = returnRate, todayChangeRate = newChangeRate, yesterdayPrice = baseYesterdayPrice)
                newTotalHoldingsValue += (updatedStock.currentPrice * updatedStock.quantity)
                updatedStock
            } else {
                newTotalHoldingsValue += (stock.currentPrice * stock.quantity)
                stock
            }
        }

        if (isChanged) {
            val sortedHoldings = sortHoldings(newHoldings, _sortType.value)

            val oldSummary = oldBalance.summary

            // 💡 1. 기존 보유 주식들의 총 평가금액 계산
            val oldTotalHoldingsValue = oldHoldings.sumOf { it.currentPrice * it.quantity }

            // 💡 2. 기존 추정 순자산에서 기존 주식 평가금액을 빼서 '순수 예수금(현금)' 역산
            val cashBalance = (oldSummary?.totalEvaluationAmount ?: 0L) - oldTotalHoldingsValue

            // 💡 3. 새로운 추정 순자산 = 역산한 예수금 + 새로운 주식 총 평가금액
            val newTotalEvaluationAmount = cashBalance + newTotalHoldingsValue

            val newSummary = oldSummary?.copy(
                totalEvaluationAmount = newTotalEvaluationAmount
            )

            val newBalance = oldBalance.copy(
                holdings = sortedHoldings,
                summary = newSummary
            )
            _uiState.value = UiState.BalanceLoaded(newBalance)
        }
    }

    // 💡 6. [t1102 연동] 장 마감 후 확정 시세 단건 호출 (새로 추가된 로직)
    fun fetchInitialPrice(stockCode: String) {
        val currentUid = auth.currentUser?.uid ?: return
        val account = _selectedAccount.value
        if (account.isEmpty()) return
        val token = tokenManager.getAccessToken(currentUid, account) ?: return

        viewModelScope.launch {
            try {
                // T1102Request는 LsAuthModels.kt 또는 network 패키지에 정의한 클래스를 호출
                val request = com.example.dk250403.network.T1102Request(
                    com.example.dk250403.network.T1102InBlock(shcode = stockCode, exchgubun = "U")
                )
                val response = RetrofitClient.lsApi.getStockCurrentPrice(
                    token = "Bearer $token",
                    request = request
                )

                if (response.isSuccessful) {
                    response.body()?.t1102OutBlock?.let { outBlock ->
                        val currentState = _uiState.value
                        if (currentState is UiState.BalanceLoaded) {
                            // 💡 5번의 웹소켓용 상태 갱신 함수를 똑같이 재활용하여 바구니에 확정값을 꽂아줍니다!
                            updateStockPrice(
                                currentState = currentState,
                                stockCode = stockCode,
                                newPrice = outBlock.price,
                                newChangeRate = outBlock.diff,
                                baseYesterdayPrice = outBlock.recprice
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }


    // =======================================================
    // 💡 [실제 주문 전송 로직] UI에서 받아온 데이터를 API 규격으로 변환해서 쏩니다.
    // =======================================================
    fun submitStockOrder(
        stockCode: String,
        tradeType: com.example.dk250403.ui.screens.TradeType,
        orderType: com.example.dk250403.ui.screens.OrderType,
        price: Long,
        quantity: Long,
        onResult: (Boolean, String) -> Unit
    ) {
        val currentUid = auth.currentUser?.uid ?: return
        val account = _selectedAccount.value

        if (account.isEmpty()) {
            onResult(false, "계좌를 먼저 선택해주세요.")
            return
        }

        val token = tokenManager.getAccessToken(currentUid, account)
        if (token.isNullOrEmpty()) {
            onResult(false, "인증 토큰이 없습니다. 계좌를 다시 조회해주세요.")
            return
        }

        // --- 💡 2-1. 시간대별 스마트 라우팅 (거래소 판별) ---
        val calendar = Calendar.getInstance()
        val currentTimeHHmm = calendar.get(Calendar.HOUR_OF_DAY) * 100 + calendar.get(Calendar.MINUTE)

        // 08:00~08:49 또는 15:40~19:59 인 경우 NXT, 그 외는 KRX로 세팅
        val targetMbrNo = if ((currentTimeHHmm in 800..849) || (currentTimeHHmm in 1540..1999)) {
            "NXT"
        } else {
            "KRX"
        }
        // ---------------------------------------------------

        val bnsTpCode = if (tradeType == com.example.dk250403.ui.screens.TradeType.BUY) "2" else "1"

        // 💡 2-2. 2차 방어 로직: 만약 NXT 라우팅인데 시장가(MARKET)가 넘어왔다면 안전을 위해 강제로 지정가(LIMIT)로 덮어씌움
        var finalOrderType = orderType
        if (targetMbrNo == "NXT" && orderType == com.example.dk250403.ui.screens.OrderType.MARKET) {
            finalOrderType = com.example.dk250403.ui.screens.OrderType.LIMIT
        }

        val ordprcPtnCode = if (finalOrderType == com.example.dk250403.ui.screens.OrderType.LIMIT) "00" else "03"
        val finalPrice = if (finalOrderType == com.example.dk250403.ui.screens.OrderType.MARKET) 0L else price

        val inBlock = com.example.dk250403.network.CSPAT00601InBlock1(
            isuNo = stockCode,
            ordQty = quantity,
            ordPrc = finalPrice,
            bnsTpCode = bnsTpCode,
            ordprcPtnCode = ordprcPtnCode,
            mbrNo = targetMbrNo // 💡 시간에 따라 결정된 거래소 세팅
        )
        val request = com.example.dk250403.network.OrderRequest(inBlock)

        viewModelScope.launch {
            try {
                val response = com.example.dk250403.network.RetrofitClient.lsApi.submitOrder(
                    token = "Bearer $token",
                    request = request
                )

                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    val orderNo = body.outBlock2?.ordNo ?: 0L
                    val responseMessage = body.rsp_msg ?: ""

                    if (body.rsp_cd == "00000" || orderNo > 0L || responseMessage.contains("완료") || responseMessage.contains("접수")) {
                        onResult(true, "$responseMessage (주문번호: $orderNo)")
                    } else {
                        onResult(false, "주문 거절: $responseMessage")
                    }
                } else {
                    onResult(false, "통신 상태가 원활하지 않습니다.")
                }
            } catch (e: Exception) {
                onResult(false, "앱 내부 오류가 발생했습니다.")
            }
        }
    }


    // =======================================================
    // 💡 [미체결 내역 조회 로직]
    // =======================================================
    fun fetchUnexecutedOrders() {
        val currentUid = auth.currentUser?.uid ?: return
        val account = _selectedAccount.value
        if (account.isEmpty()) return

        val token = tokenManager.getAccessToken(currentUid, account)
        if (token.isNullOrEmpty()) return

        // 💡 여기에 추가: 토큰과 계좌가 모두 준비된 확실한 시점에 사전 다운로드!
        // 💡 파라미터 추가
        fetchStockMaster(currentUid, account)

        _isUnexecutedLoading.value = true

        viewModelScope.launch {
            try {
                val request = com.example.dk250403.network.T0425Request(
                    com.example.dk250403.network.T0425InBlock() // 기본값이 모두 설정되어 있으므로 빈 괄호
                )
                val response = com.example.dk250403.network.RetrofitClient.lsApi.getUnexecutedOrders(
                    token = "Bearer $token",
                    request = request
                )

                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    if ((body.rsp_cd == "00000" || body.rsp_cd?.contains("00156") == true) && body.unexecutedList != null) {
                        _unexecutedOrders.value = body.unexecutedList
                    } else {
                        _unexecutedOrders.value = emptyList()
                    }
                } else {
                    _unexecutedOrders.value = emptyList()
                }
            } catch (e: Exception) {
                _unexecutedOrders.value = emptyList()
            } finally {
                _isUnexecutedLoading.value = false
            }
        }
    }

    // =======================================================
    // 💡 [체결 내역 조회 로직]
    // =======================================================
    fun fetchExecutedOrders() {
        val currentUid = auth.currentUser?.uid ?: return
        val account = _selectedAccount.value
        if (account.isEmpty()) return

        val token = tokenManager.getAccessToken(currentUid, account)
        if (token.isNullOrEmpty()) return

        fetchStockMaster(currentUid, account)

        _isExecutedLoading.value = true

        viewModelScope.launch {
            try {
                // 💡 chegb = "1" (체결 내역) 으로 세팅하여 호출
                val request = com.example.dk250403.network.T0425Request(
                    com.example.dk250403.network.T0425InBlock(chegb = "1")
                )
                val response = com.example.dk250403.network.RetrofitClient.lsApi.getUnexecutedOrders(
                    token = "Bearer $token",
                    request = request
                )

                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    if ((body.rsp_cd == "00000" || body.rsp_cd?.contains("00156") == true) && body.unexecutedList != null) {
                        val list = body.unexecutedList
                        _executedOrders.value = list

                        // 💡 당일 총 매수/매도 금액 계산 (주문단가가 아닌 체결단가 기준)
                        var buySum = 0L
                        var sellSum = 0L
                        list.forEach { order ->
                            // 💡 변경: order.price -> order.cheprice
                            val amount = order.cheprice * order.cheqty
                            if (order.medosu.contains("매수") || order.medosu == "2") {
                                buySum += amount
                            } else {
                                sellSum += amount
                            }
                        }
                        _todayTotalBuy.value = buySum
                        _todayTotalSell.value = sellSum

                    } else {
                        _executedOrders.value = emptyList()
                        _todayTotalBuy.value = 0L
                        _todayTotalSell.value = 0L
                    }
                } else {
                    _executedOrders.value = emptyList()
                }
            } catch (e: Exception) {
                _executedOrders.value = emptyList()
            } finally {
                _isExecutedLoading.value = false
            }
        }
    }


    // =======================================================
    // 💡 [개별 취소] 단건 주문 취소 전송
    // =======================================================
    fun cancelSingleOrder(
        orgOrdNo: Long,
        stockCode: String,
        onResult: (Boolean, String) -> Unit
    ) {
        val currentUid = auth.currentUser?.uid ?: return
        val account = _selectedAccount.value
        val token = tokenManager.getAccessToken(currentUid, account) ?: return

        viewModelScope.launch {
            try {
                val request = CancelOrderRequest(
                    CSPAT00801InBlock1(orgOrdNo = orgOrdNo, isuNo = stockCode, ordQty = 0)
                )
                val response = com.example.dk250403.network.RetrofitClient.lsApi.cancelOrder(
                    token = "Bearer $token",
                    request = request
                )

                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    if (body.rsp_cd == "00000" || body.rsp_msg?.contains("완료") == true) {
                        onResult(true, "주문이 취소되었습니다.")
                        fetchUnexecutedOrders() // 성공 후 리스트 새로고침
                    } else {
                        onResult(false, "취소 거절: ${body.rsp_msg}")
                    }
                } else {
                    onResult(false, "통신 상태가 원활하지 않습니다.")
                }
            } catch (e: Exception) {
                onResult(false, "앱 내부 오류가 발생했습니다.")
            }
        }
    }

    // =======================================================
    // 💡 [일괄 취소] 선택된 여러 건의 주문을 순차적으로 전송 (TPS 제한 방어)
    // =======================================================
    fun cancelSelectedOrders(
        selectedOrderNos: Set<Long>,
        onComplete: (successCount: Int, failCount: Int) -> Unit
    ) {
        val currentUid = auth.currentUser?.uid ?: return
        val account = _selectedAccount.value
        val token = tokenManager.getAccessToken(currentUid, account) ?: return

        // 현재 미체결 리스트에서 선택된 번호의 주문만 걸러냄
        val ordersToCancel = _unexecutedOrders.value.filter { selectedOrderNos.contains(it.ordno) }
        if (ordersToCancel.isEmpty()) return

        _isUnexecutedLoading.value = true // 로딩 인디케이터 ON

        viewModelScope.launch {
            var successCount = 0
            var failCount = 0

            for (order in ordersToCancel) {
                try {
                    val request = CancelOrderRequest(
                        CSPAT00801InBlock1(
                            orgOrdNo = order.ordno,
                            isuNo = order.expcode,
                            ordQty = 0
                        )
                    )
                    val response = com.example.dk250403.network.RetrofitClient.lsApi.cancelOrder(
                        token = "Bearer $token",
                        request = request
                    )

                    if (response.isSuccessful && response.body()?.rsp_cd == "00000") {
                        successCount++
                    } else {
                        failCount++
                    }
                } catch (e: Exception) {
                    failCount++
                }

                // 🚨 중요: 증권사 API 초당 전송 제한(TPS)을 피하기 위해 주문 1건당 0.3초 대기
                kotlinx.coroutines.delay(400)
            }

            // 모든 취소 전송이 끝나면 미체결 리스트를 최신화하고 UI로 결과 반환
            fetchUnexecutedOrders()
            onComplete(successCount, failCount)
        }
    }

    // =======================================================
    // 💡 [개별 정정] 단건 주문 정정 전송
    // =======================================================
    fun modifySingleOrder(
        orgOrdNo: Long,
        stockCode: String,
        newQty: Long,
        newPrice: Long,
        onResult: (Boolean, String) -> Unit
    ) {
        val currentUid = auth.currentUser?.uid ?: return
        val account = _selectedAccount.value
        val token = tokenManager.getAccessToken(currentUid, account) ?: return

        viewModelScope.launch {
            try {
                val request = ModifyOrderRequest(
                    CSPAT00701InBlock1(
                        orgOrdNo = orgOrdNo,
                        isuNo = stockCode,
                        ordQty = newQty,
                        ordPrc = newPrice
                    )
                )
                val response = com.example.dk250403.network.RetrofitClient.lsApi.modifyOrder(
                    token = "Bearer $token",
                    request = request
                )

                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    if (body.rsp_cd == "00000" || body.rsp_msg?.contains("완료") == true) {
                        onResult(true, "주문이 정정되었습니다.")
                        fetchUnexecutedOrders() // 성공 후 리스트 새로고침
                    } else {
                        onResult(false, "정정 거절: ${body.rsp_msg}")
                    }
                } else {
                    onResult(false, "통신 상태가 원활하지 않습니다.")
                }
            } catch (e: Exception) {
                onResult(false, "앱 내부 오류가 발생했습니다.")
            }
        }
    }


    // 💡 정정 바텀 시트용 상/하한가 상태 관리 (Pair<상한가, 하한가>)
    private val _currentStockLimits = MutableStateFlow<Pair<Long, Long>?>(null)
    val currentStockLimits: StateFlow<Pair<Long, Long>?> = _currentStockLimits.asStateFlow()

    // 💡 바텀 시트 오픈 시 호출하여 상/하한가 세팅
    fun fetchStockLimitsForModify(stockCode: String) {
        val currentUid = auth.currentUser?.uid ?: return
        val account = _selectedAccount.value
        val token = tokenManager.getAccessToken(currentUid, account) ?: return

        viewModelScope.launch {
            try {
                val request = com.example.dk250403.network.T1102Request(
                    com.example.dk250403.network.T1102InBlock(shcode = stockCode, exchgubun = "U")
                )
                val response = com.example.dk250403.network.RetrofitClient.lsApi.getStockCurrentPrice(
                    token = "Bearer $token",
                    request = request
                )

                if (response.isSuccessful) {
                    response.body()?.t1102OutBlock?.let { outBlock ->
                        // 🚨 주의: T1102OutBlock 모델에 uplmtprice(상한가), dnlmtprice(하한가) 필드가 있어야 합니다.
                        val upper = outBlock.uplmtprice ?: 0L
                        val lower = outBlock.dnlmtprice ?: 0L
                        _currentStockLimits.value = Pair(upper, lower)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // 💡 바텀 시트를 닫을 때 상태 초기화
    fun clearStockLimits() {
        _currentStockLimits.value = null
    }

    sealed class UiState {
        object Idle : UiState()
        object Loading : UiState()
        data class BalanceLoaded(val balance: BalanceResponse) : UiState()
        data class Error(val message: String) : UiState()
    }
}
