package com.example.dk250403.network

import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import okhttp3.*
import org.json.JSONObject

object LsWebSocketManager {
    private const val WS_URL = "wss://openapi.ls-sec.co.kr:9443/websocket"
    private var webSocket: WebSocket? = null

    private var isReady = false
    private val client = OkHttpClient.Builder().build()

    private val _realtimeDataFlow = MutableSharedFlow<JSONObject>(extraBufferCapacity = 100)
    val realtimeDataFlow: SharedFlow<JSONObject> = _realtimeDataFlow.asSharedFlow()

    private val activeSubscriptions = mutableSetOf<String>()
    private var currentToken: String = ""

    fun connect(token: String) {
        Log.d("LsWebSocket", "👉 웹소켓 connect() 함수 진입! 토큰 유무: ${token.isNotEmpty()}")
        if (webSocket != null) return
        currentToken = token

        val request = Request.Builder().url(WS_URL).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d("LsWebSocket", "✅ 웹소켓 연결 성공")
                isReady = true

                activeSubscriptions.forEach { key ->
                    val parts = key.split("|")
                    if (parts.size == 2) {
                        // 💡 재연결 시에도 구독(3)으로 전송
                        sendSubscription(parts[0], parts[1], "3")
                    }
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d("LsWebSocket", "📩 서버 응답: $text")
                try {
                    val json = JSONObject(text)
                    _realtimeDataFlow.tryEmit(json)
                } catch (e: Exception) {
                    Log.e("LsWebSocket", "메시지 파싱 오류")
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("LsWebSocket", "❌ 연결 실패 또는 끊김: ${t.localizedMessage}")
                this@LsWebSocketManager.webSocket = null
                isReady = false
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d("LsWebSocket", "정상 종료")
                this@LsWebSocketManager.webSocket = null
                isReady = false
            }
        })
    }

    fun disconnect() {
        webSocket?.close(1000, "User disconnected")
        webSocket = null
        isReady = false
        activeSubscriptions.clear()
    }

    fun subscribe(trCd: String, trKey: String) {
        val key = "${trCd}|${trKey}"
        if (!activeSubscriptions.contains(key)) {
            activeSubscriptions.add(key)
            sendSubscription(trCd, trKey, "3") // 💡 실시간 시세 등록(구독) Type: 3
        }
    }

    fun unsubscribe(trCd: String, trKey: String) {
        val key = "${trCd}|${trKey}"
        if (activeSubscriptions.contains(key)) {
            activeSubscriptions.remove(key)
            sendSubscription(trCd, trKey, "4") // 💡 실시간 시세 해지 Type: 4
        }
    }

    private fun sendSubscription(trCd: String, trKey: String, trType: String) {
        if (webSocket == null || !isReady || currentToken.isEmpty()) return

        val header = JSONObject().apply {
            put("token", currentToken)
            put("tr_type", trType)
        }
        val body = JSONObject().apply {
            put("tr_cd", trCd)
            put("tr_key", trKey)
        }
        val requestJson = JSONObject().apply {
            put("header", header)
            put("body", body)
        }

        Log.d("LsWebSocket", "🚀 구독 요청 전송: $trCd / $trKey / Type: $trType")
        webSocket?.send(requestJson.toString())
    }
}