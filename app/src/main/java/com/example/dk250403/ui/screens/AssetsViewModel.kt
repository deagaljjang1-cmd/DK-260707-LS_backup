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
    private val tokenManager = TokenManager(application)
    private val auth = FirebaseAuth.getInstance()
    private val currentUid = auth.currentUser?.uid ?: ""

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _registeredAccounts = MutableStateFlow<List<String>>(emptyList())
    val registeredAccounts: StateFlow<List<String>> = _registeredAccounts.asStateFlow()

    private val _selectedAccount = MutableStateFlow<String>("")
    val selectedAccount: StateFlow<String> = _selectedAccount.asStateFlow()

    private var fetchJob: Job? = null
    private var lastRequestTime = 0L

    fun initAssets() {
        if (currentUid.isEmpty()) {
            _uiState.value = UiState.Error("로그인 정보가 없습니다.")
            return
        }

        val accounts = tokenManager.getAccountNumbers(currentUid)
        _registeredAccounts.value = accounts

        if (accounts.isEmpty()) {
            _uiState.value = UiState.Error("등록된 계좌가 없습니다. 계정 설정에서 앱키와 계좌번호를 먼저 등록해주세요.")
            return
        }

        val userAppKey = tokenManager.getAppKey(currentUid)
        val userAppSecret = tokenManager.getSecretKey(currentUid)
        if (userAppKey.isNullOrEmpty() || userAppSecret.isNullOrEmpty()) {
            _uiState.value = UiState.Error("앱키와 시크릿키가 설정되지 않았습니다. 계정 설정에서 입력해 주세요.")
            return
        }

        val existingToken = tokenManager.getAccessToken(currentUid)
        if (!existingToken.isNullOrEmpty()) {
            requestBalanceForAccount(accounts.first())
        } else {
            issueAccessTokenAndFetch(userAppKey, userAppSecret, accounts.first())
        }
    }

    private fun issueAccessTokenAndFetch(appKey: String, appSecret: String, targetAccount: String) {
        fetchJob?.cancel()
        fetchJob = viewModelScope.launch {
            _uiState.value = UiState.Loading
            _selectedAccount.value = targetAccount
            try {
                val response = RetrofitClient.lsApi.getAccessToken(appKey = appKey, appSecretKey = appSecret)
                if (response.isSuccessful && response.body() != null) {
                    val token = response.body()!!.accessToken
                    tokenManager.saveAccessToken(currentUid, token)
                    fetchBalance(targetAccount, token)
                } else {
                    _uiState.value = UiState.Error("토큰 발급 실패: ${response.code()}")
                }
            } catch (e: Exception) {
                _uiState.value = UiState.Error("네트워크 오류: ${e.localizedMessage}")
            }
        }
    }

    fun requestBalanceForAccount(accountNumber: String) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastRequestTime < 1000) {
            _uiState.value = UiState.Error("조회 요청이 너무 빠릅니다.\n잠시 후 다시 눌러주세요.")
            return
        }
        lastRequestTime = currentTime

        _selectedAccount.value = accountNumber

        // 💡 핵심 방어 로직: 숫자로만 이루어져 있고, 정확히 10자리 또는 11자리인 경우만 통과
        // "555040835"(9자리) 처럼 불완전한 번호는 서버에 가지도 못하고 즉시 에러 처리됨
        if (!accountNumber.all { it.isDigit() } || accountNumber.length !in 10..11) {
            _uiState.value = UiState.Error("조회할 수 없는 계좌입니다.\n계좌번호를 다시 확인해 주세요.")
            return
        }

        val token = tokenManager.getAccessToken(currentUid)
        if (!token.isNullOrEmpty()) {
            fetchBalance(accountNumber, token)
        } else {
            _uiState.value = UiState.Error("토큰이 만료되었거나 없습니다. 다시 시도해주세요.")
        }
    }

    private fun fetchBalance(accountNumber: String, token: String) {
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
                    } catch (e: Exception) {
                    }
                    _uiState.value = UiState.Error("잔고 조회 실패:\n$parsedMsg")
                }
            } catch (e: Exception) {
                _uiState.value = UiState.Error("통신 오류: ${e.localizedMessage}")
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