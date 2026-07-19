package com.example.dk250403.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.dk250403.ColorBg
import com.example.dk250403.ColorStatus
import com.example.dk250403.ColorSurface
import com.example.dk250403.ColorTextPrimary
import com.example.dk250403.ColorTextSecondary
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import java.text.DecimalFormat
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.PaddingValues
import com.example.dk250403.ColorUp
import com.example.dk250403.ColorDown
import com.example.dk250403.ColorSurfaceVariant
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color

import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.foundation.verticalScroll



@Composable
fun MyAssetsScreen(
    modifier: Modifier = Modifier,
    viewModel: AssetsViewModel = viewModel()
) {
    val accounts by viewModel.registeredAccounts.collectAsState()
    val selectedAccount by viewModel.selectedAccount.collectAsState()
    var expanded by remember { mutableStateOf(false) }

    val tabs = listOf("잔고", "미체결", "거래내역")
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        viewModel.initAssets()
    }

    Column(modifier = modifier.fillMaxSize().background(ColorBg)) {
        // 1. 상단 계좌 선택 드롭박스
        Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            OutlinedCard(
                modifier = Modifier.fillMaxWidth().clickable { expanded = true },
                colors = CardDefaults.cardColors(containerColor = ColorSurface)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (selectedAccount.isEmpty()) "계좌를 선택하세요" else maskAccount(selectedAccount),
                        color = ColorTextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Icon(Icons.Default.ArrowDropDown, contentDescription = "계좌 선택", tint = ColorTextPrimary)
                }
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.fillMaxWidth(0.9f).background(ColorSurface)
            ) {
                accounts.forEach { acc ->
                    DropdownMenuItem(
                        text = { Text(maskAccount(acc), color = ColorTextPrimary) },
                        onClick = {
                            viewModel.requestBalanceForAccount(acc)
                            expanded = false
                        }
                    )
                }
            }
        }

        // 2. 탭 메뉴 (잔고 / 미체결 / 거래내역)
        TabRow(
            selectedTabIndex = pagerState.currentPage,
            containerColor = ColorBg,
            contentColor = ColorTextPrimary,
            indicator = { tabPositions ->
                if (pagerState.currentPage < tabPositions.size) {
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[pagerState.currentPage]),
                        color = ColorStatus
                    )
                }
            }
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = { coroutineScope.launch { pagerState.animateScrollToPage(index) } },
                    text = {
                        Text(
                            text = title,
                            fontWeight = if (pagerState.currentPage == index) FontWeight.Bold else FontWeight.Normal,
                            color = if (pagerState.currentPage == index) ColorTextPrimary else ColorTextSecondary
                        )
                    }
                )
            }
        }

        // 3. 탭 페이저 (스와이프 기능 지원)
        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
            when (page) {
                0 -> AssetsScreen(viewModel = viewModel) // 기존 잔고 화면 (다이어트 된 버전)
                1 -> UnexecutedOrdersScreen(viewModel = viewModel) // 미체결
                2 -> TransactionHistoryScreen() // 임시 거래내역 뷰
            }
        }
    }
}

// 계좌번호 마스킹 함수 (공용)
private fun maskAccount(accNumber: String): String {
    return if (accNumber.length >= 6) {
        val firstPart = accNumber.take(4)
        val lastPart = accNumber.takeLast(2)
        val middleStars = "*".repeat(accNumber.length - 6)
        "$firstPart$middleStars$lastPart"
    } else accNumber
}


//미체결
@OptIn(ExperimentalMaterialApi::class)
@Composable
fun UnexecutedOrdersScreen(viewModel: AssetsViewModel) {
    val unexecutedList by viewModel.unexecutedOrders.collectAsState()
    val isLoading by viewModel.isUnexecutedLoading.collectAsState()
    val selectedAccount by viewModel.selectedAccount.collectAsState()
    val numberFormat = remember { DecimalFormat("#,###") }
    val context = LocalContext.current

    val horizontalScrollState = rememberScrollState()
    var expandedOrderId by remember { mutableStateOf<Long?>(null) }

    // 💡 에러 없는 안정적인 버전의 당겨서 새로고침 상태 관리
    val pullRefreshState = rememberPullRefreshState(
        refreshing = isLoading,
        onRefresh = { viewModel.fetchUnexecutedOrders() }
    )

    LaunchedEffect(selectedAccount) {
        if (selectedAccount.isNotEmpty()) {
            viewModel.fetchUnexecutedOrders()
        }
    }

    val colOrdNo = 60.dp
    val colName = 85.dp
    val colExch = 50.dp
    val colType = 40.dp
    val colQty = 65.dp
    val colPrice = 80.dp
    val colCurrent = 80.dp
    val colCheQty = 65.dp
    val colUnexec = 65.dp
    val colOrdGb = 70.dp
    val colTime = 65.dp
    val colMtd = 75.dp

    // 💡 수정됨: 문제가 된 isRefreshing 조건을 제거하고 심플하게 처리
    if (isLoading && unexecutedList.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = ColorTextPrimary)
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(ColorBg)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { expandedOrderId = null })
                }
        ) {

            // ==========================================
            // [표 헤더 영역] - 고정 (새로고침 영향 안 받음)
            // ==========================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ColorSurfaceVariant)
                    .padding(vertical = 10.dp)
                    .height(IntrinsicSize.Min)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 12.dp)
                ) {
                    Text("주문번호", modifier = Modifier.width(colOrdNo), color = ColorTextSecondary, fontSize = 11.sp, textAlign = TextAlign.Center)
                    Text("종목명", modifier = Modifier.width(colName), color = ColorTextSecondary, fontSize = 11.sp, textAlign = TextAlign.Center)
                }

                Box(
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(ColorTextSecondary.copy(alpha = 0.3f))
                )

                Row(
                    modifier = Modifier.horizontalScroll(horizontalScrollState),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("거래소", modifier = Modifier.width(colExch), color = ColorTextSecondary, fontSize = 11.sp, textAlign = TextAlign.Center)
                    Text("구분", modifier = Modifier.width(colType), color = ColorTextSecondary, fontSize = 11.sp, textAlign = TextAlign.Center)
                    Text("주문수량", modifier = Modifier.width(colQty), color = ColorTextSecondary, fontSize = 11.sp, textAlign = TextAlign.End)
                    Text("주문가격", modifier = Modifier.width(colPrice), color = ColorTextSecondary, fontSize = 11.sp, textAlign = TextAlign.End)
                    Text("현재가", modifier = Modifier.width(colCurrent), color = ColorTextSecondary, fontSize = 11.sp, textAlign = TextAlign.End)
                    Text("체결수량", modifier = Modifier.width(colCheQty), color = ColorTextSecondary, fontSize = 11.sp, textAlign = TextAlign.End)
                    Text("미체결량", modifier = Modifier.width(colUnexec), color = ColorTextSecondary, fontSize = 11.sp, textAlign = TextAlign.End)
                    Text("주문유형", modifier = Modifier.width(colOrdGb), color = ColorTextSecondary, fontSize = 11.sp, textAlign = TextAlign.Center)
                    Text("주문시간", modifier = Modifier.width(colTime), color = ColorTextSecondary, fontSize = 11.sp, textAlign = TextAlign.Center)
                    Text("매체", modifier = Modifier.width(colMtd), color = ColorTextSecondary, fontSize = 11.sp, textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.width(16.dp))
                }
            }

            // ==========================================
            // [표 데이터 및 새로고침 영역]
            // ==========================================
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pullRefresh(pullRefreshState)
            ) {
                if (unexecutedList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("미체결 내역이 없습니다.", color = ColorTextSecondary)
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(unexecutedList) { order ->
                            val isBuy = order.medosu.contains("매수") || order.medosu == "2"
                            val tradeColor = if (isBuy) ColorUp else ColorDown
                            val tradeText = if (isBuy) "매수" else "매도"

                            val timeFormatted = if (order.ordtime.length >= 6) {
                                "${order.ordtime.substring(0, 2)}:${order.ordtime.substring(2, 4)}:${order.ordtime.substring(4, 6)}"
                            } else order.ordtime

                            val ordGbText = if (order.ordgb.isBlank()) "보통" else order.ordgb

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { expandedOrderId = if (expandedOrderId == order.ordno) null else order.ordno }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 14.dp)
                                        .height(IntrinsicSize.Min)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(start = 12.dp)
                                    ) {
                                        Text(order.ordno.toString(), modifier = Modifier.width(colOrdNo), color = ColorTextPrimary, fontSize = 12.sp, textAlign = TextAlign.Center)
                                        Text(order.expcode, modifier = Modifier.width(colName), color = ColorTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                                    }

                                    Box(
                                        modifier = Modifier
                                            .padding(horizontal = 8.dp)
                                            .width(1.dp)
                                            .fillMaxHeight()
                                            .background(ColorSurfaceVariant)
                                    )

                                    Row(
                                        modifier = Modifier.horizontalScroll(horizontalScrollState),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(order.exchname.take(3), modifier = Modifier.width(colExch), color = ColorTextSecondary, fontSize = 12.sp, textAlign = TextAlign.Center)
                                        Text(tradeText, modifier = Modifier.width(colType), color = tradeColor, fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                                        Text(numberFormat.format(order.qty), modifier = Modifier.width(colQty), color = ColorTextPrimary, fontSize = 12.sp, textAlign = TextAlign.End)
                                        Text(numberFormat.format(order.price), modifier = Modifier.width(colPrice), color = ColorTextPrimary, fontSize = 12.sp, textAlign = TextAlign.End)
                                        Text(numberFormat.format(order.price1), modifier = Modifier.width(colCurrent), color = ColorTextPrimary, fontSize = 12.sp, textAlign = TextAlign.End)
                                        Text(numberFormat.format(order.cheqty), modifier = Modifier.width(colCheQty), color = ColorTextSecondary, fontSize = 12.sp, textAlign = TextAlign.End)
                                        Text(numberFormat.format(order.ordrem), modifier = Modifier.width(colUnexec), color = tradeColor, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.End)
                                        Text(ordGbText, modifier = Modifier.width(colOrdGb), color = ColorTextSecondary, fontSize = 12.sp, textAlign = TextAlign.Center)
                                        Text(timeFormatted, modifier = Modifier.width(colTime), color = ColorTextSecondary, fontSize = 12.sp, textAlign = TextAlign.Center)
                                        Text(order.ordermtd, modifier = Modifier.width(colMtd), color = ColorTextSecondary, fontSize = 12.sp, textAlign = TextAlign.Center)
                                        Spacer(modifier = Modifier.width(16.dp))
                                    }
                                }

                                AnimatedVisibility(visible = expandedOrderId == order.ordno) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(ColorSurface.copy(alpha = 0.5f))
                                            .padding(horizontal = 16.dp, vertical = 12.dp),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        OutlinedButton(
                                            onClick = { android.widget.Toast.makeText(context, "주문 취소 API 연동 예정", android.widget.Toast.LENGTH_SHORT).show() },
                                            modifier = Modifier.height(32.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ColorTextPrimary)
                                        ) { Text("취소", fontSize = 12.sp, fontWeight = FontWeight.Bold) }

                                        Spacer(modifier = Modifier.width(8.dp))

                                        Button(
                                            onClick = { android.widget.Toast.makeText(context, "단가 정정 API 연동 예정", android.widget.Toast.LENGTH_SHORT).show() },
                                            modifier = Modifier.height(32.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = ColorSurfaceVariant, contentColor = ColorTextPrimary)
                                        ) { Text("정정", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                                    }
                                }

                                HorizontalDivider(color = ColorSurfaceVariant.copy(alpha = 0.5f))
                            }
                        }
                    }
                }
                // 💡 상단에서 돌아가는 새로고침 인디케이터
                PullRefreshIndicator(
                    refreshing = isLoading,
                    state = pullRefreshState,
                    modifier = Modifier.align(Alignment.TopCenter),
                    contentColor = Color(0xFF39FF81),
                    backgroundColor = ColorSurface.copy(alpha = 0.9f)
                )
            }
        }
    }
}
@Composable
fun TransactionHistoryScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("거래 내역이 없습니다.", color = ColorTextSecondary)
    }
}