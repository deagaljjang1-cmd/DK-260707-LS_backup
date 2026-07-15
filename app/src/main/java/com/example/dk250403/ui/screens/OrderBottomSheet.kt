package com.example.dk250403.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dk250403.ColorBg
import com.example.dk250403.ColorSurface
import com.example.dk250403.ColorTextPrimary
import com.example.dk250403.ColorTextSecondary
import java.text.DecimalFormat
import kotlin.math.abs
import kotlin.math.roundToLong
import androidx.compose.ui.focus.onFocusChanged

enum class TradeType { BUY, SELL }
enum class OrderType { LIMIT, MARKET }

fun getTickSize(price: Long): Long {
    return when {
        price < 2000 -> 1
        price < 5000 -> 5
        price < 20000 -> 10
        price < 50000 -> 50
        price < 200000 -> 100
        price < 500000 -> 500
        else -> 1000
    }
}

fun snapToValidTick(price: Long): Long {
    if (price < 1L) return 1L
    val tick = getTickSize(price)
    val remainder = price % tick
    if (remainder == 0L) return price
    return if (remainder >= tick / 2.0) price + (tick - remainder) else price - remainder
}

fun calculatePriceByTick(basePrice: Long, ticks: Int): Long {
    var p = basePrice
    if (ticks > 0) {
        for (i in 1..ticks) p += getTickSize(p)
    } else if (ticks < 0) {
        for (i in 1..-ticks) {
            val temp = (p - 1).coerceAtLeast(1L)
            p -= getTickSize(temp)
        }
    }
    return snapToValidTick(p)
}

// 💡 한국거래소(KRX) 상하한가 제한폭 절사 알고리즘
fun getKrxLimitWidth(basePrice: Long): Long {
    val maxLimit = (basePrice * 0.3)
    val tickSize = getTickSize(basePrice)
    // 기준가의 호가 단위 미만을 강제로 버림(절사) 처리
    return (maxLimit / tickSize).toLong() * tickSize
}

// 💡 오터치 방지를 위한 안전 여백 및 내장형 상하 증감 버튼 컴포저블
@Composable
fun EmbeddedStepper(
    onUpClick: () -> Unit,
    onDownClick: () -> Unit,
    enabled: Boolean = true
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.size(width = 60.dp, height = 48.dp)
    ) {
        Spacer(modifier = Modifier.width(12.dp))
        Box(modifier = Modifier.width(1.dp).fillMaxHeight(0.7f).background(ColorSurface))

        Column(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth().then(if (enabled) Modifier.clickable(onClick = onUpClick) else Modifier),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "증가", tint = if (enabled) ColorTextPrimary else ColorTextSecondary)
            }
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(ColorSurface.copy(alpha = 0.5f)))
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth().then(if (enabled) Modifier.clickable(onClick = onDownClick) else Modifier),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "감소", tint = if (enabled) ColorTextPrimary else ColorTextSecondary)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderBottomSheet(
    stockName: String,
    stockCode: String,
    currentPrice: Long,
    todayChangeRate: Double,
    yesterdayPrice: Long,
    availableCash: Long,
    holdingQuantity: Long,
    onDismiss: () -> Unit,
    onOrderSubmit: (TradeType, OrderType, Long, Long) -> Unit,
    // 💡 [t1102 연동 추가] 부모 화면(AssetsScreen)에서 ViewModel의 fetchInitialPrice를 넘겨받는 람다
    onFetchInitialPrice: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val numberFormat = remember { DecimalFormat("#,###") }

    // 💡 [t1102 연동 추가] 바텀 시트가 열릴 때 딱 한 번 API 호출 트리거 작동
    LaunchedEffect(stockCode) {
        onFetchInitialPrice(stockCode)
    }

    val colorBuy = Color(0xFFFF3737)
    val colorSell = Color(0xFF37C4FF)
    val colorFarming = Color(0xFF39FF81) // 💡 농사 주문 테마색 (초록)

    // 💡 1. 미보유/보유 상태에 따라 초기 탭과 수량, 금액, 텍스트를 자동 세팅
    var tradeType by remember { mutableStateOf(if (holdingQuantity > 0) TradeType.SELL else TradeType.BUY) }
    var orderType by remember { mutableStateOf(OrderType.LIMIT) }
    var isUserInteracted by remember { mutableStateOf(false) }

    var priceValue by remember { mutableLongStateOf(currentPrice) }
    var quantityValue by remember { mutableLongStateOf(if (holdingQuantity > 0) holdingQuantity else 0L) }
    var totalAmountValue by remember { mutableLongStateOf(if (holdingQuantity > 0) currentPrice * holdingQuantity else 0L) }
    var proportionText by remember { mutableStateOf(if (holdingQuantity > 0) "최대" else "가능") }

    // 💡 농사 매매 스위치 (상태)
    var isFarmingOrder by remember { mutableStateOf(false) }

    LaunchedEffect(currentPrice) {
        // 지정가(LIMIT)일 때는 주문 단가가 실시간 현재가를 절대 따라가지 않도록 추적 로직 삭제됨.

        // 시장가(MARKET)이고 농사 주문이 꺼져 있을 때만 실시간 현재가 변동에 맞춰 총 예상 금액 갱신
        if (orderType == OrderType.MARKET && !isFarmingOrder) {
            totalAmountValue = currentPrice * quantityValue
        }
    }


    // 💡 농사 주문 자동 계산 로직 (농사 상태가 켜지거나, 단가가 변경될 때마다 자동 실행)
// 💡 감시 키워드 맨 끝에 currentPrice 추가 (실시간 가격 변동 시 농사 수량 자동 재계산)
    LaunchedEffect(isFarmingOrder, priceValue, orderType, currentPrice) {
        if (isFarmingOrder) {
            // TODO: 추후 '투자 설정'에서 불러올 값. 현재는 테스트용 50만 원 고정
            val targetFarmingAmount = 500_000L

            val basePrice = if (orderType == OrderType.MARKET) currentPrice else priceValue
            if (basePrice > 0) {
                quantityValue = targetFarmingAmount / basePrice
                totalAmountValue = basePrice * quantityValue
            } else {
                quantityValue = 0L
                totalAmountValue = 0L
            }
            proportionText = "농사"
        } else {
            if (proportionText == "농사") proportionText = "가능"
        }
    }


    val actualBasePrice = if (yesterdayPrice > 0L) yesterdayPrice else (currentPrice / (1.0 + todayChangeRate / 100.0)).roundToLong()
    val limitWidth = getKrxLimitWidth(actualBasePrice)
    val upperLimitPrice = actualBasePrice + limitWidth
    val lowerLimitPrice = actualBasePrice - limitWidth

    val percentList = listOf(30, 25, 20, 15, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1, 0, -1, -2, -3, -4, -5, -6, -7, -8, -9, -10, -15, -20, -25, -30)
    val tickList = listOf(20, 15, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1, 0, -1, -2, -3, -4, -5, -6, -7, -8, -9, -10, -15, -20)
    val proportionList = listOf(100, 75, 50, 25, 10)

    var expandedPercent by remember { mutableStateOf(false) }
    var expandedTick by remember { mutableStateOf(false) }
    var expandedProportion by remember { mutableStateOf(false) }

    val percentScrollState = rememberLazyListState()
    val tickScrollState = rememberLazyListState()

    LaunchedEffect(expandedPercent) { if (expandedPercent) percentScrollState.scrollToItem(11) }
    LaunchedEffect(expandedTick) { if (expandedTick) tickScrollState.scrollToItem(9) }

    fun updateTotalAmount() {
        if (isFarmingOrder) return // 💡 농사 주문 켜짐 상태에서는 수동 갱신 무시
        val basePrice = if (orderType == OrderType.MARKET) currentPrice else priceValue
        totalAmountValue = basePrice * quantityValue
    }

    fun resetProportionText() { if (!isFarmingOrder) proportionText = "가능" }

    fun resetOrderState() {
        orderType = OrderType.LIMIT
        priceValue = currentPrice
        quantityValue = 0L
        totalAmountValue = 0L
        isFarmingOrder = false // 초기화 시 농사 주문도 해제
        resetProportionText()
    }

    val activeColor = if (tradeType == TradeType.BUY) colorBuy else colorSell
    val disabledModifier = Modifier.background(Color.Gray.copy(alpha = 0.15f))

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ColorBg,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .padding(bottom = 20.dp)
                .imePadding()
                .verticalScroll(rememberScrollState())
        ) {
            // 💡 [0] 보유 중 알림 라벨
            if (holdingQuantity > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentWidth(Alignment.CenterHorizontally)
                        .background(Color(0xFF39FF81), RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 1.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "보유 중 : ${numberFormat.format(holdingQuantity)}주",
                        color = ColorBg,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // [1] 헤더
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = stockName, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = ColorTextPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    val rateSymbol = if (todayChangeRate > 0) "▲" else if (todayChangeRate < 0) "▼" else ""
                    val rateColor = if (todayChangeRate > 0) colorBuy else if (todayChangeRate < 0) colorSell else ColorTextSecondary
                    Text(text = "$rateSymbol ${String.format("%.2f", abs(todayChangeRate))}%", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = rateColor)
                }
                Text(text = "${numberFormat.format(currentPrice)} 원", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // [2] 탭
            val tabIndex = if (tradeType == TradeType.BUY) 0 else 1
            TabRow(
                selectedTabIndex = tabIndex, containerColor = Color.Transparent, contentColor = activeColor,
                indicator = { TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(it[tabIndex]), color = activeColor) }
            ) {
                Tab(
                    selected = tradeType == TradeType.BUY,
                    onClick = {
                        tradeType = TradeType.BUY
                        quantityValue = 0L
                        totalAmountValue = 0L
                        resetProportionText()
                    },
                    text = { Text("매수", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = if (tradeType == TradeType.BUY) colorBuy else ColorTextSecondary) }
                )
                Tab(
                    selected = tradeType == TradeType.SELL,
                    onClick = {
                        tradeType = TradeType.SELL
                        isFarmingOrder = false // 농사 주문 강제 해제

                        if (holdingQuantity > 0) {
                            quantityValue = holdingQuantity
                            val basePrice = if (orderType == OrderType.MARKET) currentPrice else priceValue
                            totalAmountValue = basePrice * quantityValue
                            proportionText = "최대"
                        } else {
                            quantityValue = 0L
                            totalAmountValue = 0L
                            resetProportionText()
                        }
                    },
                    text = { Text("매도", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = if (tradeType == TradeType.SELL) colorSell else ColorTextSecondary) }
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

            // [3] 주문 가능 금액 표시 (농사 스위치가 드롭다운으로 이동하여 제거됨)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 32.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End // 💡 우측 끝으로 정렬
            ) {
                Text(text = "주문가능: ${numberFormat.format(availableCash)} 원", color = ColorTextSecondary, fontSize = 12.sp)
            }
            Spacer(modifier = Modifier.height(8.dp))


            // [4] 지정/시장가 선택 버튼
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { if (orderType == OrderType.MARKET) { orderType = OrderType.LIMIT; priceValue = currentPrice; updateTotalAmount() } },
                    modifier = Modifier.weight(1f).height(42.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = if (orderType == OrderType.LIMIT) Color.White else ColorSurface, contentColor = if (orderType == OrderType.LIMIT) Color.Black else ColorTextSecondary)
                ) { Text("지정가", fontWeight = FontWeight.Bold, fontSize = 14.sp) }

                Button(
                    onClick = { orderType = OrderType.MARKET; priceValue = 0L; updateTotalAmount() },
                    modifier = Modifier.weight(1f).height(42.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = if (orderType == OrderType.MARKET) Color.White else ColorSurface, contentColor = if (orderType == OrderType.MARKET) Color.Black else ColorTextSecondary)
                ) { Text("시장가", fontWeight = FontWeight.Bold, fontSize = 14.sp) }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ==================== [단가 입력 영역] ====================
            val isLimit = orderType == OrderType.LIMIT
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = if (!isLimit) "시장가" else if (priceValue == 0L) "" else numberFormat.format(priceValue),
                    onValueChange = { newValue ->
                        if (isLimit) {
                            val raw = newValue.replace(",", "")
                            if (raw.all { it.isDigit() }) {
                                val typedPrice = raw.toLongOrNull() ?: 0L

                                // 💡 입력 중에는 '상한가' 초과만 즉각 방어합니다.
                                // (여기서 하한가를 막으면 백스페이스가 다시 고장납니다)
                                priceValue = typedPrice.coerceAtMost(upperLimitPrice)

                                isUserInteracted = true
                                updateTotalAmount()
                            }
                        }
                    },
                    label = { Text("주문 단가", color = ColorTextSecondary, fontSize = 12.sp) },
                    enabled = isLimit,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .weight(0.7f)
                        // 💡 추가된 핵심 로직: 단가 입력을 마치고 빈 공간이나 다른 곳을 터치하면 하한가 미만 여부를 검사해 교정합니다.
                        .onFocusChanged { focusState ->
                            if (!focusState.isFocused && isUserInteracted) {
                                if (priceValue in 1L..<lowerLimitPrice) {
                                    priceValue = lowerLimitPrice
                                    updateTotalAmount()
                                }
                            }
                        }
                        .then(if (isLimit) Modifier else disabledModifier),
                    textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.End, fontSize = 15.sp),
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = ColorTextPrimary, unfocusedTextColor = ColorTextPrimary, disabledTextColor = ColorTextSecondary),
                    trailingIcon = {
                        EmbeddedStepper(
                            enabled = isLimit,
                            onUpClick = {
                                if (priceValue < upperLimitPrice) {
                                    priceValue = snapToValidTick(priceValue + getTickSize(priceValue)).coerceAtMost(upperLimitPrice)
                                    updateTotalAmount()
                                }
                            },
                            onDownClick = {
                                if (priceValue > lowerLimitPrice) {
                                    priceValue = snapToValidTick(priceValue - getTickSize(priceValue)).coerceAtLeast(lowerLimitPrice)
                                    updateTotalAmount()
                                }
                            }
                        )
                    }
                )

                Box(
                    modifier = Modifier
                        .weight(0.3f)
                        .height(56.dp)
                        .padding(top = 6.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isLimit) ColorSurface else Color.Gray.copy(alpha = 0.15f))
                        .then(if (isLimit) Modifier.clickable {
                            priceValue = snapToValidTick(currentPrice).coerceIn(lowerLimitPrice, upperLimitPrice)
                            updateTotalAmount()
                        } else Modifier),
                    contentAlignment = Alignment.Center
                ) {
                    Text("현재가", color = if (isLimit) Color.White else ColorTextSecondary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }

            // 드롭메뉴
            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(modifier = Modifier.weight(1f).height(42.dp).clip(RoundedCornerShape(8.dp)).border(1.dp, ColorSurface, RoundedCornerShape(8.dp)).then(if(isLimit) Modifier.clickable { expandedPercent = true } else disabledModifier), contentAlignment = Alignment.Center) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("현재가 대비 %", color = if(isLimit) Color.White else ColorTextSecondary, fontSize = 13.sp)
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = if(isLimit) Color.White else ColorTextSecondary)
                    }
                    DropdownMenu(expanded = expandedPercent, onDismissRequest = { expandedPercent = false }, modifier = Modifier.background(ColorSurface)) {
                        Box(modifier = Modifier.width(140.dp).height(240.dp)) {
                            LazyColumn(state = percentScrollState, modifier = Modifier.fillMaxSize()) {
                                items(percentList) { pct ->
                                    val sign = if (pct > 0) "+" else ""
                                    val txtColor = if (pct > 0) colorBuy else if (pct < 0) colorSell else ColorTextPrimary
                                    DropdownMenuItem(
                                        text = { Text("$sign$pct%", color = txtColor, fontWeight = FontWeight.Bold) },
                                        onClick = {
                                            val calcPrice = (currentPrice * (1.0 + pct / 100.0)).toLong()
                                            priceValue = snapToValidTick(calcPrice).coerceIn(lowerLimitPrice, upperLimitPrice)
                                            updateTotalAmount()
                                            expandedPercent = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
                Box(modifier = Modifier.weight(1f).height(42.dp).clip(RoundedCornerShape(8.dp)).border(1.dp, ColorSurface, RoundedCornerShape(8.dp)).then(if(isLimit) Modifier.clickable { expandedTick = true } else disabledModifier), contentAlignment = Alignment.Center) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("현재가 대비 호가", color = if(isLimit) Color.White else ColorTextSecondary, fontSize = 13.sp)
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = if(isLimit) Color.White else ColorTextSecondary)
                    }
                    DropdownMenu(expanded = expandedTick, onDismissRequest = { expandedTick = false }, modifier = Modifier.background(ColorSurface)) {
                        Box(modifier = Modifier.width(140.dp).height(240.dp)) {
                            LazyColumn(state = tickScrollState, modifier = Modifier.fillMaxSize()) {
                                items(tickList) { tick ->
                                    val sign = if (tick > 0) "+" else ""
                                    val txtColor = if (tick > 0) colorBuy else if (tick < 0) colorSell else ColorTextPrimary
                                    DropdownMenuItem(
                                        text = { Text("$sign${tick}호가", color = txtColor, fontWeight = FontWeight.Bold) },
                                        onClick = {
                                            priceValue = calculatePriceByTick(currentPrice, tick).coerceIn(lowerLimitPrice, upperLimitPrice)
                                            updateTotalAmount()
                                            expandedTick = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ==================== [수량 입력 영역] ====================
            // 💡 농사 매매가 켜지면 수동 조작을 차단
            val isQuantityEnabled = !isFarmingOrder

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = if (quantityValue == 0L) "" else numberFormat.format(quantityValue),
                    onValueChange = { newValue ->
                        val raw = newValue.replace(",", "")
                        if (raw.all { it.isDigit() }) {
                            quantityValue = raw.toLongOrNull() ?: 0L
                            updateTotalAmount()
                            resetProportionText()
                        }
                    },
                    label = { Text("주문 수량", color = ColorTextSecondary, fontSize = 12.sp) },
                    enabled = isQuantityEnabled, // 💡 비활성화 적용
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(0.7f).then(if (isQuantityEnabled) Modifier else disabledModifier),
                    textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.End, fontSize = 15.sp, color = if(isQuantityEnabled) ColorTextPrimary else ColorTextSecondary),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = ColorTextPrimary,
                        unfocusedTextColor = ColorTextPrimary,
                        disabledTextColor = ColorTextSecondary,
                        disabledBorderColor = ColorSurface,
                        disabledLabelColor = ColorTextSecondary
                    ),
                    trailingIcon = {
                        EmbeddedStepper(
                            enabled = isQuantityEnabled, // 💡 증감 버튼 비활성화
                            onUpClick = {
                                quantityValue += 1
                                updateTotalAmount()
                                resetProportionText()
                            },
                            onDownClick = {
                                if (quantityValue > 0) {
                                    quantityValue -= 1
                                    updateTotalAmount()
                                    resetProportionText()
                                }
                            }
                        )
                    }
                )

                // '가능' 드롭박스
                Box(
                    modifier = Modifier
                        .weight(0.3f)
                        .height(56.dp)
                        .padding(top = 6.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, ColorSurface, RoundedCornerShape(8.dp))
                        // 💡 농사 상태여도 드롭메뉴는 열려야 하므로 항상 투명 배경 & 클릭 가능 상태로 고정
                        .background(Color.Transparent)
                        .clickable { expandedProportion = true },
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = proportionText,
                            color = if (isFarmingOrder) colorFarming else Color.White,
                            fontSize = 14.sp,
                            fontWeight = if(isFarmingOrder) FontWeight.Bold else FontWeight.Normal
                        )
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color.White)
                    }
                    DropdownMenu(expanded = expandedProportion, onDismissRequest = { expandedProportion = false }, modifier = Modifier.background(ColorSurface)) {
                        proportionList.forEach { prop ->
                            val label = if (prop == 100) "최대" else "${prop}%"
                            DropdownMenuItem(
                                text = { Text(label, color = ColorTextPrimary) },
                                onClick = {
                                    isFarmingOrder = false // 💡 일반 비율 선택 시 농사 모드 해제
                                    val basePrice = if (orderType == OrderType.MARKET) currentPrice else priceValue
                                    if (basePrice > 0) {
                                        quantityValue = if (tradeType == TradeType.BUY) {
                                            ((availableCash * (prop / 100.0)) / basePrice).toLong()
                                        } else {
                                            (holdingQuantity * (prop / 100.0)).toLong()
                                        }
                                        updateTotalAmount()
                                        proportionText = label
                                    }
                                    expandedProportion = false
                                }
                            )
                        }

                        // 💡 매수(BUY) 상태일 때만 비율 리스트 끝에 '농사' 메뉴 추가
                        if (tradeType == TradeType.BUY) {
                            DropdownMenuItem(
                                text = { Text("농사", color = colorFarming, fontWeight = FontWeight.Bold) },
                                onClick = {
                                    isFarmingOrder = true // 💡 농사 선택 시 기존 LaunchedEffect가 자동 계산 수행
                                    expandedProportion = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ==================== [총 금액 영역] ====================
            // 💡 농사이거나 시장가(MARKET)일 경우 수동 입력 차단
            val isTotalAmountEnabled = !isFarmingOrder && orderType == OrderType.LIMIT

            OutlinedTextField(
                value = if (totalAmountValue == 0L) "" else numberFormat.format(totalAmountValue),
                onValueChange = { newValue ->
                    val raw = newValue.replace(",", "")
                    if (raw.all { it.isDigit() }) {
                        val newTotal = raw.toLongOrNull() ?: 0L
                        totalAmountValue = newTotal
                        resetProportionText()

                        val basePrice = if (orderType == OrderType.MARKET) currentPrice else priceValue
                        if (basePrice > 0) {
                            quantityValue = newTotal / basePrice
                        }
                    }
                },
                label = { Text(if (orderType == OrderType.MARKET) "총 예상 금액 입력" else "총 주문 금액 입력", color = ColorTextSecondary, fontSize = 12.sp) },
                enabled = isTotalAmountEnabled, // 💡 조건 변수 적용
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth().then(if (isTotalAmountEnabled) Modifier else disabledModifier),
                textStyle = LocalTextStyle.current.copy(
                    textAlign = TextAlign.End,
                    color = if(isFarmingOrder) colorFarming else activeColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = activeColor,
                    unfocusedTextColor = activeColor,
                    // 💡 비활성화(잠금) 상태라도 농사가 아니면(시장가 잠금이면) 액티브 컬러 유지
                    disabledTextColor = if (isFarmingOrder) colorFarming else activeColor,
                    disabledBorderColor = ColorSurface,
                    disabledLabelColor = ColorTextSecondary
                )
            )

            Spacer(modifier = Modifier.height(20.dp))

            // ==================== [버튼 영역] ====================
// ==================== [버튼 영역] ====================
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { resetOrderState() }, modifier = Modifier.weight(0.25f).height(50.dp), shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = ColorTextSecondary)) {
                    Icon(Icons.Default.Refresh, "초기화")
                }
                Button(
                    onClick = {
                        if (tradeType == TradeType.BUY && totalAmountValue > availableCash) {
                            Toast.makeText(context, "주문 가능 금액을 초과하였습니다.", Toast.LENGTH_SHORT).show()
                        } else if (orderType == OrderType.LIMIT && priceValue in 1L..<lowerLimitPrice) {
                            // 💡 포커스가 안 빠진 상태에서 바로 주문 버튼을 눌렀을 때의 2차 교정
                            priceValue = lowerLimitPrice
                            updateTotalAmount()
                            Toast.makeText(context, "주문단가가 하한가 보다 낮아 하한가로 자동 변경됩니다.", Toast.LENGTH_SHORT).show()
                        } else {
                            // 정상 주문 실행
                            onOrderSubmit(tradeType, orderType, priceValue, quantityValue)
                        }
                    },
                    modifier = Modifier.weight(0.75f).height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = activeColor),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(if (tradeType == TradeType.BUY) "매수하기" else "매도하기", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }        }
    }
}