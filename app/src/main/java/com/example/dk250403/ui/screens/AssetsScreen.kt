package com.example.dk250403.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.dk250403.*
import java.text.DecimalFormat

@Composable
fun AssetsScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    viewModel: AssetsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val accounts by viewModel.registeredAccounts.collectAsState()
    val selectedAccount by viewModel.selectedAccount.collectAsState()
    val numberFormat = DecimalFormat("#,###")

    // 💡 국내주식 전용 컬러 지정
    val stockColorUp = Color(0xFFFF3737)   // 상승
    val stockColorDown = Color(0xFF37C4FF) // 하락

    // 💡 뷰페이저 상태 관리 (계좌 리스트 개수 기반)
    val pagerState = rememberPagerState(pageCount = { accounts.size.coerceAtLeast(1) })

    LaunchedEffect(Unit) {
        viewModel.initAssets()
    }

    // 💡 사용자가 화면을 좌우로 드래그(스와이프)했을 때 탭과 연동하여 API 호출
    LaunchedEffect(pagerState.currentPage) {
        if (accounts.isNotEmpty()) {
            val currentTabAccount = accounts[pagerState.currentPage]
            if (selectedAccount != currentTabAccount && selectedAccount.isNotEmpty()) {
                viewModel.requestBalanceForAccount(currentTabAccount)
            }
        }
    }

    // 💡 사용자가 칩(버튼)을 클릭하여 계좌가 변경되었을 때 화면(페이저) 스크롤 이동
    LaunchedEffect(selectedAccount) {
        if (accounts.isNotEmpty() && selectedAccount.isNotEmpty()) {
            val index = accounts.indexOf(selectedAccount)
            if (index != -1 && pagerState.currentPage != index) {
                pagerState.animateScrollToPage(index)
            }
        }
    }

    androidx.activity.compose.BackHandler { onBack() }

    Column(modifier = modifier.fillMaxSize().background(ColorBg).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.offset(x = (-12).dp)) {
                Icon(Icons.Default.ArrowBack, contentDescription = "뒤로가기", tint = ColorTextPrimary)
            }
            Text("💰 나의 자산", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = ColorTextPrimary)
        }

        Spacer(Modifier.height(16.dp))

        if (accounts.size > 1) {
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(accounts) { accNumber ->
                    val isSelected = selectedAccount == accNumber

                    FilterChip(
                        selected = isSelected,
                        onClick = { if (!isSelected) viewModel.requestBalanceForAccount(accNumber) },
                        label = { Text(accNumber, fontWeight = FontWeight.Bold) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ColorStatus,
                            selectedLabelColor = ColorBg,
                            containerColor = ColorSurface,
                            labelColor = ColorTextSecondary
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true, selected = isSelected,
                            borderColor = Color.Transparent, selectedBorderColor = Color.Transparent
                        )
                    )
                }
            }
        }

        // 💡 페이저를 통해 화면 구조를 좌우 드래그 가능하게 매핑
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
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
                                color = ColorUp,
                                fontSize = 15.sp,
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                is AssetsViewModel.UiState.BalanceLoaded -> {
                    val data = (uiState as AssetsViewModel.UiState.BalanceLoaded).balance

                    Column(modifier = Modifier.fillMaxSize()) {
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

                                // 💡 실현 손익 색상 및 + 기호 적용
                                val totalPnl = data.summary?.totalProfitAndLoss ?: 0L
                                val pnlColor = if (totalPnl > 0) stockColorUp else if (totalPnl < 0) stockColorDown else ColorTextPrimary
                                val pnlPrefix = if (totalPnl > 0) "+" else ""
                                Text("실현 손익: $pnlPrefix${numberFormat.format(totalPnl)} 원", color = pnlColor, fontSize = 14.sp)
                            }
                        }

                        Spacer(Modifier.height(20.dp))
                        Text("보유 종목", color = ColorTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
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
                                        modifier = Modifier.fillMaxWidth().background(ColorSurface, RoundedCornerShape(8.dp)).padding(16.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(stock.itemName, color = ColorTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                            Spacer(Modifier.height(4.dp))
                                            Text("${stock.quantity}주 | 평단가 ${numberFormat.format(stock.averagePrice)}", color = ColorTextSecondary, fontSize = 12.sp)
                                        }
                                        Column(horizontalAlignment = Alignment.End) {
                                            // 💡 종목 수익률 색상 및 + 기호 적용
                                            val isProfit = stock.returnRate > 0
                                            val isLoss = stock.returnRate < 0
                                            val rateColor = if (isProfit) stockColorUp else if (isLoss) stockColorDown else ColorTextPrimary
                                            val ratePrefix = if (isProfit) "+" else ""

                                            Text("${numberFormat.format(stock.currentPrice)} 원", color = ColorTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                            Spacer(Modifier.height(4.dp))
                                            Text("$ratePrefix${stock.returnRate}%", color = rateColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                else -> {}
            }
        }
    }
}