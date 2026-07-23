package com.example.dk250403.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
// 💡 1. 뷰모델 확장을 위한 import 추가
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.dk250403.ColorBg
import com.example.dk250403.ColorStatus
import com.example.dk250403.ColorSurface
import com.example.dk250403.ColorTextPrimary
import com.example.dk250403.ColorTextSecondary
import com.example.dk250403.ui.components.CommonTopBar
import com.example.dk250403.ui.components.TradingBottomSheet

@Composable
fun TradingRoomScreen(
    modifier: Modifier = Modifier,
    nickname: String,
    profileUrl: String,
    isPremium: Boolean,
    onNavigate: (String) -> Unit,
    onLogoutRequest: () -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf("WATCHLIST") }

    // 💡 2. 자산 뷰모델을 트레이딩 룸 레벨로 끌어올림 (메뉴를 이동해도 상태가 파괴되지 않음)
    val assetsViewModel: AssetsViewModel = viewModel()

    // 💡 3. 트레이딩 룸에 최초 진입할 때 딱 한 번만 계좌 초기화(주계좌 세팅) 실행
    LaunchedEffect(Unit) {
        assetsViewModel.initAssets()
    }

    // 바텀 시트 상태 관리
    var showOrderSheet by remember { mutableStateOf(false) }
    var selectedStockName by remember { mutableStateOf("") }
    var selectedStockCode by remember { mutableStateOf("") }
    var selectedStockPrice by remember { mutableStateOf(0L) }

    val onStockClick: (String, String, Long) -> Unit = { name, code, price ->
        selectedStockName = name
        selectedStockCode = code
        selectedStockPrice = price
        showOrderSheet = true
    }

    BackHandler {
        if (showOrderSheet) {
            showOrderSheet = false
        } else {
            onNavigate("MAIN")
        }
    }

    if (showOrderSheet) {
        TradingBottomSheet(
            stockName = selectedStockName,
            stockCode = selectedStockCode,
            currentPrice = selectedStockPrice,
            onDismissRequest = { showOrderSheet = false },
            onOrderSubmit = { isBuy, orderType, price, quantity ->
                val action = if (isBuy) "매수" else "매도"
                Toast.makeText(context, "[${selectedStockName}] $quantity 주 $action 주문 전송 ($orderType)", Toast.LENGTH_SHORT).show()
                showOrderSheet = false
            }
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = ColorBg,
        topBar = {
            CommonTopBar(
                nickname = nickname,
                profileUrl = profileUrl,
                isPremium = isPremium,
                onNavigate = onNavigate,
                onLogoutRequest = onLogoutRequest
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = ColorSurface,
                contentColor = ColorTextPrimary,
                windowInsets = WindowInsets(0, 0, 0, 0),
                modifier = Modifier.height(70.dp)
            ) {
                NavigationBarItem(
                    selected = selectedTab == "WATCHLIST",
                    onClick = { selectedTab = "WATCHLIST" },
                    icon = { Icon(Icons.Default.List, contentDescription = "관심 종목") },
                    label = { Text("관심 종목") },
                    colors = NavigationBarItemDefaults.colors(selectedIconColor = ColorStatus, selectedTextColor = ColorStatus, unselectedIconColor = ColorTextSecondary, unselectedTextColor = ColorTextSecondary, indicatorColor = ColorSurface)
                )
                NavigationBarItem(
                    selected = selectedTab == "CHART",
                    onClick = { selectedTab = "CHART" },
                    icon = { Icon(Icons.Default.Analytics, contentDescription = "종목 차트") },
                    label = { Text("종목 차트") },
                    colors = NavigationBarItemDefaults.colors(selectedIconColor = ColorStatus, selectedTextColor = ColorStatus, unselectedIconColor = ColorTextSecondary, unselectedTextColor = ColorTextSecondary, indicatorColor = ColorSurface)
                )
                NavigationBarItem(
                    selected = selectedTab == "DISCOVERY",
                    onClick = { selectedTab = "DISCOVERY" },
                    icon = { Icon(Icons.Default.Search, contentDescription = "종목 발굴") },
                    label = { Text("종목 발굴") },
                    colors = NavigationBarItemDefaults.colors(selectedIconColor = ColorStatus, selectedTextColor = ColorStatus, unselectedIconColor = ColorTextSecondary, unselectedTextColor = ColorTextSecondary, indicatorColor = ColorSurface)
                )
                NavigationBarItem(
                    selected = selectedTab == "ASSETS",
                    onClick = { selectedTab = "ASSETS" },
                    icon = { Icon(Icons.Default.Wallet, contentDescription = "나의 자산") },
                    label = { Text("나의 자산") },
                    colors = NavigationBarItemDefaults.colors(selectedIconColor = ColorStatus, selectedTextColor = ColorStatus, unselectedIconColor = ColorTextSecondary, unselectedTextColor = ColorTextSecondary, indicatorColor = ColorSurface)
                )
                NavigationBarItem(
                    selected = selectedTab == "SETTINGS",
                    onClick = { selectedTab = "SETTINGS" },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "투자 설정") },
                    label = { Text("투자 설정") },
                    colors = NavigationBarItemDefaults.colors(selectedIconColor = ColorStatus, selectedTextColor = ColorStatus, unselectedIconColor = ColorTextSecondary, unselectedTextColor = ColorTextSecondary, indicatorColor = ColorSurface)
                )
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            when (selectedTab) {
                "WATCHLIST" -> Text("관심 종목 화면 (준비 중)", color = ColorTextPrimary)
                "CHART" -> Text("종목 차트 화면 (준비 중)", color = ColorTextPrimary)
                "DISCOVERY" -> Text("종목 발굴 화면 (준비 중)", color = ColorTextPrimary)
                // 💡 4. 부모가 가진 뷰모델을 자식 화면으로 전달
                "ASSETS" -> MyAssetsScreen(viewModel = assetsViewModel)
                "SETTINGS" -> Text("투자 설정 화면 (준비 중)", color = ColorTextPrimary)
            }
        }
    }
}