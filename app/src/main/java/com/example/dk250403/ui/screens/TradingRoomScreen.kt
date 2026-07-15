package com.example.dk250403.ui.screens

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
import com.example.dk250403.ColorBg
import com.example.dk250403.ColorSurface
import com.example.dk250403.ColorStatus
import com.example.dk250403.ColorTextPrimary
import com.example.dk250403.ColorTextSecondary
import com.example.dk250403.ui.components.CommonTopBar
import androidx.activity.compose.BackHandler // 💡 이 import 구문이 필요합니다.
import com.example.dk250403.ui.components.TradingBottomSheet
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

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

    // 💡 바텀 시트(매수/매도 팝업) 상태 관리
    var showOrderSheet by remember { mutableStateOf(false) }
    var selectedStockName by remember { mutableStateOf("") }
    var selectedStockCode by remember { mutableStateOf("") }
    var selectedStockPrice by remember { mutableStateOf(0L) }

    // 종목 클릭 시 팝업을 띄우는 공통 함수
    val onStockClick: (String, String, Long) -> Unit = { name, code, price ->
        selectedStockName = name
        selectedStockCode = code
        selectedStockPrice = price
        showOrderSheet = true
    }

    // 안드로이드 시스템 뒤로가기 버튼 이벤트 가로채기
    androidx.activity.compose.BackHandler {
        if (showOrderSheet) {
            showOrderSheet = false // 시트가 열려있으면 시트만 닫음
        } else {
            onNavigate("MAIN")
        }
    }

    // 💡 바텀 시트 호출 로직
    if (showOrderSheet) {
        TradingBottomSheet(
            stockName = selectedStockName,
            stockCode = selectedStockCode,
            currentPrice = selectedStockPrice,
            onDismissRequest = { showOrderSheet = false },
            onOrderSubmit = { isBuy, orderType, price, quantity ->
                // TODO: LS증권 실제 매수/매도 주문 API 호출 연동 필요
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
                // 💡 추가 1: NavigationBar 자체의 하단 시스템 여백 계산을 무력화하여 이중 여백 방지
                windowInsets = WindowInsets(0, 0, 0, 0),
                // 💡 추가 2: 탭바 전체 높이를 슬림하게 고정 (너무 얇다면 70.dp 등으로 조절 가능)
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
            // 선택된 탭에 따라 화면 전환
            when (selectedTab) {
                "WATCHLIST" -> Text("관심 종목 화면 (준비 중)", color = ColorTextPrimary)
                "CHART" -> Text("종목 차트 화면 (준비 중)", color = ColorTextPrimary)
                "DISCOVERY" -> Text("종목 발굴 화면 (준비 중)", color = ColorTextPrimary)
                "ASSETS" -> MyAssetsScreen() // 💡 AssetsScreen -> MyAssetsScreen 으로 변경
                "SETTINGS" -> Text("투자 설정 화면 (준비 중)", color = ColorTextPrimary)
            }
        }
    }
}