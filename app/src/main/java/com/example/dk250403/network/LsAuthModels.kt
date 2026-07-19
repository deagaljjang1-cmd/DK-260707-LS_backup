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


// ==========================================
// [CSPAT00601] 현물주문 API 모델
// ==========================================

data class OrderRequest(
    @SerializedName("CSPAT00601InBlock1") val inBlock: CSPAT00601InBlock1
)

data class CSPAT00601InBlock1(
    @SerializedName("IsuNo") val isuNo: String,             // 종목번호 (일반 주식은 종목코드 6자리, 모의투자는 'A' + 6자리 등 환경에 맞게 세팅 필요)
    @SerializedName("OrdQty") val ordQty: Long,             // 주문수량
    @SerializedName("OrdPrc") val ordPrc: Long,             // 주문가 (시장가의 경우 0 또는 API 가이드에 따른 값)
    @SerializedName("BnsTpCode") val bnsTpCode: String,     // 매매구분 (1:매도, 2:매수)
    @SerializedName("OrdprcPtnCode") val ordprcPtnCode: String, // 호가유형코드 (00:지정가, 03:시장가 등)
    @SerializedName("MgntrnCode") val mgntrnCode: String = "000", // 신용거래코드 (000: 보통)
    @SerializedName("LoanDt") val loanDt: String = "",      // 대출일 (신용 아닐경우 공백)
    @SerializedName("OrdCndiTpCode") val ordCndiTpCode: String = "0", // 주문조건구분 (0:없음)
    @SerializedName("MbrNo") val mbrNo: String = "KRX"      // 회원사번호
)

data class OrderResponse(
    val rsp_cd: String?,
    val rsp_msg: String?,
    @SerializedName("CSPAT00601OutBlock1") val outBlock1: CSPAT00601OutBlock1?,
    @SerializedName("CSPAT00601OutBlock2") val outBlock2: CSPAT00601OutBlock2?
)

// 응답 InBlock 메아리 (필요한 것만 매핑)
data class CSPAT00601OutBlock1(
    @SerializedName("OrdSeqNo") val ordSeqNo: Long
)

// 응답 실제 결과 (주문번호 등)
data class CSPAT00601OutBlock2(
    @SerializedName("OrdNo") val ordNo: Long,               // 발번된 주문번호
    @SerializedName("OrdTime") val ordTime: String,         // 주문시각
    @SerializedName("OrdAmt") val ordAmt: Long,             // 주문금액
    @SerializedName("IsuNm") val isuNm: String              // 종목명
)

// ==========================================
// [t0425] 현물 체결/미체결 조회 API 모델
// ==========================================

data class T0425Request(
    @SerializedName("t0425InBlock") val t0425InBlock: T0425InBlock
)

data class T0425InBlock(
    @SerializedName("expcode") val expcode: String = "",
    @SerializedName("chegb") val chegb: String = "2",   // 2: 미체결 고정
    @SerializedName("medosu") val medosu: String = "0", // 0: 전체 (매수/매도 모두)
    @SerializedName("sortgb") val sortgb: String = "1", // 1: 역순 (최신순)
    @SerializedName("cts_ordno") val ctsOrdno: String = ""
)

data class T0425Response(
    val rsp_cd: String?,
    val rsp_msg: String?,
    @SerializedName("t0425OutBlock") val outBlock: T0425OutBlock?,
    @SerializedName("t0425OutBlock1") val unexecutedList: List<T0425OutBlock1>?
)

data class T0425OutBlock(
    @SerializedName("tordrem") val totalUnexecutedQty: Long
)

data class T0425OutBlock1(
    @SerializedName("ordno") val ordno: Long,             // 주문번호
    @SerializedName("expcode") val expcode: String,       // 종목번호
    @SerializedName("exchname") val exchname: String,     // 거래소명
    @SerializedName("medosu") val medosu: String,         // 매매구분 (매도/매수)
    @SerializedName("qty") val qty: Long,                 // 주문수량
    @SerializedName("price") val price: Long,             // 주문가격
    @SerializedName("price1") val price1: Long,           // 현재가
    @SerializedName("cheqty") val cheqty: Long,           // 체결수량
    @SerializedName("ordrem") val ordrem: Long,           // 미체결잔량
    @SerializedName("ordgb") val ordgb: String,           // 주문유형
    @SerializedName("hogagb") val hogagb: String,         // 호가유형
    @SerializedName("ordtime") val ordtime: String,       // 주문시간
    @SerializedName("ordermtd") val ordermtd: String      // 주문매체
)