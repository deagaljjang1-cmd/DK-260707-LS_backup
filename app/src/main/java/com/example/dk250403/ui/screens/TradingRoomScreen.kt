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
import androidx.activity.compose.BackHandler

@Composable
fun TradingRoomScreen(
    modifier: Modifier = Modifier,
    nickname: String,
    profileUrl: String,
    isPremium: Boolean,
    onNavigate: (String) -> Unit,
    onLogoutRequest: () -> Unit
) {
    var selectedTab by remember { mutableStateOf("WATCHLIST") }

    BackHandler {
        onNavigate("MAIN") // 뒤로가기 시 메인 화면으로 이동
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
                contentColor = ColorTextPrimary
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