package com.example.dk250403.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.Header
import retrofit2.http.POST

interface LsApiService {
    // 1. 접근 토큰(Access Token) 발급
    @FormUrlEncoded
    @POST("oauth2/token")
    suspend fun getAccessToken(
        @Field("grant_type") grantType: String = "client_credentials",
        @Field("appkey") appKey: String,
        @Field("appsecretkey") appSecretKey: String,
        @Field("scope") scope: String = "oob"
    ): Response<TokenResponse>

    // 2. 국내주식 잔고 조회 (예: t0424)
    @POST("stock/accno") // 실제 LS증권의 잔고조회 엔드포인트 URL로 변경해야 합니다.
    suspend fun getAccountBalance(
        @Header("authorization") token: String,
        @Header("tr_cd") trCd: String = "t0424",
        @Header("tr_cont") trCont: String = "N",
        @Header("mac_address") macAddress: String = "",
        @Body request: BalanceRequest
    ): Response<BalanceResponse>
}