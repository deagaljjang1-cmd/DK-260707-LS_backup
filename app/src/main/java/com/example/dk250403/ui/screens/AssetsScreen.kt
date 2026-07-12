package com.example.dk250403.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dk250403.*
import com.example.dk250403.network.StockHolding
import java.text.DecimalFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssetsScreen(
    modifier: Modifier = Modifier,
    viewModel: AssetsViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val currentSortType by viewModel.sortType.collectAsState()

    val numberFormat = remember { DecimalFormat("#,###") }
    val stockColorUp = remember { Color(0xFFFF3737) }
    val stockColorDown = remember { Color(0xFF37C4FF) }

    // 💡 바텀 시트 및 다이얼로그 상태 관리 변수 추가 // 💡 기존 selectedStockForOrder를 삭제하고 코드를 담는 변수로 교체 (박제 방지)
    var selectedStockCodeForOrder by remember { mutableStateOf<String?>(null) }
    var showConfirmDialog by remember { mutableStateOf(false) }

    // 💡 다이얼로그로 넘길 주문 임시 데이터
    var pendingTradeType by remember { mutableStateOf(TradeType.BUY) }
    var pendingOrderType by remember { mutableStateOf(OrderType.LIMIT) }
    var pendingPrice by remember { mutableStateOf(0L) }
    var pendingQuantity by remember { mutableStateOf(0L) }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().background(ColorBg).padding(16.dp)) {
            when (uiState) {
                is AssetsViewModel.UiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = ColorStatus)
                            Spacer(Modifier.height(16.dp))
                            Text("증권사 데이터를 불러오는 중...", color = ColorTextSecondary, fontSize = 14.sp)
                        }
                    }
                }
                is AssetsViewModel.UiState.Error -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.ErrorOutline, contentDescription = "에러 아이콘", modifier = Modifier.size(48.dp), tint = ColorUp)
                            Spacer(Modifier.height(16.dp))
                            Text(
                                text = (uiState as AssetsViewModel.UiState.Error).message,
                                color = ColorUp, fontSize = 15.sp, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                is AssetsViewModel.UiState.BalanceLoaded -> {
                    val data = (uiState as AssetsViewModel.UiState.BalanceLoaded).balance

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = ColorSurfaceVariant),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("추정 순자산", color = ColorTextSecondary, fontSize = 13.sp)
                            Text(
                                "${numberFormat.format(data.summary?.totalEvaluationAmount ?: 0)} 원",
                                color = ColorTextPrimary, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold
                            )
                            Spacer(Modifier.height(8.dp))

                            val totalPnl = data.summary?.totalProfitAndLoss ?: 0L
                            val pnlColor = if (totalPnl > 0) stockColorUp else if (totalPnl < 0) stockColorDown else ColorTextPrimary
                            val pnlPrefix = if (totalPnl > 0) "+" else ""
                            Text("실현 손익: $pnlPrefix${numberFormat.format(totalPnl)} 원", color = pnlColor, fontSize = 14.sp)
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("보유 종목", color = ColorTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(
                                selected = currentSortType == SortType.NAME_ASC || currentSortType == SortType.NAME_DESC,
                                onClick = {
                                    if (currentSortType == SortType.NAME_ASC) viewModel.setSortType(SortType.NAME_DESC)
                                    else viewModel.setSortType(SortType.NAME_ASC)
                                },
                                label = { Text(if (currentSortType == SortType.NAME_DESC) "종목명↓" else "종목명↑", fontSize = 12.sp) }
                            )
                            FilterChip(
                                selected = currentSortType == SortType.RETURN_DESC || currentSortType == SortType.RETURN_ASC,
                                onClick = {
                                    if (currentSortType == SortType.RETURN_DESC) viewModel.setSortType(SortType.RETURN_ASC)
                                    else viewModel.setSortType(SortType.RETURN_DESC)
                                },
                                label = { Text(if (currentSortType == SortType.RETURN_ASC) "수익률↑" else "수익률↓", fontSize = 12.sp) }
                            )
                            FilterChip(
                                selected = currentSortType == SortType.PROFIT_DESC || currentSortType == SortType.PROFIT_ASC,
                                onClick = {
                                    if (currentSortType == SortType.PROFIT_DESC) viewModel.setSortType(SortType.PROFIT_ASC)
                                    else viewModel.setSortType(SortType.PROFIT_DESC)
                                },
                                label = { Text(if (currentSortType == SortType.PROFIT_ASC) "수익금↑" else "수익금↓", fontSize = 12.sp) }
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        val holdingList = data.holdings ?: emptyList()
                        if (holdingList.isEmpty()) {
                            item {
                                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                    Text("보유 중인 주식이 없습니다.", color = ColorTextSecondary, fontSize = 14.sp)
                                }
                            }
                        } else {
                            items(holdingList) { stock ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(ColorSurface, RoundedCornerShape(8.dp))
                                        // 💡 객체 대신 종목 코드(String)만 저장
                                        .clickable { selectedStockCodeForOrder = stock.itemCode }
                                        .padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(stock.itemName, color = ColorTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                        Spacer(Modifier.height(4.dp))
                                        Text("${stock.quantity}주 | 평단가 ${numberFormat.format(stock.averagePrice)}", color = ColorTextSecondary, fontSize = 12.sp)
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        val isProfit = stock.returnRate > 0
                                        val isLoss = stock.returnRate < 0
                                        val rateColor = if (isProfit) stockColorUp else if (isLoss) stockColorDown else ColorTextPrimary
                                        val signPrefix = if (isProfit) "+" else ""

                                        val profitAmount = (stock.currentPrice - stock.averagePrice) * stock.quantity
                                        val formattedRate = String.format("%.2f", stock.returnRate)

                                        Text("${numberFormat.format(stock.currentPrice)} 원", color = ColorTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = "$signPrefix$formattedRate% ($signPrefix${numberFormat.format(profitAmount)}원)",
                                            color = rateColor,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                else -> {}
            }
        }

        // 💡 부모 화면 상단에 시간 Flow를 관찰하는 변수 추가 (상태바 시계에도 동일하게 적용)
        val currentTime by viewModel.realTimeClock.collectAsState()

        val latestStock = if (uiState is AssetsViewModel.UiState.BalanceLoaded) {
            (uiState as AssetsViewModel.UiState.BalanceLoaded).balance.holdings?.find { it.itemCode == selectedStockCodeForOrder }
        } else null

        latestStock?.let { stock ->
            val cashBalance = if (uiState is AssetsViewModel.UiState.BalanceLoaded) {
                val balance = (uiState as AssetsViewModel.UiState.BalanceLoaded).balance
                val totalEvaluationAmount = balance.summary?.totalEvaluationAmount ?: 0L
                val totalHoldingsValue = balance.holdings?.sumOf { it.currentPrice * it.quantity } ?: 0L
                totalEvaluationAmount - totalHoldingsValue
            } else 0L

            OrderBottomSheet(
                stockName = stock.itemName,
                stockCode = stock.itemCode,
                currentPrice = stock.currentPrice,
                todayChangeRate = stock.todayChangeRate,
                yesterdayPrice = stock.yesterdayPrice,
                availableCash = cashBalance,
                holdingQuantity = stock.quantity,
                onDismiss = { selectedStockCodeForOrder = null },
                onOrderSubmit = { tradeType, orderType, price, quantity, market ->
                    pendingTradeType = tradeType
                    pendingOrderType = orderType
                    pendingPrice = price
                    pendingQuantity = quantity
                    // pendingMarket = market // 추후 사용
                    showConfirmDialog = true
                },
                onFetchInitialPrice = { viewModel.fetchInitialPrice(it) },
                realTimeMillis = currentTime // 💡 실시간 타이머 변수 주입!
            )
        }

        // 💡 확인 팝업에서도 selectedStockCodeForOrder를 null 처리하도록 onConfirm 부분 수정 필요
        // 💡 최종 주문 확인 팝업 영역 (latestStock 참조로 교체)
        if (showConfirmDialog && latestStock != null) {
            OrderConfirmDialog(
                stockName = latestStock.itemName,
                tradeType = pendingTradeType,
                orderType = pendingOrderType,
                price = pendingPrice,
                quantity = pendingQuantity,
                onDismiss = { showConfirmDialog = false },
                onConfirm = {
                    showConfirmDialog = false
                    selectedStockCodeForOrder = null // 💡 코드를 null로 비워주어 바텀 시트를 닫음

                    // TODO: LS증권 API로 주문 전송 로직 (다음 단계)
                    println("🚀 주문 전송 완료: ${pendingTradeType} / 단가: $pendingPrice / 수량: $pendingQuantity")
                }
            )
        }    }
}