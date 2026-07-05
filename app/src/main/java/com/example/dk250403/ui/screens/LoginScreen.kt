package com.example.dk250403.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dk250403.*
import com.example.dk250403.R
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider

// ====================================================================
// 💡 추후 약관 내용이 정리되면 아래 변수의 큰따옴표 3개(""") 안쪽 텍스트만 수정하세요.
// 스크롤은 자동으로 생성됩니다.
// ====================================================================
const val TERMS_OF_SERVICE_TEXT = """
제1조 (목적)
이 약관은 단테트레이딩M+ (이하 "회사")가 제공하는 제반 서비스의 이용과 관련하여 회사와 회원과의 권리, 의무 및 책임사항, 기타 필요한 사항을 규정함을 목적으로 합니다.

제2조 (용어의 정의)
1. "서비스"라 함은 구현되는 단말기(PC, TV, 휴대형단말기 등의 각종 유무선 장치를 포함)와 상관없이 "회원"이 이용할 수 있는 단테트레이딩M+ 관련 제반 서비스를 의미합니다.
2. "회원"이라 함은 회사의 "서비스"에 접속하여 이 약관에 따라 "회사"와 이용계약을 체결하고 "회사"가 제공하는 "서비스"를 이용하는 고객을 말합니다.

(추후 여기에 전체 이용약관 내용을 붙여넣으세요.)
"""

const val PRIVACY_POLICY_TEXT = """
1. 개인정보의 처리 목적
단테트레이딩M+는 다음의 목적을 위하여 개인정보를 처리하고 있으며, 다음의 목적 이외의 용도로는 이용하지 않습니다.
- 고객 가입의사 확인, 고객에 대한 서비스 제공에 따른 본인 식별.인증, 회원자격 유지.관리, 물품 또는 서비스 공급에 따른 금액 결제 등

2. 개인정보의 처리 및 보유 기간
① 단테트레이딩M+는 정보주체로부터 개인정보를 수집할 때 동의 받은 개인정보 보유.이용기간 또는 법령에 따른 개인정보 보유.이용기간 내에서 개인정보를 처리.보유합니다.

(추후 여기에 전체 개인정보 처리방침 내용을 붙여넣으세요.)
"""
// ====================================================================

@Composable
fun LoginScreen(modifier: Modifier = Modifier, onLoginSuccess: (com.google.firebase.auth.FirebaseUser) -> Unit) {
    val context = LocalContext.current
    val auth = FirebaseAuth.getInstance()
    var isLoggingIn by remember { mutableStateOf(false) }
    var pendingAuthResult by remember { mutableStateOf<AuthResult?>(null) }

    // --- 약관 동의 체크박스 상태 ---
    var agreeAll by remember { mutableStateOf(false) }
    var agreeTerms by remember { mutableStateOf(false) }
    var agreePrivacy by remember { mutableStateOf(false) }

    // --- 약관 내용 팝업창 노출 상태 ---
    var showTermsDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }

    LaunchedEffect(pendingAuthResult) {
        if (pendingAuthResult == null) {
            agreeAll = false
            agreeTerms = false
            agreePrivacy = false
        }
    }

    val webClientId = remember {
        val id = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        if (id != 0) context.getString(id) else ""
    }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            val credential = GoogleAuthProvider.getCredential(account.idToken, null)

            auth.signInWithCredential(credential)
                .addOnSuccessListener { authResult ->
                    if (authResult.additionalUserInfo?.isNewUser == true) {
                        pendingAuthResult = authResult
                    } else {
                        isLoggingIn = false
                        onLoginSuccess(authResult.user!!)
                    }
                }
                .addOnFailureListener {
                    isLoggingIn = false
                    Toast.makeText(context, "❌ 파이어베이스 연동 실패", Toast.LENGTH_SHORT).show()
                }
        } catch (e: ApiException) {
            isLoggingIn = false
            Toast.makeText(context, "❌ 구글 인증 취소 또는 오류", Toast.LENGTH_SHORT).show()
        }
    }

    // --- 1. 이용약관 상세 내용 팝업 ---
    if (showTermsDialog) {
        AlertDialog(
            containerColor = ColorSurface,
            onDismissRequest = { showTermsDialog = false },
            title = { Text("이용약관", fontWeight = FontWeight.Bold, color = ColorTextPrimary) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(text = TERMS_OF_SERVICE_TEXT.trimIndent(), color = ColorTextSecondary, fontSize = 14.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = { showTermsDialog = false }) { Text("확인", color = ColorUp, fontWeight = FontWeight.Bold) }
            }
        )
    }

    // --- 2. 개인정보 처리방침 상세 내용 팝업 ---
    if (showPrivacyDialog) {
        AlertDialog(
            containerColor = ColorSurface,
            onDismissRequest = { showPrivacyDialog = false },
            title = { Text("개인정보 처리방침", fontWeight = FontWeight.Bold, color = ColorTextPrimary) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(text = PRIVACY_POLICY_TEXT.trimIndent(), color = ColorTextSecondary, fontSize = 14.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = { showPrivacyDialog = false }) { Text("확인", color = ColorUp, fontWeight = FontWeight.Bold) }
            }
        )
    }

    // --- 신규 회원 가입 약관 동의 팝업창 ---
    if (pendingAuthResult != null) {
        AlertDialog(
            containerColor = ColorSurface,
            onDismissRequest = { },
            title = { Text("서비스 이용 동의", fontWeight = FontWeight.Bold, color = ColorTextPrimary) },
            text = {
                Column {
                    Text("단테트레이딩M+ 서비스 가입을 위해\n아래 필수 약관에 동의해 주세요.", fontSize = 14.sp, color = ColorTextPrimary)
                    Spacer(modifier = Modifier.height(24.dp))

                    // 전체 동의
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable {
                            val newValue = !agreeAll
                            agreeAll = newValue
                            agreeTerms = newValue
                            agreePrivacy = newValue
                        }.padding(vertical = 4.dp)
                    ) {
                        Checkbox(
                            checked = agreeAll,
                            onCheckedChange = null,
                            colors = CheckboxDefaults.colors(checkedColor = ColorUp, uncheckedColor = ColorTextSecondary)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("전체 동의하기", color = ColorTextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = ColorSurfaceVariant)

                    // 필수 약관 1 (체크박스 + 보기 버튼)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween, // 좌우로 배치
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable {
                                agreeTerms = !agreeTerms
                                agreeAll = agreeTerms && agreePrivacy
                            }
                        ) {
                            Checkbox(
                                checked = agreeTerms,
                                onCheckedChange = null,
                                colors = CheckboxDefaults.colors(checkedColor = ColorUp, uncheckedColor = ColorTextSecondary)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("(필수) 이용약관 동의", color = ColorTextSecondary, fontSize = 14.sp)
                        }
                        Text(
                            text = "[보기]",
                            color = ColorTextPrimary,
                            fontSize = 13.sp,
                            modifier = Modifier.clickable { showTermsDialog = true }.padding(4.dp)
                        )
                    }

                    // 필수 약관 2 (체크박스 + 보기 버튼)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween, // 좌우로 배치
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable {
                                agreePrivacy = !agreePrivacy
                                agreeAll = agreeTerms && agreePrivacy
                            }
                        ) {
                            Checkbox(
                                checked = agreePrivacy,
                                onCheckedChange = null,
                                colors = CheckboxDefaults.colors(checkedColor = ColorUp, uncheckedColor = ColorTextSecondary)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("(필수) 개인정보 처리방침 동의", color = ColorTextSecondary, fontSize = 14.sp)
                        }
                        Text(
                            text = "[보기]",
                            color = ColorTextPrimary,
                            fontSize = 13.sp,
                            modifier = Modifier.clickable { showPrivacyDialog = true }.padding(4.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        Toast.makeText(context, "가입 완료", Toast.LENGTH_SHORT).show()
                        val user = pendingAuthResult!!.user!!
                        pendingAuthResult = null
                        isLoggingIn = false
                        onLoginSuccess(user)
                    },
                    enabled = agreeTerms && agreePrivacy
                ) {
                    Text("가입하기", fontWeight = FontWeight.Bold, color = if (agreeTerms && agreePrivacy) ColorUp else ColorTextSecondary)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    pendingAuthResult!!.user?.delete()?.addOnCompleteListener {
                        auth.signOut()
                        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).build()
                        GoogleSignIn.getClient(context, gso).signOut()
                        pendingAuthResult = null
                        isLoggingIn = false
                        Toast.makeText(context, "가입이 취소되었습니다.", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("취소", color = ColorTextSecondary) }
            }
        )
    }

    Column(
        modifier = modifier.padding(32.dp).fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.logo_dtm1),
            contentDescription = "로고",
            modifier = Modifier.size(80.dp),
            contentScale = ContentScale.Fit
        )
        Spacer(modifier = Modifier.height(24.dp))

        Text(text = "단테트레이딩M+", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = ColorTextPrimary)
        Spacer(modifier = Modifier.height(48.dp))

        if (isLoggingIn) {
            CircularProgressIndicator(color = ColorTextPrimary)
        } else {
            Card(
                modifier = Modifier.fillMaxWidth().height(54.dp).clickable {
                    if (webClientId.isEmpty()) return@clickable
                    isLoggingIn = true
                    val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).requestIdToken(webClientId).requestEmail().build()
                    val googleSignInClient = GoogleSignIn.getClient(context, gso)
                    googleSignInLauncher.launch(googleSignInClient.signInIntent)
                },
                shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = ColorSurfaceVariant), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_google),
                        contentDescription = "Google Logo",
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(text = "구글 계정으로 로그인 / 계정 생성", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ColorTextPrimary)
                }
            }
        }
    }
}