package com.example.dk250403.ui.components

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.example.dk250403.*
import com.example.dk250403.R
import com.google.firebase.auth.FirebaseAuth

@Composable
fun CommonTopBar(
    nickname: String,
    profileUrl: String,
    isPremium: Boolean,
    onNavigate: (String) -> Unit,
    onLogoutRequest: () -> Unit
) {
    var showProfileMenu by remember { mutableStateOf(false) }
    var isMenuImageReady by remember { mutableStateOf(false) }
    var showPremiumGateDialog by remember { mutableStateOf(false) }

    val auth = FirebaseAuth.getInstance()
    val currentUserUID = auth.currentUser?.uid ?: ""
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    if (showPremiumGateDialog) {
        AlertDialog(
            containerColor = ColorSurface,
            onDismissRequest = { showPremiumGateDialog = false },
            title = { Text("프리미엄 전용 기능", fontWeight = FontWeight.Bold, color = ColorUp) },
            text = { Text("해당 기능은 인증된 구독자만 이용할 수 있습니다.\n고객센터를 통해 권한을 부여받으시겠습니까?", color = ColorTextPrimary) },
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

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(id = R.drawable.logo_dtm1),
                contentDescription = "로고",
                modifier = Modifier.size(28.dp),
                contentScale = ContentScale.Fit
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "단테트레이딩M+", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = ColorTextPrimary)
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { /* 알림 목록 준비 중 */ }) { Icon(imageVector = Icons.Default.Notifications, contentDescription = "알림", tint = ColorTextSecondary) }
            IconButton(onClick = onLogoutRequest) { Icon(imageVector = Icons.Default.ExitToApp, contentDescription = "로그아웃", tint = ColorTextSecondary) }
            Box {
                IconButton(onClick = { showProfileMenu = true }) {
                    if (profileUrl.isNotEmpty()) {
                        Image(painter = rememberAsyncImagePainter(ImageRequest.Builder(context).data(profileUrl).crossfade(true).build()), contentDescription = "상단프로필", modifier = Modifier.size(32.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                    } else { Icon(imageVector = Icons.Default.AccountCircle, contentDescription = "프로필", modifier = Modifier.size(32.dp), tint = ColorTextPrimary) }
                }
                DropdownMenu(expanded = showProfileMenu, onDismissRequest = { showProfileMenu = false }, modifier = Modifier.width(260.dp).background(ColorSurface)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.alpha(if (isMenuImageReady || profileUrl.isEmpty()) 1f else 0f)) {
                            if (profileUrl.isNotEmpty()) {
                                Image(painter = rememberAsyncImagePainter(model = ImageRequest.Builder(context).data(profileUrl).crossfade(true).build(), onSuccess = { isMenuImageReady = true }), contentDescription = "메뉴프로필", modifier = Modifier.size(40.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                            } else { Icon(Icons.Default.AccountCircle, contentDescription = "프로필", modifier = Modifier.size(40.dp), tint = ColorTextSecondary) }
                            Spacer(Modifier.width(12.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = nickname, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = ColorTextPrimary)
                                if (isPremium) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Image(painter = painterResource(id = R.drawable.ic_verified), contentDescription = "인증 완료", modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth().background(ColorSurfaceVariant, RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "UID: $currentUserUID", fontSize = 12.sp, color = ColorTextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Spacer(modifier = Modifier.width(4.dp))
                            IconButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(currentUserUID))
                                    Toast.makeText(context, "UID 복사됨", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(24.dp)
                            ) { Icon(Icons.Default.ContentCopy, contentDescription = "복사", modifier = Modifier.size(16.dp), tint = ColorTextSecondary) }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = ColorSurfaceVariant)
                        Spacer(modifier = Modifier.height(8.dp))

                        TextButton(onClick = { showProfileMenu = false; onNavigate("SETTINGS") }, modifier = Modifier.fillMaxWidth()) {
                            Text("⚙️ 계정 설정 및 보안", fontSize = 14.sp, color = ColorTextPrimary, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
    }
}