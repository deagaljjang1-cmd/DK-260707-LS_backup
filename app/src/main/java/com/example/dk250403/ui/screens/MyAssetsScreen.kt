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

import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut

import androidx.compose.ui.draw.scale

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType

import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown

import androidx.compose.ui.text.style.TextOverflow

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
                2 -> TransactionHistoryScreen(viewModel = viewModel) // 임시 거래내역 뷰
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
@OptIn(ExperimentalMaterialApi::class, ExperimentalMaterial3Api::class)
@Composable
fun UnexecutedOrdersScreen(viewModel: AssetsViewModel) {
    // 💡 뷰모델에서 상/하한가 값을 관찰
    val currentLimits by viewModel.currentStockLimits.collectAsState()

    val unexecutedList by viewModel.unexecutedOrders.collectAsState()
    val isLoading by viewModel.isUnexecutedLoading.collectAsState()
    val selectedAccount by viewModel.selectedAccount.collectAsState()
    val stockMasterMap by viewModel.stockMasterMap.collectAsState()

    val numberFormat = remember { DecimalFormat("#,###") }
    val context = LocalContext.current

    val horizontalScrollState = rememberScrollState()
    var expandedOrderId by remember { mutableStateOf<Long?>(null) }

    var selectedOrders by remember { mutableStateOf(setOf<Long>()) }
    var showCancelDialog by remember { mutableStateOf(false) }
    var singleCancelTarget by remember { mutableStateOf<Pair<Long, String>?>(null) }

    // 💡 추가됨: 바텀 시트(정정 주문) 상태 관리
    var modifyTarget by remember { mutableStateOf<com.example.dk250403.network.T0425OutBlock1?>(null) }
    val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var inputModifyPrice by remember { mutableStateOf("") }
    var inputModifyQty by remember { mutableStateOf("") }

    LaunchedEffect(unexecutedList) {
        selectedOrders = selectedOrders.intersect(unexecutedList.map { it.ordno }.toSet())
    }

    val pullRefreshState = rememberPullRefreshState(
        refreshing = isLoading,
        onRefresh = { viewModel.fetchUnexecutedOrders() }
    )

    LaunchedEffect(selectedAccount) {
        if (selectedAccount.isNotEmpty()) {
            viewModel.fetchUnexecutedOrders()
        }
    }

    val colCheck = 20.dp
    val colOrdNo = 55.dp
    val colName = 70.dp
    val colExch = 35.dp
    val colType = 60.dp
    val colQty = 50.dp
    val colPrice = 80.dp
    val colCurrent = 80.dp
    val colCheQty = 65.dp
    val colUnexec = 65.dp
    val colOrdGb = 70.dp
    val colTime = 65.dp
    val colMtd = 75.dp

    if (isLoading && unexecutedList.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = ColorTextPrimary)
        }
    } else {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(ColorBg)
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { expandedOrderId = null })
                    }
            ) {
                // ==========================================
                // [표 헤더 영역]
                // ==========================================
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ColorSurfaceVariant)
                        .padding(vertical = 10.dp)
                        .height(IntrinsicSize.Min),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 12.dp).fillMaxHeight()
                    ) {
                        val isAllSelected = unexecutedList.isNotEmpty() && selectedOrders.size == unexecutedList.size

                        Box(modifier = Modifier.width(colCheck).height(18.dp), contentAlignment = Alignment.Center) {
                            Checkbox(
                                checked = isAllSelected,
                                onCheckedChange = { checked ->
                                    selectedOrders = if (checked) {
                                        unexecutedList.map { it.ordno }.toSet()
                                    } else {
                                        emptySet()
                                    }
                                },
                                modifier = Modifier.scale(0.75f),
                                colors = CheckboxDefaults.colors(checkedColor = ColorStatus, uncheckedColor = ColorTextSecondary)
                            )
                        }

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
                        modifier = Modifier.horizontalScroll(horizontalScrollState).fillMaxHeight(),
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
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 80.dp)
                        ) {
                            items(unexecutedList) { order ->
                                val isBuy = order.medosu.contains("매수") || order.medosu == "2"
                                val tradeColor = if (isBuy) ColorUp else ColorDown
                                // 💡 API가 주는 원본 텍스트(매수정정, 매도정정 등)를 그대로 노출
                                val tradeText = when (order.medosu) {
                                    "1" -> "매도"
                                    "2" -> "매수"
                                    else -> order.medosu
                                }

                                val timeFormatted = if (order.ordtime.length >= 6) {
                                    "${order.ordtime.substring(0, 2)}:${order.ordtime.substring(2, 4)}:${order.ordtime.substring(4, 6)}"
                                } else order.ordtime

                                val ordGbText = if (order.ordgb.isBlank()) "보통" else order.ordgb
                                val displayStockName = stockMasterMap[order.expcode] ?: order.expcode

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (selectedOrders.isNotEmpty()) {
                                                selectedOrders = if (selectedOrders.contains(order.ordno)) {
                                                    selectedOrders - order.ordno
                                                } else {
                                                    selectedOrders + order.ordno
                                                }
                                            } else {
                                                expandedOrderId = if (expandedOrderId == order.ordno) null else order.ordno
                                            }
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 14.dp)
                                            .height(IntrinsicSize.Min),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(start = 12.dp).fillMaxHeight()
                                        ) {
                                            Box(modifier = Modifier.width(colCheck).height(18.dp), contentAlignment = Alignment.Center) {
                                                Checkbox(
                                                    checked = selectedOrders.contains(order.ordno),
                                                    onCheckedChange = { checked ->
                                                        selectedOrders = if (checked) {
                                                            selectedOrders + order.ordno
                                                        } else {
                                                            selectedOrders - order.ordno
                                                        }
                                                    },
                                                    modifier = Modifier.scale(0.75f),
                                                    colors = CheckboxDefaults.colors(checkedColor = ColorStatus, uncheckedColor = ColorTextSecondary)
                                                )
                                            }

                                            Text(order.ordno.toString(), modifier = Modifier.width(colOrdNo), color = ColorTextPrimary, fontSize = 12.sp, textAlign = TextAlign.Center)
                                            Text(
                                                text = displayStockName,
                                                modifier = Modifier.width(colName),
                                                color = ColorTextPrimary,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                textAlign = TextAlign.Center,
                                                maxLines = 1,                            // 💡 무조건 1줄 고정
                                                overflow = TextOverflow.Ellipsis         // 💡 영역 벗어나면 "..." 표시
                                            )
                                        }

                                        Box(
                                            modifier = Modifier
                                                .padding(horizontal = 8.dp)
                                                .width(1.dp)
                                                .fillMaxHeight()
                                                .background(ColorSurfaceVariant)
                                        )

                                        Row(
                                            modifier = Modifier.horizontalScroll(horizontalScrollState).fillMaxHeight(),
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

                                    AnimatedVisibility(visible = expandedOrderId == order.ordno && selectedOrders.isEmpty()) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(ColorSurface.copy(alpha = 0.5f))
                                                .padding(horizontal = 16.dp, vertical = 12.dp),
                                            horizontalArrangement = Arrangement.End
                                        ) {
                                            OutlinedButton(
                                                onClick = {
                                                    singleCancelTarget = Pair(order.ordno, order.expcode)
                                                },
                                                modifier = Modifier.height(32.dp),
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                                colors = ButtonDefaults.outlinedButtonColors(contentColor = ColorTextPrimary)
                                            ) { Text("취소", fontSize = 12.sp, fontWeight = FontWeight.Bold) }

                                            Spacer(modifier = Modifier.width(8.dp))

                                            Button(
                                                onClick = {
                                                    // 💡 정정 타겟 지정 후 바텀 시트 열기
                                                    modifyTarget = order
                                                    inputModifyPrice = numberFormat.format(order.price)
                                                    inputModifyQty = numberFormat.format(order.ordrem)
                                                    // 💡 바텀 시트가 열리면서 백그라운드에서 정확한 상/하한가 호출!
                                                    viewModel.fetchStockLimitsForModify(order.expcode)
                                                },
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

                    PullRefreshIndicator(
                        refreshing = isLoading,
                        state = pullRefreshState,
                        modifier = Modifier.align(Alignment.TopCenter),
                        contentColor = Color(0xFF39FF81),
                        backgroundColor = ColorSurface.copy(alpha = 0.9f)
                    )
                }
            }

            // ==========================================
            // [일괄 취소 플로팅 액션 버튼 (FAB)]
            // ==========================================
            AnimatedVisibility(
                visible = selectedOrders.isNotEmpty(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp),
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                ExtendedFloatingActionButton(
                    onClick = { showCancelDialog = true },
                    containerColor = Color(0xFF444444),
                    contentColor = Color.White
                ) {
                    Text(
                        text = "${selectedOrders.size}건 일괄 취소",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }

            // ==========================================
            // [개별 취소 확인 다이얼로그]
            // ==========================================
            singleCancelTarget?.let { (ordNo, expCode) ->
                AlertDialog(
                    onDismissRequest = { singleCancelTarget = null },
                    containerColor = ColorSurface,
                    titleContentColor = ColorTextPrimary,
                    textContentColor = ColorTextSecondary,
                    title = { Text(text = "개별 취소 확인", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
                    text = {
                        val stockName = stockMasterMap[expCode] ?: expCode
                        Text(text = "[$stockName] 주문(번호: $ordNo)을 취소하시겠습니까?", fontSize = 14.sp)
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                viewModel.cancelSingleOrder(orgOrdNo = ordNo, stockCode = expCode) { success, msg ->
                                    android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                                }
                                singleCancelTarget = null
                            }
                        ) { Text("확인", color = Color(0xFF39FF81), fontWeight = FontWeight.Bold) }
                    },
                    dismissButton = {
                        TextButton(onClick = { singleCancelTarget = null }) { Text("취소", color = ColorTextSecondary) }
                    }
                )
            }

            // ==========================================
            // [일괄 취소 확인 다이얼로그]
            // ==========================================
            if (showCancelDialog) {
                AlertDialog(
                    onDismissRequest = { showCancelDialog = false },
                    containerColor = ColorSurface,
                    titleContentColor = ColorTextPrimary,
                    textContentColor = ColorTextSecondary,
                    title = { Text(text = "일괄 취소 확인", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
                    text = { Text(text = "선택하신 ${selectedOrders.size}건의 주문을 일괄 취소하시겠습니까?", fontSize = 14.sp) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                viewModel.cancelSelectedOrders(selectedOrders) { successCount, failCount ->
                                    val resultMsg = if (failCount == 0) "${successCount}건 일괄 취소 완료" else "${successCount}건 성공, ${failCount}건 실패"
                                    android.widget.Toast.makeText(context, resultMsg, android.widget.Toast.LENGTH_SHORT).show()
                                }
                                selectedOrders = emptySet()
                                showCancelDialog = false
                            }
                        ) { Text("확인", color = Color(0xFF39FF81), fontWeight = FontWeight.Bold) }
                    },
                    dismissButton = {
                        TextButton(onClick = { showCancelDialog = false }) { Text("취소", color = ColorTextSecondary) }
                    }
                )
            }
        }
    }

// ==========================================
    // 💡 [정정 주문 바텀 시트]
    // ==========================================
    if (modifyTarget != null) {
        val target = modifyTarget!!
        val stockName = stockMasterMap[target.expcode] ?: target.expcode
        val isBuy = target.medosu.contains("매수") || target.medosu == "2"
        val tradeColor = if (isBuy) ColorUp else ColorDown
        // 💡 바텀 시트 상단 타이틀에도 원본 텍스트 노출
        val tradeText = when (target.medosu) {
            "1" -> "매도"
            "2" -> "매수"
            else -> target.medosu
        }

        // 💡 상/하한가 임시 계산 (전일 종가 데이터가 없으므로 현재가 기준으로 대략 ±30% 산출)
        // 실제 운영 시에는 t1102 등에서 받아온 정확한 상/하한가(uplmtprice, dnlmtprice)를 적용해야 합니다.
        val estimatedUpperLimit = (target.price1 * 1.3).toLong()
        val estimatedLowerLimit = (target.price1 * 0.7).toLong()

        ModalBottomSheet(
            onDismissRequest = {
                modifyTarget = null
                viewModel.clearStockLimits() // 💡 시트 닫을 때 값 초기화
            },
            sheetState = bottomSheetState,
            containerColor = ColorSurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .padding(bottom = 32.dp)
            ) {
                // 상단 타이틀
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 24.dp)) {
                    Text(stockName, color = ColorTextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("정정 주문 ($tradeText)", color = tradeColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.weight(1f))
                    Text("주문번호: ${target.ordno}", color = ColorTextSecondary, fontSize = 12.sp)
                }

                // 주문 단가 정정
                Text("주문 단가", color = ColorTextSecondary, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 16.dp)) {
                    OutlinedTextField(
                        value = inputModifyPrice,
                        onValueChange = {
                            val clean = it.replace(",", "").filter { char -> char.isDigit() }
                            inputModifyPrice = if (clean.isNotEmpty()) numberFormat.format(clean.toLong()) else ""
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = ColorTextPrimary,
                            unfocusedTextColor = ColorTextPrimary,
                            focusedBorderColor = ColorStatus,
                            unfocusedBorderColor = ColorSurfaceVariant
                        ),
                        singleLine = true,
                        trailingIcon = {
                            Column(
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowUp,
                                    contentDescription = "단가 올림",
                                    tint = ColorTextSecondary,
                                    modifier = Modifier.clickable {
                                        val current = inputModifyPrice.replace(",", "").toLongOrNull() ?: 0L
                                        val tick = getTickSize(current) // 💡 공용 함수에서 호가 단위 산출
                                        inputModifyPrice = numberFormat.format(current + tick)
                                    }.size(24.dp)
                                )
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = "단가 내림",
                                    tint = ColorTextSecondary,
                                    modifier = Modifier.clickable {
                                        val current = inputModifyPrice.replace(",", "").toLongOrNull() ?: 0L
                                        if (current > 0) {
                                            // 정확한 호가 내림을 위해 현재가보다 1원 낮은 금액의 틱을 구함
                                            val tick = getTickSize(current - 1)
                                            inputModifyPrice = numberFormat.format(current - tick)
                                        }
                                    }.size(24.dp)
                                )
                            }
                        }
                    )
                    Spacer(modifier = Modifier.width(8.dp))

                    // 💡 퀵버튼 영역 (현재가 / 상 / 하)
                    Button(
                        onClick = { inputModifyPrice = numberFormat.format(target.price1) },
                        colors = ButtonDefaults.buttonColors(containerColor = ColorSurfaceVariant, contentColor = ColorTextPrimary),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        modifier = Modifier.height(36.dp) // 버튼 높이 슬림화
                    ) { Text("현재가", fontSize = 13.sp) }

                    Spacer(modifier = Modifier.width(4.dp))

                    Button(
                        onClick = { currentLimits?.first?.let { inputModifyPrice = numberFormat.format(it) } },
                        colors = ButtonDefaults.buttonColors(containerColor = ColorUp, contentColor = Color.White),
                        contentPadding = PaddingValues(0.dp), // 💡 내부 여백 완전 제거
                        modifier = Modifier.size(36.dp) // 💡 가로세로 36dp로 크기 강제 고정 (텍스트 한 글자 크기)
                    ) { Text("상", fontSize = 13.sp) }

                    Spacer(modifier = Modifier.width(4.dp))

                    Button(
                        onClick = { currentLimits?.second?.let { inputModifyPrice = numberFormat.format(it) } },
                        colors = ButtonDefaults.buttonColors(containerColor = ColorDown, contentColor = Color.White),
                        contentPadding = PaddingValues(0.dp), // 💡 내부 여백 완전 제거
                        modifier = Modifier.size(36.dp) // 💡 가로세로 36dp로 크기 강제 고정
                    ) { Text("하", fontSize = 13.sp) }
                }

                // 미체결 수량 정정
                Text("주문 수량 (미체결: ${numberFormat.format(target.ordrem)}주)", color = ColorTextSecondary, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 32.dp)) {
                    OutlinedTextField(
                        value = inputModifyQty,
                        onValueChange = {
                            val clean = it.replace(",", "").filter { char -> char.isDigit() }
                            val num = clean.toLongOrNull() ?: 0L
                            // 기존 미체결 수량을 초과하지 못하도록 제한
                            if (num <= target.ordrem) {
                                inputModifyQty = if (clean.isNotEmpty()) numberFormat.format(num) else ""
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = ColorTextPrimary,
                            unfocusedTextColor = ColorTextPrimary,
                            focusedBorderColor = ColorStatus,
                            unfocusedBorderColor = ColorSurfaceVariant
                        ),
                        singleLine = true,
                        trailingIcon = {
                            Column(
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowUp,
                                    contentDescription = "수량 올림",
                                    tint = ColorTextSecondary,
                                    modifier = Modifier.clickable {
                                        val current = inputModifyQty.replace(",", "").toLongOrNull() ?: 0L
                                        if (current < target.ordrem) {
                                            inputModifyQty = numberFormat.format(current + 1)
                                        }
                                    }.size(24.dp)
                                )
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = "수량 내림",
                                    tint = ColorTextSecondary,
                                    modifier = Modifier.clickable {
                                        val current = inputModifyQty.replace(",", "").toLongOrNull() ?: 0L
                                        if (current > 0) {
                                            inputModifyQty = numberFormat.format(current - 1)
                                        }
                                    }.size(24.dp)
                                )
                            }
                        }
                    )
                    Spacer(modifier = Modifier.width(8.dp))

                    // 💡 퀵버튼 영역 (50% / 전량)
                    Button(
                        onClick = { inputModifyQty = numberFormat.format(target.ordrem / 2) },
                        colors = ButtonDefaults.buttonColors(containerColor = ColorSurfaceVariant, contentColor = ColorTextPrimary),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) { Text("50%") }
                    Spacer(modifier = Modifier.width(4.dp))
                    Button(
                        onClick = { inputModifyQty = numberFormat.format(target.ordrem) },
                        colors = ButtonDefaults.buttonColors(containerColor = ColorSurfaceVariant, contentColor = ColorTextPrimary),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) { Text("전량") }
                }

                // 하단 전송 버튼
                Button(
                    onClick = {
                        val newPrice = inputModifyPrice.replace(",", "").toLongOrNull() ?: 0L
                        val newQty = inputModifyQty.replace(",", "").toLongOrNull() ?: 0L

                        if (newPrice > 0 && newQty > 0) {
                            viewModel.modifySingleOrder(
                                orgOrdNo = target.ordno,
                                stockCode = target.expcode,
                                newQty = newQty,
                                newPrice = newPrice
                            ) { success, msg ->
                                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                            }
                            modifyTarget = null
                        } else {
                            android.widget.Toast.makeText(context, "단가와 수량을 정확히 입력하세요.", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF39FF81), contentColor = ColorBg)
                ) {
                    Text("정정 주문 접수", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
    }
}


// 거래내역
@OptIn(ExperimentalMaterialApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TransactionHistoryScreen(viewModel: AssetsViewModel) {
    val executedList by viewModel.executedOrders.collectAsState()
    val isLoading by viewModel.isExecutedLoading.collectAsState()
    val totalBuyAmount by viewModel.todayTotalBuy.collectAsState()
    val totalSellAmount by viewModel.todayTotalSell.collectAsState()
    val stockMasterMap by viewModel.stockMasterMap.collectAsState()
    val selectedAccount by viewModel.selectedAccount.collectAsState() // 계좌 감지용 추가

    // 💡 2. 계좌가 선택되거나 화면이 열릴 때 자동으로 API를 호출하는 트리거 추가
    LaunchedEffect(selectedAccount) {
        if (selectedAccount.isNotEmpty()) {
            viewModel.fetchExecutedOrders()
        }
    }

    var selectedFilter by remember { mutableStateOf("전체") } // 필터 상태: 전체, 매수, 매도
    val numberFormat = remember { java.text.DecimalFormat("#,###") }
    val horizontalScrollState = rememberScrollState()

    // 💡 3. 당겨서 새로고침 액션에 실제 함수 연결
    val pullRefreshState = rememberPullRefreshState(
        refreshing = isLoading,
        onRefresh = { viewModel.fetchExecutedOrders() }
    )

    // 필터링 적용된 리스트
    val filteredList = executedList.filter { order ->
        when (selectedFilter) {
            "매수" -> order.medosu.contains("매수") || order.medosu == "2"
            "매도" -> order.medosu.contains("매도") || order.medosu == "1"
            else -> true
        }
    }

    // 컬럼 너비 설정 (미체결 내역과 동일하게 유지하되 체크박스 제외)
    val colTime = 50.dp
    val colName = 75.dp
    val colExch = 40.dp
    val colType = 50.dp
    val colPrice = 70.dp  // 체결단가
    val colQty = 60.dp    // 체결수량
    val colOrdGb = 70.dp
    val colOrdNo = 65.dp
    val colMtd = 75.dp

    Column(modifier = Modifier.fillMaxSize().background(ColorBg)) {

        // ==========================================
        // 1. 상단 대시보드 (총 매수/매도 금액)
        // ==========================================
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = ColorSurface),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("당일 총 매수", fontSize = 12.sp, color = ColorTextSecondary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("${numberFormat.format(totalBuyAmount)} 원", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = ColorUp)
                }
            }
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = ColorSurface),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("당일 총 매도", fontSize = 12.sp, color = ColorTextSecondary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("${numberFormat.format(totalSellAmount)} 원", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = ColorDown)
                }
            }
        }

        // ==========================================
        // 2. 필터 칩 (전체 / 매수 / 매도)
        // ==========================================
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("전체", "매수", "매도").forEach { filter ->
                val isSelected = selectedFilter == filter

                // 💡 선택 시 '매수'는 빨강, '매도'는 파랑, '전체'는 흰색으로 동적 할당
                val activeTextColor = when (filter) {
                    "매수" -> ColorUp
                    "매도" -> ColorDown
                    else -> ColorTextPrimary
                }

                FilterChip(
                    selected = isSelected,
                    onClick = { selectedFilter = filter },
                    label = {
                        Text(
                            text = filter,
                            fontSize = 12.sp,
                            // 💡 선택 여부에 따라 폰트 굵기도 조금 더 강조
                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Normal
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ColorSurfaceVariant,
                        selectedLabelColor = activeTextColor, // 💡 여기에 동적 컬러 적용
                        containerColor = ColorSurface,
                        labelColor = ColorTextSecondary       // 비활성화 시 기존 컬러 유지
                    ),
                    border = null
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ==========================================
        // 3. 표 헤더 영역 (가로 스크롤)
        // ==========================================
        Row(
            modifier = Modifier.fillMaxWidth().background(ColorSurfaceVariant).padding(vertical = 10.dp).height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 고정 영역 (주문번호, 종목명)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp).fillMaxHeight()) {
                Text("체결시간", modifier = Modifier.width(colTime), color = ColorTextSecondary, fontSize = 11.sp, textAlign = TextAlign.Center)
                Text("종목명", modifier = Modifier.width(colName), color = ColorTextSecondary, fontSize = 11.sp, textAlign = TextAlign.Center)
            }

            Box(modifier = Modifier.padding(horizontal = 8.dp).width(1.dp).fillMaxHeight().background(ColorTextSecondary.copy(alpha = 0.3f)))

            // 스크롤 영역 (거래소, 구분, 체결단가, 체결수량 등 모든 항목)
            Row(modifier = Modifier.horizontalScroll(horizontalScrollState).fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
                Text("거래소", modifier = Modifier.width(colExch), color = ColorTextSecondary, fontSize = 11.sp, textAlign = TextAlign.Center)
                Text("구분", modifier = Modifier.width(colType), color = ColorTextSecondary, fontSize = 11.sp, textAlign = TextAlign.Center)
                Text("체결단가", modifier = Modifier.width(colPrice), color = ColorTextSecondary, fontSize = 11.sp, textAlign = TextAlign.End)
                Text("체결수량", modifier = Modifier.width(colQty), color = ColorTextSecondary, fontSize = 11.sp, textAlign = TextAlign.End)
                Text("주문유형", modifier = Modifier.width(colOrdGb), color = ColorTextSecondary, fontSize = 11.sp, textAlign = TextAlign.Center)
                Text("원주문번호", modifier = Modifier.width(colOrdNo), color = ColorTextSecondary, fontSize = 11.sp, textAlign = TextAlign.Center)
                Text("매체", modifier = Modifier.width(colMtd), color = ColorTextSecondary, fontSize = 11.sp, textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.width(16.dp))
            }
        }

        // ==========================================
        // 4. 표 데이터 및 새로고침 영역
        // ==========================================
        Box(modifier = Modifier.fillMaxSize().pullRefresh(pullRefreshState)) {
            if (filteredList.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.Center) {
                    Text("당일 거래내역이 없습니다.", color = ColorTextSecondary)
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 80.dp)) {
                    items(filteredList) { order ->
                        val isBuy = order.medosu.contains("매수") || order.medosu == "2"
                        val tradeColor = if (isBuy) ColorUp else ColorDown
                        val tradeText = when (order.medosu) {
                            "1" -> "매도"
                            "2" -> "매수"
                            else -> order.medosu
                        }

                        val timeFormatted = if (order.ordtime.length >= 6) {
                            "${order.ordtime.substring(0, 2)}:${order.ordtime.substring(2, 4)}:${order.ordtime.substring(4, 6)}"
                        } else order.ordtime

                        val ordGbText = if (order.ordgb.isBlank()) "보통" else order.ordgb
                        val displayStockName = stockMasterMap[order.expcode] ?: order.expcode

                        // 💡 읽기 전용이므로 Modifier.clickable 제외
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp).height(IntrinsicSize.Min),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp).fillMaxHeight()) {
                                    Text(timeFormatted, modifier = Modifier.width(colTime), color = ColorTextSecondary, fontSize = 12.sp, textAlign = TextAlign.Center)
                                    Text(
                                        text = displayStockName, modifier = Modifier.width(colName), color = ColorTextPrimary, fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                }

                                Box(modifier = Modifier.padding(horizontal = 8.dp).width(1.dp).fillMaxHeight().background(ColorSurfaceVariant))

                                Row(modifier = Modifier.horizontalScroll(horizontalScrollState).fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
                                    Text(order.exchname.take(3), modifier = Modifier.width(colExch), color = ColorTextSecondary, fontSize = 12.sp, textAlign = TextAlign.Center)
                                    Text(tradeText, modifier = Modifier.width(colType), color = tradeColor, fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                                    Text(numberFormat.format(order.cheprice), modifier = Modifier.width(colPrice), color = ColorTextPrimary, fontSize = 12.sp, textAlign = TextAlign.End)
                                    Text(numberFormat.format(order.cheqty), modifier = Modifier.width(colQty), color = ColorTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
                                    Text(ordGbText, modifier = Modifier.width(colOrdGb), color = ColorTextSecondary, fontSize = 12.sp, textAlign = TextAlign.Center)
                                    Text(order.ordno.toString(), modifier = Modifier.width(colOrdNo), color = ColorTextPrimary, fontSize = 12.sp, textAlign = TextAlign.Center)
                                    Text(order.ordermtd, modifier = Modifier.width(colMtd), color = ColorTextSecondary, fontSize = 12.sp, textAlign = TextAlign.Center)
                                    Spacer(modifier = Modifier.width(16.dp))
                                }
                            }
                            HorizontalDivider(color = ColorSurfaceVariant.copy(alpha = 0.5f))
                        }
                    }
                }
            }

            PullRefreshIndicator(
                refreshing = isLoading,
                state = pullRefreshState,
                modifier = Modifier.align(Alignment.TopCenter),
                contentColor = ColorStatus,
                backgroundColor = ColorSurface.copy(alpha = 0.9f)
            )
        }
    }
}