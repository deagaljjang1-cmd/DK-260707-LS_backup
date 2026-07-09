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
                1 -> UnexecutedOrdersScreen() // 임시 미체결 뷰
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

@Composable
fun UnexecutedOrdersScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("미체결 내역이 없습니다.", color = ColorTextSecondary)
    }
}

@Composable
fun TransactionHistoryScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("거래 내역이 없습니다.", color = ColorTextSecondary)
    }
}