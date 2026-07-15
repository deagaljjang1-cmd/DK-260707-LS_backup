package com.example.dk250403.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dk250403.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TradingBottomSheet(
    stockName: String,
    stockCode: String,
    currentPrice: Long,
    onDismissRequest: () -> Unit,
    onOrderSubmit: (isBuy: Boolean, orderType: String, price: Long, quantity: Int) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // 주문 설정 상태
    var isMarketPrice by remember { mutableStateOf(false) } // true: 시장가, false: 지정가
    var orderPrice by remember { mutableStateOf(currentPrice) }
    var orderQuantity by remember { mutableStateOf(1) }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = ColorBg,
        dragHandle = { BottomSheetDefaults.DragHandle(color = ColorTextSecondary) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .padding(bottom = 32.dp)
        ) {
            // 1. 상단 종목 정보
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text(text = stockName, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = ColorTextPrimary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = stockCode, fontSize = 14.sp, color = ColorTextSecondary)
                }
                Text(text = "현재가: ${String.format("%,d", currentPrice)}원", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = ColorTextPrimary)
            }

            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider(color = ColorSurfaceVariant)
            Spacer(modifier = Modifier.height(24.dp))

            // 2. 주문 설정 (단가 및 수량)
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("주문 구분", fontSize = 14.sp, color = ColorTextSecondary, modifier = Modifier.weight(1f))
                Row(modifier = Modifier.weight(2f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !isMarketPrice,
                        onClick = { isMarketPrice = false },
                        label = { Text("지정가") },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = ColorSurfaceVariant, selectedLabelColor = ColorTextPrimary, containerColor = ColorSurface, labelColor = ColorTextSecondary),
                        border = null
                    )
                    FilterChip(
                        selected = isMarketPrice,
                        onClick = { isMarketPrice = true },
                        label = { Text("시장가") },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = ColorSurfaceVariant, selectedLabelColor = ColorTextPrimary, containerColor = ColorSurface, labelColor = ColorTextSecondary),
                        border = null
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("주문 단가", fontSize = 14.sp, color = ColorTextSecondary, modifier = Modifier.weight(1f))
                Row(modifier = Modifier.weight(2f), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = { if (orderPrice > 0) orderPrice -= 100 }, // 추후 호가단위 계산 로직 적용
                        enabled = !isMarketPrice,
                        modifier = Modifier.size(40.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) { Text("-", fontSize = 18.sp) }

                    Text(
                        text = if (isMarketPrice) "시장가 적용" else "${String.format("%,d", orderPrice)}원",
                        fontSize = 16.sp, fontWeight = FontWeight.Bold, color = if (isMarketPrice) ColorTextSecondary else ColorTextPrimary,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                    )

                    OutlinedButton(
                        onClick = { orderPrice += 100 }, // 추후 호가단위 계산 로직 적용
                        enabled = !isMarketPrice,
                        modifier = Modifier.size(40.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) { Text("+", fontSize = 18.sp) }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("주문 수량", fontSize = 14.sp, color = ColorTextSecondary, modifier = Modifier.weight(1f))
                Row(modifier = Modifier.weight(2f), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = { if (orderQuantity > 1) orderQuantity -= 1 },
                        modifier = Modifier.size(40.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) { Text("-", fontSize = 18.sp) }

                    Text(
                        text = "${orderQuantity}주",
                        fontSize = 16.sp, fontWeight = FontWeight.Bold, color = ColorTextPrimary,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                    )

                    OutlinedButton(
                        onClick = { orderQuantity += 1 },
                        modifier = Modifier.size(40.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) { Text("+", fontSize = 18.sp) }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // 3. 매수 / 매도 버튼
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { onOrderSubmit(false, if (isMarketPrice) "시장가" else "지정가", orderPrice, orderQuantity) },
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ColorDown)
                ) { Text("매도 (Sell)", fontSize = 16.sp, fontWeight = FontWeight.Bold) }

                Button(
                    onClick = { onOrderSubmit(true, if (isMarketPrice) "시장가" else "지정가", orderPrice, orderQuantity) },
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ColorUp)
                ) { Text("매수 (Buy)", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
            }
        }
    }
}