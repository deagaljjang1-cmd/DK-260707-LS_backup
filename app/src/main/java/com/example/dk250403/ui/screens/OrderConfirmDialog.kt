package com.example.dk250403.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.dk250403.ColorBg
import com.example.dk250403.ColorSurface
import com.example.dk250403.ColorTextPrimary
import com.example.dk250403.ColorTextSecondary
import java.text.DecimalFormat
import java.util.Calendar

@Composable
fun OrderConfirmDialog(
    stockName: String,
    tradeType: TradeType,
    orderType: OrderType,
    price: Long,
    quantity: Long,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val numberFormat = remember { DecimalFormat("#,###") }

    // 💡 1. 팝업이 열릴 때의 시간을 계산하여 거래소 판별
    val currentTimeHHmm = remember {
        val calendar = Calendar.getInstance()
        calendar.get(Calendar.HOUR_OF_DAY) * 100 + calendar.get(Calendar.MINUTE)
    }
    val targetExchange = if ((currentTimeHHmm in 800..849) || (currentTimeHHmm in 1540..1999)) {
        "NXT"
    } else {
        "KRX"
    }

    val isBuy = tradeType == TradeType.BUY
    val tradeTypeText = if (isBuy) "매수" else "매도"
    val themeColor = if (isBuy) Color(0xFFFF3737) else Color(0xFF37C4FF)

    val isMarket = orderType == OrderType.MARKET
    val orderTypeText = if (isMarket) "시장가" else "지정가"

    // 총 금액 계산
    val totalAmount = price * quantity

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = ColorSurface)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 1. 팝업 타이틀
                Text(
                    text = "주문 내역 확인",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = ColorTextPrimary
                )

                Spacer(modifier = Modifier.height(24.dp))

                // 2. 종목명 및 매매 구분
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "종목명", color = ColorTextSecondary, fontSize = 14.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = stockName, color = ColorTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(6.dp))
                        // 매수/매도 뱃지
                        Surface(
                            color = themeColor.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = tradeTypeText,
                                color = themeColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 💡 [추가된 부분] 주문 거래소 표시 영역
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = "거래소", color = ColorTextSecondary, fontSize = 14.sp)
                    Text(
                        text = targetExchange,
                        color = ColorTextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 3. 구분 (시장가/지정가)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = "주문 구분", color = ColorTextSecondary, fontSize = 14.sp)
                    Text(text = orderTypeText, color = ColorTextPrimary, fontSize = 15.sp)
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 4. 주문 단가
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = "주문 단가", color = ColorTextSecondary, fontSize = 14.sp)
                    Text(
                        text = if (isMarket) "시장가 적용" else "${numberFormat.format(price)} 원",
                        color = ColorTextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 5. 주문 수량
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = "주문 수량", color = ColorTextSecondary, fontSize = 14.sp)
                    Text(text = "${numberFormat.format(quantity)} 주", color = ColorTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = ColorBg)
                Spacer(modifier = Modifier.height(16.dp))

                // 6. 총 주문 (예상) 금액
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isMarket) "총 예상 금액" else "총 주문 금액",
                        color = ColorTextSecondary,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "${numberFormat.format(totalAmount)} 원",
                        color = themeColor,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                // 7. 취소 및 실행 버튼
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).height(50.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ColorTextSecondary)
                    ) {
                        Text("취소", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f).height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = themeColor)
                    ) {
                        Text("주문 전송", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
}