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

@Composable
fun AssetsScreen(
    modifier: Modifier = Modifier,
    viewModel: AssetsViewModel
) {
    val uiState by viewModel.uiState.collectAsState()

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
            else -> {}
        }
    }
}