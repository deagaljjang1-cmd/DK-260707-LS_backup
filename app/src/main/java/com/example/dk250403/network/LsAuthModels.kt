package com.example.dk250403.network



import com.google.gson.annotations.SerializedName



// 토큰 발급 요청 모델

data class TokenRequest(

    @SerializedName("grant_type") val grantType: String = "client_credentials",

    @SerializedName("appkey") val appKey: String,

    @SerializedName("appsecretkey") val appSecretKey: String,

    @SerializedName("scope") val scope: String = "oob"

)



// 토큰 발급 응답 모델

data class TokenResponse(

    @SerializedName("access_token") val accessToken: String,

    @SerializedName("expires_in") val expiresIn: Int,

    @SerializedName("token_type") val tokenType: String

)



// 잔고 조회 요청 모델 (InBlock)

data class BalanceRequest(

    @SerializedName("t0424InBlock") val t0424InBlock: T0424InBlock

)



data class T0424InBlock(

    @SerializedName("accno") val accno: String, // 계좌번호

    @SerializedName("passwd") val passwd: String = "", // 비밀번호 (API 설정에 따라 생략 가능)

    @SerializedName("prcgb") val prcgb: String = "1", // 단가구분

    @SerializedName("chegb") val chegb: String = "2", // 체결구분

    @SerializedName("dangb") val dangb: String = "0", // 단일가구분

    @SerializedName("charge") val charge: String = "0", // 제비용구분

    @SerializedName("cts_expcode") val ctsExpcode: String = "" // 연속조회용

)



// 잔고 조회 응답 모델 (OutBlock)

data class BalanceResponse(

    @SerializedName("rsp_cd") val rspCd: String,

    @SerializedName("rsp_msg") val rspMsg: String,

    @SerializedName("t0424OutBlock") val summary: BalanceSummary?,

    @SerializedName("t0424OutBlock1") val holdings: List<StockHolding>?

)



data class BalanceSummary(

    @SerializedName("sunamt") val totalEvaluationAmount: Long, // 추정순자산

    @SerializedName("dtsunik") val totalProfitAndLoss: Long    // 실현손익

)



data class StockHolding(

    @SerializedName("hname") val itemName: String,             // 종목명

    @SerializedName("expcode") val itemCode: String,           // 종목코드

    @SerializedName("janqty") val quantity: Long,              // 잔고수량

    @SerializedName("pamt") val averagePrice: Long,            // 평균단가

    @SerializedName("price") val currentPrice: Long,           // 현재가

    @SerializedName("sunikrt") val returnRate: Double,          // 수익률
    @SerializedName("drate") val todayChangeRate: Double = 0.0,
    // 💡 추가: 실시간으로 계산할 전일 종가(기준가)
    val yesterdayPrice: Long = 0L

)


// ==========================================
// [t1102] 주식 현재가 단건 시세 조회 모델
// ==========================================

data class T1102Request(
    val t1102InBlock: T1102InBlock
)

data class T1102InBlock(
    val shcode: String,
    val exchgubun: String = "U" // 기본값 'U' (통합) 세팅
)

data class T1102Response(
    val rsp_cd: String?,
    val rsp_msg: String?,
    val t1102OutBlock: T1102OutBlock?
)

data class T1102OutBlock(
    val price: Long,         // 현재가 (종가)
    val diff: Double,        // 등락률
    val recprice: Long,      // 전일종가(기준가)
    val uplmtprice: Long,    // 상한가
    val dnlmtprice: Long     // 하한가
)