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
    onOrderSubmit: (TradeType, OrderType, Long, Long) -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val numberFormat = remember { DecimalFormat("#,###") }

    val colorBuy = Color(0xFFFF3737)
    val colorSell = Color(0xFF37C4FF)
    val colorFarming = Color(0xFF4CAF50) // 💡 농사 주문 테마색 (초록)

    // 💡 1. 미보유/보유 상태에 따라 초기 탭과 수량, 금액, 텍스트를 자동 세팅
    var tradeType by remember { mutableStateOf(if (holdingQuantity > 0) TradeType.SELL else TradeType.BUY) }
    var orderType by remember { mutableStateOf(OrderType.LIMIT) }

    var priceValue by remember { mutableLongStateOf(currentPrice) }
    var quantityValue by remember { mutableLongStateOf(if (holdingQuantity > 0) holdingQuantity else 0L) }
    var totalAmountValue by remember { mutableLongStateOf(if (holdingQuantity > 0) currentPrice * holdingQuantity else 0L) }
    var proportionText by remember { mutableStateOf(if (holdingQuantity > 0) "최대" else "가능") }

    // 💡 농사 매매 스위치 (상태)
    var isFarmingOrder by remember { mutableStateOf(false) }

    var isUserInteracted by remember { mutableStateOf(false) }

    LaunchedEffect(currentPrice) {
        if (!isUserInteracted && orderType == OrderType.LIMIT) {
            priceValue = currentPrice
            val basePrice = if (orderType == OrderType.MARKET) currentPrice else priceValue
            // 농사 주문이 꺼져 있을 때만 총 금액을 갱신 (켜져있으면 농사 로직이 덮어씌움)
            if (!isFarmingOrder) {
                totalAmountValue = basePrice * quantityValue
            }
        }
    }

    // 💡 농사 주문 자동 계산 로직 (농사 상태가 켜지거나, 단가가 변경될 때마다 자동 실행)
    LaunchedEffect(isFarmingOrder, priceValue, orderType) {
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
        isUserInteracted = false
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
                        .fillMaxWidth() // 1. 먼저 가로 전체 공간을 확보한 뒤
                        .wrapContentWidth(Alignment.CenterHorizontally) // 2. 내용물 크기만큼 뷰를 좁히고 중앙 정렬!
                        .background(Color(0xFF39FF81), RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 1.dp), // 💡 좌우 여백을 주어 안정적인 캡슐 모양을 만듭니다. (사용자님의 얇은 세로 두께감 유지)
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "보유 중 : ${numberFormat.format(holdingQuantity)}주",
                        color = ColorBg, // 주문창 바탕 컬러와 동일 (글씨를 파낸 듯한 효과)
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // [1] 헤더 (원래의 깔끔한 상태로 복구)
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
            // ... (기존 코드 유지)

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
                        // 💡 매수 탭 전환 시: 주문 상태 안전하게 초기화
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

                        // 💡 매도 탭 전환 시: 보유 수량 전량 세팅 (보유 종목일 경우)
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

            // [3] 농사 주문 토글 & 주문 가능
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 32.dp), // 💡 버튼이 사라져도 최소 32.dp의 높이를 강제로 유지
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 💡 매수(BUY)일 때만 농사 매매 태그(필터 칩) 표시
                if (tradeType == TradeType.BUY) {
                    FilterChip(
                        selected = isFarmingOrder,
                        onClick = { isFarmingOrder = !isFarmingOrder },
                        label = { Text("🌱 농사 주문", fontSize = 13.sp, fontWeight = FontWeight.Bold) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = colorFarming.copy(alpha = 0.2f),
                            selectedLabelColor = colorFarming,
                            labelColor = ColorTextSecondary
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isFarmingOrder,
                            borderColor = ColorSurface,
                            selectedBorderColor = colorFarming
                        ),
                        modifier = Modifier.height(32.dp)
                    )
                }

                // 💡 빈 공간을 가변으로 차지하여 '주문가능' 텍스트를 항상 우측 끝으로 밀어냄
                Spacer(modifier = Modifier.weight(1f))

                // 우측: 주문 가능 금액
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
                                priceValue = typedPrice.coerceIn(lowerLimitPrice, upperLimitPrice)
                                isUserInteracted = true
                                updateTotalAmount()
                            }
                        }
                    },
                    label = { Text("주문 단가", color = ColorTextSecondary, fontSize = 12.sp) },
                    enabled = isLimit,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(0.7f).then(if (isLimit) Modifier else disabledModifier),
                    textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.End, fontSize = 15.sp),
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = ColorTextPrimary, unfocusedTextColor = ColorTextPrimary, disabledTextColor = ColorTextSecondary),
                    trailingIcon = {
                        EmbeddedStepper(
                            enabled = isLimit,
                            onUpClick = {
                                if (priceValue < upperLimitPrice) {
                                    priceValue = snapToValidTick(priceValue + getTickSize(priceValue)).coerceAtMost(upperLimitPrice)
                                    isUserInteracted = true
                                    updateTotalAmount()
                                }
                            },
                            onDownClick = {
                                if (priceValue > lowerLimitPrice) {
                                    priceValue = snapToValidTick(priceValue - getTickSize(priceValue)).coerceAtLeast(lowerLimitPrice)
                                    isUserInteracted = true
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
                            isUserInteracted = true
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
                                            isUserInteracted = true
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
                                            isUserInteracted = true
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
                        // 💡 수정: 직접 Color.Gray를 주입하여 타입 충돌 해결
                        .background(if (isQuantityEnabled) Color.Transparent else Color.Gray.copy(alpha = 0.15f))
                        .then(if (isQuantityEnabled) Modifier.clickable { expandedProportion = true } else Modifier),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(proportionText, color = if (isQuantityEnabled) Color.White else colorFarming, fontSize = 14.sp, fontWeight = if(!isQuantityEnabled) FontWeight.Bold else FontWeight.Normal)
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = if (isQuantityEnabled) Color.White else ColorTextSecondary)
                    }
                    DropdownMenu(expanded = expandedProportion, onDismissRequest = { expandedProportion = false }, modifier = Modifier.background(ColorSurface)) {
                        proportionList.forEach { prop ->
                            val label = if (prop == 100) "최대" else "${prop}%"
                            DropdownMenuItem(
                                text = { Text(label, color = ColorTextPrimary) },
                                onClick = {
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
                    }
                }            }

            Spacer(modifier = Modifier.height(16.dp))

            // ==================== [총 금액 영역] ====================
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
                enabled = isQuantityEnabled, // 💡 농사 매매 시 총 금액 직접 입력 차단
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth().then(if (isQuantityEnabled) Modifier else disabledModifier),
                textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.End, color = if(isQuantityEnabled) activeColor else colorFarming, fontWeight = FontWeight.Bold, fontSize = 16.sp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = activeColor,
                    unfocusedTextColor = activeColor,
                    disabledTextColor = colorFarming, // 비활성화 시 금액을 초록색으로 표시
                    disabledBorderColor = ColorSurface,
                    disabledLabelColor = ColorTextSecondary
                )
            )

            Spacer(modifier = Modifier.height(20.dp))

            // ==================== [버튼 영역] ====================
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { resetOrderState() }, modifier = Modifier.weight(0.25f).height(50.dp), shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = ColorTextSecondary)) {
                    Icon(Icons.Default.Refresh, "초기화")
                }
                Button(
                    onClick = {
                        if (tradeType == TradeType.BUY && totalAmountValue > availableCash) {
                            Toast.makeText(context, "주문 가능 금액을 초과하였습니다.", Toast.LENGTH_SHORT).show()
                        } else {
                            // 💡 향후 주문 전송 시 isFarmingOrder 상태를 전달할 수 있도록 준비 완료
                            onOrderSubmit(tradeType, orderType, priceValue, quantityValue)
                        }
                    },
                    modifier = Modifier.weight(0.75f).height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = activeColor),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(if (tradeType == TradeType.BUY) "매수하기" else "매도하기", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}