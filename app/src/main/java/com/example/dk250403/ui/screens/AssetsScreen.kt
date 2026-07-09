package com.example.dk250403.ui.screens

import androidx.compose.foundation.background
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
import java.text.DecimalFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssetsScreen(
    modifier: Modifier = Modifier,
    viewModel: AssetsViewModel
) {
    val uiState by viewModel.uiState.collectAsState()

    // 💡 정렬 상태 관찰 추가
    val currentSortType by viewModel.sortType.collectAsState()

    val numberFormat = remember { DecimalFormat("#,###") }
    val stockColorUp = remember { Color(0xFFFF3737) }
    val stockColorDown = remember { Color(0xFF37C4FF) }

    Column(modifier = modifier.fillMaxSize().background(ColorBg).padding(16.dp)) {
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

                // 상단: 추정 순자산 카드
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

                // 💡 정렬 필터 추가 영역
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("보유 종목", color = ColorTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            // 오름차순이든 내림차순이든 종목명 기준이면 칩을 활성화(Selected) 상태로 유지
                            selected = currentSortType == SortType.NAME_ASC || currentSortType == SortType.NAME_DESC,
                            onClick = {
                                // 누를 때마다 오름차순 <-> 내림차순 토글
                                if (currentSortType == SortType.NAME_ASC) {
                                    viewModel.setSortType(SortType.NAME_DESC)
                                } else {
                                    viewModel.setSortType(SortType.NAME_ASC)
                                }
                            },
                            label = {
                                // 현재 정렬 상태에 따라 화살표 텍스트 변경
                                val labelText = if (currentSortType == SortType.NAME_DESC) "종목명↓" else "종목명↑"
                                Text(labelText, fontSize = 12.sp)
                            }
                        )
                        FilterChip(
                            selected = currentSortType == SortType.RETURN_DESC || currentSortType == SortType.RETURN_ASC,
                            onClick = {
                                if (currentSortType == SortType.RETURN_DESC) {
                                    viewModel.setSortType(SortType.RETURN_ASC)
                                } else {
                                    viewModel.setSortType(SortType.RETURN_DESC)
                                }
                            },
                            label = {
                                val labelText = if (currentSortType == SortType.RETURN_ASC) "수익률↑" else "수익률↓"
                                Text(labelText, fontSize = 12.sp)
                            }
                        )
                        FilterChip(
                            selected = currentSortType == SortType.PROFIT_DESC || currentSortType == SortType.PROFIT_ASC,
                            onClick = {
                                if (currentSortType == SortType.PROFIT_DESC) {
                                    viewModel.setSortType(SortType.PROFIT_ASC)
                                } else {
                                    viewModel.setSortType(SortType.PROFIT_DESC)
                                }
                            },
                            label = {
                                val labelText = if (currentSortType == SortType.PROFIT_ASC) "수익금↑" else "수익금↓"
                                Text(labelText, fontSize = 12.sp)
                            }
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // 하단: 보유 종목 리스트
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
                                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(stock.itemName, color = ColorTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.height(4.dp))
                                    Text("${stock.quantity}주 | 평단가 ${numberFormat.format(stock.averagePrice)}", color = ColorTextSecondary, fontSize = 12.sp)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    // 💡 수익률 상태 판별
                                    val isProfit = stock.returnRate > 0
                                    val isLoss = stock.returnRate < 0
                                    val rateColor = if (isProfit) stockColorUp else if (isLoss) stockColorDown else ColorTextPrimary
                                    val signPrefix = if (isProfit) "+" else ""

                                    // 💡 1번/2번 항목: 수익금 계산 및 수익률 소수점 2자리 포맷팅
                                    val profitAmount = (stock.currentPrice - stock.averagePrice) * stock.quantity
                                    val formattedRate = String.format("%.2f", stock.returnRate)

                                    Text("${numberFormat.format(stock.currentPrice)} 원", color = ColorTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.height(4.dp))

                                    // 수익률(소수점 2자리)과 수익금 동시 출력
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
}