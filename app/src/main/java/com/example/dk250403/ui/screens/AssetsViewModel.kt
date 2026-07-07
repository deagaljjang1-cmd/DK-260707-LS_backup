package com.example.dk250403.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.dk250403.network.BalanceRequest
import com.example.dk250403.network.BalanceResponse
import com.example.dk250403.network.RetrofitClient
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

    // 💡 (수정) 뷰모델 생성 시점에 UID를 박제(캐싱)하던 로직 삭제

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _registeredAccounts = MutableStateFlow<List<String>>(emptyList())
    val registeredAccounts: StateFlow<List<String>> = _registeredAccounts.asStateFlow()

    private val _selectedAccount = MutableStateFlow<String>("")
    val selectedAccount: StateFlow<String> = _selectedAccount.asStateFlow()

    private var fetchJob: Job? = null
    private var lastRequestTime = 0L

    // 💡 화면 잔상을 비워주는 초기화 함수
    fun clearData() {
        _uiState.value = UiState.Idle
        _registeredAccounts.value = emptyList()
        _selectedAccount.value = ""
        fetchJob?.cancel()
    }

    fun initAssets() {
        // 💡 동적으로 현재 로그인된 사용자의 UID 호출
        val currentUid = auth.currentUser?.uid ?: ""

        if (currentUid.isEmpty()) {
            _uiState.value = UiState.Error("로그인 정보가 없습니다.")
            return
        }

        // 이전 계정의 잔상을 완전히 비운 뒤 새로 데이터를 구성
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

    sealed class UiState {
        object Idle : UiState()
        object Loading : UiState()
        data class BalanceLoaded(val balance: BalanceResponse) : UiState()
        data class Error(val message: String) : UiState()
    }
}