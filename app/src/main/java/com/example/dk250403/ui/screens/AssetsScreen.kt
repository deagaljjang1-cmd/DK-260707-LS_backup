package com.example.dk250403.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun AssetsScreen(
    modifier: Modifier = Modifier,
    viewModel: AssetsViewModel
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    val uiState by viewModel.uiState.collectAsState()
    val currentSortType by viewModel.sortType.collectAsState()
    val selectedAccount by viewModel.selectedAccount.collectAsState()

    val numberFormat = remember { DecimalFormat("#,###") }
    val stockColorUp = remember { Color(0xFFFF3737) }
    val stockColorDown = remember { Color(0xFF37C4FF) }

    // 💡 바텀 시트(주문) 호출을 위한 상태
    var selectedStockCodeForOrder by remember { mutableStateOf<String?>(null) }
    var showConfirmDialog by remember { mutableStateOf(false) }

    // 💡 아코디언 UI(펼치기/접기) 상태 관리 변수
    var expandedSummary by remember { mutableStateOf(false) }
    var expandedStockCode by remember { mutableStateOf<String?>(null) }

    // 💡 주문 정보 저장
    var pendingTradeType by remember { mutableStateOf(TradeType.BUY) }
    var pendingOrderType by remember { mutableStateOf(OrderType.LIMIT) }
    var pendingPrice by remember { mutableStateOf(0L) }
    var pendingQuantity by remember { mutableStateOf(0L) }

    // 💡 당겨서 새로고침 상태
    val isLoading = uiState is AssetsViewModel.UiState.Loading
    val pullRefreshState = rememberPullRefreshState(
        refreshing = isLoading,
        onRefresh = {
            if (selectedAccount.isNotEmpty()) {
                viewModel.requestBalanceForAccount(selectedAccount) // 전체 잔고 및 토큰 갱신
            }
        }
    )

    Box(modifier = modifier.fillMaxSize().pullRefresh(pullRefreshState)) {
        // 전체 화면이 스크롤되도록 단일 LazyColumn 사용
        LazyColumn(
            modifier = Modifier.fillMaxSize().background(ColorBg).padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp), // ✅ top과 bottom을 명시적으로 분리
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            // 1. 추정 순자산 카드 및 에러 메시지
            item {
                when (uiState) {
                    is AssetsViewModel.UiState.Error -> {
                        Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.ErrorOutline, contentDescription = "에러", modifier = Modifier.size(48.dp), tint = ColorUp)
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
                        val holdings = data.holdings ?: emptyList()

                        // 💡 임시 계산 로직 (API 연동 전 UI 렌더링용)
                        val totalEval = data.summary?.totalEvaluationAmount ?: 0L
                        val totalRealized = data.summary?.totalProfitAndLoss ?: 0L
                        val totalPurchaseAmt = holdings.sumOf { it.averagePrice * it.quantity }
                        val totalCurrentAmt = holdings.sumOf { it.currentPrice * it.quantity }
                        val totalUnrealizedPnl = totalCurrentAmt - totalPurchaseAmt
                        val totalReturnRate = if (totalPurchaseAmt > 0) (totalUnrealizedPnl.toDouble() / totalPurchaseAmt.toDouble()) * 100 else 0.0
                        val d2Deposit = totalEval - totalCurrentAmt // 임시 예수금 계산

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = ColorSurfaceVariant),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("추정 순자산", color = ColorTextSecondary, fontSize = 13.sp)
                                Text(
                                    "${numberFormat.format(totalEval)} 원",
                                    color = ColorTextPrimary, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold
                                )
                                Spacer(Modifier.height(12.dp))

                                // 실현 손익 라인
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        // 폰트 컬러 화이트 유지
                                        Text("실현 손익: ", color = ColorTextPrimary, fontSize = 14.sp)

                                        val pnlColor = if (totalRealized > 0) stockColorUp else if (totalRealized < 0) stockColorDown else ColorTextPrimary
                                        val pnlPrefix = if (totalRealized > 0) "+" else ""
                                        // 금액 부분에만 색상 적용
                                        Text("$pnlPrefix${numberFormat.format(totalRealized)} 원", color = pnlColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    }

                                    OutlinedButton(
                                        onClick = { /* 당일실현손익 조회 연동 */ },
                                        modifier = Modifier.height(28.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ColorTextSecondary)
                                    ) {
                                        Text("당일실현손익 조회 〉", fontSize = 11.sp)
                                    }
                                }

                                // 하단 화살표 버튼
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { expandedSummary = !expandedSummary }
                                        .padding(top = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (expandedSummary) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                        contentDescription = "상세보기",
                                        tint = ColorTextSecondary
                                    )
                                }

                                // 상세 정보 아코디언 영역
                                AnimatedVisibility(
                                    visible = expandedSummary,
                                    enter = expandVertically(animationSpec = tween(300)),
                                    exit = shrinkVertically(animationSpec = tween(300))
                                ) {
                                    Column(modifier = Modifier.padding(top = 16.dp)) {
                                        HorizontalDivider(color = ColorSurface)
                                        Spacer(Modifier.height(16.dp))

                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("총 수익률", color = ColorTextSecondary, fontSize = 13.sp)
                                            val rateColor = if (totalReturnRate > 0) stockColorUp else if (totalReturnRate < 0) stockColorDown else ColorTextPrimary
                                            val ratePrefix = if (totalReturnRate > 0) "+" else ""
                                            Text("$ratePrefix${String.format("%.2f", totalReturnRate)}%", color = rateColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        }
                                        Spacer(Modifier.height(8.dp))
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("잔고평가손익", color = ColorTextSecondary, fontSize = 13.sp)
                                            val unColor = if (totalUnrealizedPnl > 0) stockColorUp else if (totalUnrealizedPnl < 0) stockColorDown else ColorTextPrimary
                                            val unPrefix = if (totalUnrealizedPnl > 0) "+" else ""
                                            Text("$unPrefix${numberFormat.format(totalUnrealizedPnl)} 원", color = unColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        }
                                        Spacer(Modifier.height(8.dp))
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("총 매입금액", color = ColorTextSecondary, fontSize = 13.sp)
                                            Text("${numberFormat.format(totalPurchaseAmt)} 원", color = ColorTextPrimary, fontSize = 13.sp)
                                        }
                                        Spacer(Modifier.height(8.dp))
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("예수금(D+2)", color = ColorTextSecondary, fontSize = 13.sp)
                                            Text("${numberFormat.format(d2Deposit)} 원", color = ColorTextPrimary, fontSize = 13.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    else -> {}
                }
            }

            // 2. 보유 종목 리스트 헤더 및 필터 칩
            if (uiState is AssetsViewModel.UiState.BalanceLoaded) {
                item {
                    Spacer(Modifier.height(8.dp))
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
                }
            }

            // 3. 보유 종목 카드 리스트
            if (uiState is AssetsViewModel.UiState.BalanceLoaded) {
                val holdingList = (uiState as AssetsViewModel.UiState.BalanceLoaded).balance.holdings ?: emptyList()
                if (holdingList.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text("보유 중인 주식이 없습니다.", color = ColorTextSecondary, fontSize = 14.sp)
                        }
                    }
                } else {
                    items(holdingList, key = { it.itemCode }) { stock ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(ColorSurface, RoundedCornerShape(8.dp))
                                // 카드 클릭 시 메뉴 아코디언 토글
                                .clickable {
                                    expandedStockCode = if (expandedStockCode == stock.itemCode) null else stock.itemCode
                                }
                        ) {
                            // 💡 1. 상단 영역 (종목명 및 현재가)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(stock.itemName, color = ColorTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                Text("${numberFormat.format(stock.currentPrice)} 원", color = ColorTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            }

                            // 💡 구분선 추가
                            HorizontalDivider(
                                color = ColorSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )

                            // 💡 2. 하단 상세 내역 영역
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 좌측: 매입금액(위), 보유수량/평단가(아래)
                                Column {
                                    val purchaseAmount = stock.averagePrice * stock.quantity

                                    Text("매입금액 ${numberFormat.format(purchaseAmount)}원", color = ColorTextSecondary, fontSize = 12.sp)
                                    Spacer(Modifier.height(4.dp))
                                    Text("${numberFormat.format(stock.quantity)}주 | 평단가 ${numberFormat.format(stock.averagePrice)}", color = ColorTextSecondary, fontSize = 12.sp)
                                }

                                // 우측: 수익률(위), 수익금(아래)
                                Column(horizontalAlignment = Alignment.End) {
                                    val isProfit = stock.returnRate > 0
                                    val isLoss = stock.returnRate < 0
                                    val rateColor = if (isProfit) stockColorUp else if (isLoss) stockColorDown else ColorTextPrimary
                                    val signPrefix = if (isProfit) "+" else ""

                                    val profitAmount = (stock.currentPrice - stock.averagePrice) * stock.quantity
                                    val formattedRate = String.format("%.2f", stock.returnRate)

                                    Text("$signPrefix$formattedRate%", color = rateColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.height(4.dp))
                                    Text("$signPrefix${numberFormat.format(profitAmount)}원", color = rateColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            // 💡 3. 카드 하단 4개 버튼 아코디언 영역
                            AnimatedVisibility(
                                visible = expandedStockCode == stock.itemCode,
                                enter = expandVertically(animationSpec = tween(200)),
                                exit = shrinkVertically(animationSpec = tween(200))
                            ) {
                                Column {
                                    HorizontalDivider(color = ColorBg.copy(alpha = 0.5f))
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 8.dp, vertical = 2.dp),
                                        horizontalArrangement = Arrangement.SpaceEvenly
                                    ) {
                                        TextButton(
                                            onClick = { /* 차트 뷰 연동 */ },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                            modifier = Modifier.height(32.dp)
                                        ) {
                                            Text("차트", color = ColorTextSecondary, fontSize = 13.sp)
                                        }
                                        TextButton(
                                            onClick = { selectedStockCodeForOrder = stock.itemCode },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                            modifier = Modifier.height(32.dp)
                                        ) {
                                            Text("주문", color = ColorStatus, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        }
                                        TextButton(
                                            onClick = { /* AI 연동 */ },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                            modifier = Modifier.height(32.dp)
                                        ) {
                                            Text("AI 기업분석", color = ColorTextSecondary, fontSize = 13.sp)
                                        }
                                        TextButton(
                                            onClick = { /* 재무제표 뷰 연동 */ },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                            modifier = Modifier.height(32.dp)
                                        ) {
                                            Text("재무", color = ColorTextSecondary, fontSize = 13.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }                }
            }
        }

        // 당겨서 새로고침 인디케이터
        PullRefreshIndicator(
            refreshing = isLoading,
            state = pullRefreshState,
            modifier = Modifier.align(Alignment.TopCenter),
            contentColor = ColorStatus,
            backgroundColor = ColorSurface.copy(alpha = 0.9f)
        )

        // --- 바텀 시트 및 팝업 (기존 로직 유지) ---
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
                onOrderSubmit = { tradeType, orderType, price, quantity ->
                    pendingTradeType = tradeType
                    pendingOrderType = orderType
                    pendingPrice = price
                    pendingQuantity = quantity
                    showConfirmDialog = true
                },
                onFetchInitialPrice = { viewModel.fetchInitialPrice(it) }
            )
        }

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
                    val targetStockCode = selectedStockCodeForOrder ?: ""
                    selectedStockCodeForOrder = null

                    if (targetStockCode.isNotEmpty()) {
                        viewModel.submitStockOrder(
                            stockCode = targetStockCode,
                            tradeType = pendingTradeType,
                            orderType = pendingOrderType,
                            price = pendingPrice,
                            quantity = pendingQuantity
                        ) { isSuccess, message ->
                            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
                        }
                    }
                }
            )
        }
    }
}