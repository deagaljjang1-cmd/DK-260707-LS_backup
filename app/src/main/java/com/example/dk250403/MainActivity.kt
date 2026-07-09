package com.example.dk250403

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import com.example.dk250403.ui.screens.AssetsScreen
import com.example.dk250403.ui.screens.LoginScreen
import com.example.dk250403.ui.screens.SettingsScreen
import com.example.dk250403.ui.screens.StockMainScreen
import com.example.dk250403.ui.screens.TradingRoomScreen
import com.example.dk250403.ui.theme.DK250403Theme
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions

// 지정된 다크 테마 색상 팔레트
val ColorBg = Color(0xFF0E1319)
val ColorSurface = Color(0xFF202020)
val ColorSurfaceVariant = Color(0xFF333333)
val ColorTextPrimary = Color(0xFFFFFFFF)
val ColorTextSecondary = Color(0xFF999999)
val ColorUp = Color(0xFFFF3737)
val ColorDown = Color(0xFF37C4FF)
val ColorStatus = Color(0xFF39FF81)

// 💡 정규식 및 금지어를 전역 상수로 분리하여 반복 컴파일 방지 (최적화)
val NICKNAME_REGEX = Regex("^[가-힣a-zA-Z0-9_\\[\\]-]+$")
val BANNED_WORDS = listOf("관리자", "운영자", "admin", "system", "root", "운영진")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 상태창과 하단 네비게이션 바 아이콘을 밝은 색(흰색)으로 강제 고정
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )

        setContent {
            DK250403Theme {
                Scaffold(modifier = Modifier.fillMaxSize(), containerColor = ColorBg) { innerPadding ->
                    AppNavigator(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

// ------------------- 라우팅 (네비게이터) -------------------
@Composable
fun AppNavigator(modifier: Modifier = Modifier) {
    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()
    val context = LocalContext.current

    var currentUser by remember { mutableStateOf(auth.currentUser) }
    var globalNickname by remember { mutableStateOf("투자자") }
    var globalProfileUrl by remember { mutableStateOf("") }
    var globalIsPremium by remember { mutableStateOf(false) }

    var isCheckingUser by remember { mutableStateOf(false) }
    var startScreen by remember { mutableStateOf("MAIN") }
    var showForceLogoutDialog by remember { mutableStateOf(false) }

    // 기기 고유 ID 추출
    val currentDeviceId = remember { Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) }

    val performLogout: () -> Unit = {
        auth.signOut()
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).build()
        val googleSignInClient = GoogleSignIn.getClient(context, gso)
        googleSignInClient.signOut().addOnCompleteListener {
            currentUser = null
            globalProfileUrl = ""
            globalNickname = "투자자"
            globalIsPremium = false
        }
    }

    DisposableEffect(currentUser?.uid) {
        var listener: ListenerRegistration? = null

        if (currentUser != null) {
            isCheckingUser = true
            val uid = currentUser!!.uid
            val userRef = db.collection("users").document(uid)

            // 1. 단발성 조회로 신규 가입 여부 확인
            userRef.get().addOnSuccessListener { document ->
                if (!document.exists()) {
                    // --- [신규 회원 가입] 구글 프로필 받아오기 ---
                    val rawName = currentUser!!.displayName ?: "투자자"
                    val cleanedName = rawName.replace(" ", "")

                    var isValid = true
                    // 💡 전역 상수화된 정규식 및 금지어 사용
                    if (!NICKNAME_REGEX.matches(cleanedName)) isValid = false
                    if (cleanedName.length !in 2..12) isValid = false
                    if (BANNED_WORDS.any { cleanedName.contains(it, ignoreCase = true) }) isValid = false

                    val finalName = if (isValid) cleanedName else "투자자_${uid.take(4)}"
                    val targetScreen = if (isValid) "MAIN" else "SETTINGS"
                    val initialUrl = currentUser!!.photoUrl?.toString() ?: ""

                    val initialData = hashMapOf(
                        "nickname" to finalName,
                        "profileImageUrl" to initialUrl,
                        "isPremium" to false,
                        "currentDeviceId" to currentDeviceId
                    )

                    userRef.set(initialData).addOnSuccessListener {
                        globalNickname = if (isValid) finalName else cleanedName
                        globalProfileUrl = initialUrl
                        globalIsPremium = false
                        startScreen = targetScreen
                        isCheckingUser = false

                        if (!isValid) {
                            Toast.makeText(context, "사용할 수 없는 구글 닉네임입니다. 프로필을 설정해 주세요.", Toast.LENGTH_LONG).show()
                        }

                        // 가입 완료 후 실시간 감시 시작
                        listener = userRef.addSnapshotListener { snapshot, e ->
                            if (e != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener
                            globalNickname = snapshot.getString("nickname") ?: "투자자"
                            globalProfileUrl = snapshot.getString("profileImageUrl") ?: ""
                            globalIsPremium = snapshot.getBoolean("isPremium") ?: false
                            val dbDeviceId = snapshot.getString("currentDeviceId") ?: ""
                            if (dbDeviceId.isNotEmpty() && dbDeviceId != currentDeviceId) showForceLogoutDialog = true
                        }
                    }
                } else {
                    // --- [기존 회원 로그인] ---
                    userRef.set(hashMapOf("currentDeviceId" to currentDeviceId), SetOptions.merge())
                        .addOnSuccessListener {
                            startScreen = "MAIN"
                            isCheckingUser = false

                            // 기기 등록 완료 후 실시간 감시 시작
                            listener = userRef.addSnapshotListener { snapshot, e ->
                                if (e != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener
                                globalNickname = snapshot.getString("nickname") ?: "투자자"
                                globalProfileUrl = snapshot.getString("profileImageUrl") ?: ""
                                globalIsPremium = snapshot.getBoolean("isPremium") ?: false
                                val dbDeviceId = snapshot.getString("currentDeviceId") ?: ""
                                if (dbDeviceId.isNotEmpty() && dbDeviceId != currentDeviceId) showForceLogoutDialog = true
                            }
                        }
                }
            }.addOnFailureListener {
                isCheckingUser = false
                Toast.makeText(context, "회원 정보를 불러오는데 실패했습니다.", Toast.LENGTH_SHORT).show()
            }
        }

        onDispose {
            listener?.remove()
        }
    }

    if (showForceLogoutDialog) {
        AlertDialog(
            containerColor = ColorSurface,
            onDismissRequest = { },
            title = { Text("⚠️ 중복 로그인 감지", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = ColorUp) },
            text = { Text("다른 기기에서 동일한 계정으로 로그인이 감지되었습니다. 보안을 위해 현재 기기에서는 로그아웃됩니다.", color = ColorTextPrimary) },
            confirmButton = {
                TextButton(onClick = {
                    showForceLogoutDialog = false
                    performLogout()
                }) { Text("확인", color = ColorUp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) }
            }
        )
    }

    if (currentUser == null) {
        LoginScreen(modifier = modifier, onLoginSuccess = { user -> currentUser = user })
    } else if (isCheckingUser) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = ColorTextPrimary)
        }
    } else {
        MainScreen(
            modifier = modifier,
            currentNickname = globalNickname,
            currentProfileUrl = globalProfileUrl,
            isPremium = globalIsPremium,
            initialScreen = startScreen,
            onProfileSaved = { newName, newUrl ->
                globalNickname = newName
                if (newUrl != null) globalProfileUrl = newUrl
            },
            onCancelSettings = {
                db.collection("users").document(currentUser!!.uid).get().addOnSuccessListener { document ->
                    globalNickname = document.getString("nickname") ?: "투자자"
                }
            },
            onLogout = performLogout
        )
    }
}

// ------------------- 메인 라우터 -------------------
@Composable
fun MainScreen(
    modifier: Modifier = Modifier, currentNickname: String, currentProfileUrl: String, isPremium: Boolean,
    initialScreen: String, onProfileSaved: (String, String?) -> Unit, onCancelSettings: () -> Unit, onLogout: () -> Unit
) {
    var currentScreen by remember { mutableStateOf(initialScreen) }
    var showLogoutDialog by remember { mutableStateOf(false) }

    LaunchedEffect(initialScreen) { currentScreen = initialScreen }

    if (showLogoutDialog) {
        AlertDialog(
            containerColor = ColorSurface,
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("로그아웃", fontWeight = FontWeight.Bold, color = ColorTextPrimary) },
            text = { Text("정말 로그아웃 하시겠습니까?", color = ColorTextPrimary) },
            confirmButton = { TextButton(onClick = { showLogoutDialog = false; onLogout() }) { Text("로그아웃", color = ColorUp, fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { showLogoutDialog = false }) { Text("취소", color = ColorTextSecondary) } }
        )
    }

    when (currentScreen) {
        "MAIN" -> StockMainScreen(
            modifier = modifier, nickname = currentNickname, profileUrl = currentProfileUrl, isPremium = isPremium,
            onNavigate = { currentScreen = it }, onLogoutRequest = { showLogoutDialog = true }
        )
        "TRADING_ROOM" -> TradingRoomScreen(
            modifier = modifier, nickname = currentNickname, profileUrl = currentProfileUrl, isPremium = isPremium,
            onNavigate = { currentScreen = it }, onLogoutRequest = { showLogoutDialog = true }
        )
        "SETTINGS" -> SettingsScreen(
            modifier = modifier, initialNickname = currentNickname, initialProfileUrl = currentProfileUrl, isPremium = isPremium,
            onProfileSaved = { name, url -> onProfileSaved(name, url); currentScreen = "MAIN" },
            onBack = { onCancelSettings(); currentScreen = "MAIN" }, onLogout = onLogout
        )
    }
}