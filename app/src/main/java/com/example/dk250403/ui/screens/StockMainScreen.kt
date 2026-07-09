package com.example.dk250403.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dk250403.*
import com.example.dk250403.ui.components.CommonTopBar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun StockMainScreen(
    modifier: Modifier = Modifier,
    nickname: String,
    profileUrl: String,
    isPremium: Boolean,
    onNavigate: (String) -> Unit,
    onLogoutRequest: () -> Unit
) {
    val context = LocalContext.current
    var showPremiumGateDialog by remember { mutableStateOf(false) }

    val currentTimeStr = remember {
        val sdf = SimpleDateFormat("yy.MM.dd (E) hh:mm a", Locale.KOREA)
        sdf.format(Date())
    }

    if (showPremiumGateDialog) {
        AlertDialog(
            containerColor = ColorSurface,
            onDismissRequest = { showPremiumGateDialog = false },
            title = { Text("프리미엄 전용 기능", fontWeight = FontWeight.Bold, color = ColorUp) },
            text = { Text("트레이딩 룸은 인증된 구독자만 입장할 수 있습니다.\n고객센터를 통해 권한을 부여받으시겠습니까?", color = ColorTextPrimary) },
            confirmButton = {
                TextButton(onClick = {
                    showPremiumGateDialog = false
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://pf.kakao.com/your-chat-link"))
                    context.startActivity(intent)
                }) { Text("문의하기", color = ColorUp, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showPremiumGateDialog = false }) { Text("취소", color = ColorTextSecondary) }
            }
        )
    }

    Column(modifier = modifier.fillMaxSize().background(ColorBg)) {
        // 공통 상단바 적용
        CommonTopBar(
            nickname = nickname,
            profileUrl = profileUrl,
            isPremium = isPremium,
            onNavigate = onNavigate,
            onLogoutRequest = onLogoutRequest
        )

        Row(
            modifier = Modifier.fillMaxWidth().background(ColorSurfaceVariant).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(ColorStatus))
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "접속 중", fontSize = 12.sp, color = ColorStatus, fontWeight = FontWeight.Bold)
            }
            Text(text = currentTimeStr, fontSize = 12.sp, color = ColorTextPrimary)
            Text(text = "ver. 1.0", fontSize = 12.sp, color = ColorTextSecondary)
        }

        Spacer(modifier = Modifier.height(24.dp))

        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Text(text = "실시간 지수", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = ColorTextPrimary)
            Spacer(modifier = Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // (기존 코스피/코스닥 카드 UI 코드 동일 유지)
                Card(modifier = Modifier.weight(1f).height(80.dp), colors = CardDefaults.cardColors(containerColor = ColorSurface), shape = RoundedCornerShape(8.dp)) {
                    Column(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.Center) {
                        Text(text = "코스피 (KOSPI)", fontSize = 12.sp, color = ColorTextSecondary)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(text = "2,784.26", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = ColorTextPrimary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = "▲ +1.24%", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ColorUp)
                        }
                    }
                }
                Card(modifier = Modifier.weight(1f).height(80.dp), colors = CardDefaults.cardColors(containerColor = ColorSurface), shape = RoundedCornerShape(8.dp)) {
                    Column(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.Center) {
                        Text(text = "코스닥 (KOSDAQ)", fontSize = 12.sp, color = ColorTextSecondary)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(text = "836.71", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = ColorTextPrimary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = "▼ -0.52%", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ColorDown)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            // 중앙 트레이딩 룸 입장 버튼
            Button(
                onClick = {
                    if (isPremium) {
                        onNavigate("TRADING_ROOM")
                    } else {
                        showPremiumGateDialog = true
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ColorStatus, contentColor = ColorBg)
            ) {
                Text("📈 트레이딩 룸 입장", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}