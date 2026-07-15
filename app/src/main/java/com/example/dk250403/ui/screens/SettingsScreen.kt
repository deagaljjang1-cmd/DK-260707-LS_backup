package com.example.dk250403.ui.screens



import android.content.Intent

import android.net.Uri

import android.widget.Toast

import androidx.activity.compose.rememberLauncherForActivityResult

import androidx.activity.result.contract.ActivityResultContracts

import androidx.compose.foundation.Image

import androidx.compose.foundation.background

import androidx.compose.foundation.clickable

import androidx.compose.foundation.layout.*

import androidx.compose.foundation.rememberScrollState

import androidx.compose.foundation.shape.CircleShape

import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.foundation.verticalScroll

import androidx.compose.material.icons.Icons

import androidx.compose.material.icons.filled.*

import androidx.compose.material3.*

import androidx.compose.runtime.*

import androidx.compose.ui.Alignment

import androidx.compose.ui.Modifier

import androidx.compose.ui.draw.clip

import androidx.compose.ui.graphics.Color

import androidx.compose.ui.layout.ContentScale

import androidx.compose.ui.platform.LocalClipboardManager

import androidx.compose.ui.platform.LocalContext

import androidx.compose.ui.res.painterResource

import androidx.compose.ui.text.AnnotatedString

import androidx.compose.ui.text.TextStyle

import androidx.compose.ui.text.font.FontWeight

import androidx.compose.ui.text.input.PasswordVisualTransformation

import androidx.compose.ui.text.input.VisualTransformation

import androidx.compose.ui.unit.dp

import androidx.compose.ui.unit.sp

import coil.compose.rememberAsyncImagePainter

import coil.request.ImageRequest

import com.canhub.cropper.CropImageContract

import com.canhub.cropper.CropImageContractOptions

import com.canhub.cropper.CropImageOptions

import com.example.dk250403.*

import com.example.dk250403.R

import com.example.dk250403.util.TokenManager

import com.google.android.gms.auth.api.signin.GoogleSignIn

import com.google.android.gms.auth.api.signin.GoogleSignInOptions

import com.google.firebase.auth.FirebaseAuth

import com.google.firebase.firestore.FirebaseFirestore

import com.google.firebase.firestore.SetOptions

import com.google.firebase.storage.FirebaseStorage



@Composable

fun AccountSlotInputGroup(

    slotTitle: String,

    accInput: String, onAccChange: (String) -> Unit, isAccVisible: Boolean, onAccToggle: () -> Unit,

    appKeyInput: String, onAppKeyChange: (String) -> Unit, isAppKeyVisible: Boolean, onAppKeyToggle: () -> Unit,

    secretKeyInput: String, onSecretKeyChange: (String) -> Unit, isSecretKeyVisible: Boolean, onSecretKeyToggle: () -> Unit

) {

    Column(modifier = Modifier.fillMaxWidth().background(ColorSurfaceVariant, RoundedCornerShape(8.dp)).padding(12.dp)) {

        Text(slotTitle, color = ColorTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)

        Spacer(modifier = Modifier.height(12.dp))



        OutlinedTextField(

            value = accInput, onValueChange = onAccChange, modifier = Modifier.fillMaxWidth(), singleLine = true,

            label = { Text("계좌번호 (숫자만 입력)", fontSize = 12.sp) },

            visualTransformation = if (isAccVisible) VisualTransformation.None else PasswordVisualTransformation(),

            colors = OutlinedTextFieldDefaults.colors(

                focusedTextColor = ColorTextPrimary, unfocusedTextColor = ColorTextPrimary,

                focusedBorderColor = ColorStatus, unfocusedBorderColor = Color.Transparent,

                focusedLabelColor = ColorTextPrimary, unfocusedLabelColor = ColorTextPrimary

            ),

            trailingIcon = {

                Row(verticalAlignment = Alignment.CenterVertically) {

                    if (accInput.isNotEmpty()) {

                        IconButton(onClick = { onAccChange("") }) { Icon(Icons.Default.Clear, contentDescription = "전체 삭제", tint = ColorTextSecondary) }

                    }

                    IconButton(onClick = onAccToggle) { Icon(imageVector = if (isAccVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff, contentDescription = "토글", tint = ColorTextSecondary) }

                }

            }

        )

        Spacer(modifier = Modifier.height(8.dp))



        OutlinedTextField(

            value = appKeyInput, onValueChange = onAppKeyChange, modifier = Modifier.fillMaxWidth(), singleLine = true,

            label = { Text("앱키(App Key)", fontSize = 12.sp) },

            visualTransformation = if (isAppKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),

            colors = OutlinedTextFieldDefaults.colors(

                focusedTextColor = ColorTextPrimary, unfocusedTextColor = ColorTextPrimary,

                focusedBorderColor = ColorStatus, unfocusedBorderColor = Color.Transparent,

                focusedLabelColor = ColorTextPrimary, unfocusedLabelColor = ColorTextPrimary

            ),

            trailingIcon = {

                Row(verticalAlignment = Alignment.CenterVertically) {

                    if (appKeyInput.isNotEmpty()) {

                        IconButton(onClick = { onAppKeyChange("") }) { Icon(Icons.Default.Clear, contentDescription = "전체 삭제", tint = ColorTextSecondary) }

                    }

                    IconButton(onClick = onAppKeyToggle) { Icon(imageVector = if (isAppKeyVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff, contentDescription = "토글", tint = ColorTextSecondary) }

                }

            }

        )

        Spacer(modifier = Modifier.height(8.dp))



        OutlinedTextField(

            value = secretKeyInput, onValueChange = onSecretKeyChange, modifier = Modifier.fillMaxWidth(), singleLine = true,

            label = { Text("시크릿키(Secret Key)", fontSize = 12.sp) },

            visualTransformation = if (isSecretKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),

            colors = OutlinedTextFieldDefaults.colors(

                focusedTextColor = ColorTextPrimary, unfocusedTextColor = ColorTextPrimary,

                focusedBorderColor = ColorStatus, unfocusedBorderColor = Color.Transparent,

                focusedLabelColor = ColorTextPrimary, unfocusedLabelColor = ColorTextPrimary

            ),

            trailingIcon = {

                Row(verticalAlignment = Alignment.CenterVertically) {

                    if (secretKeyInput.isNotEmpty()) {

                        IconButton(onClick = { onSecretKeyChange("") }) { Icon(Icons.Default.Clear, contentDescription = "전체 삭제", tint = ColorTextSecondary) }

                    }

                    IconButton(onClick = onSecretKeyToggle) { Icon(imageVector = if (isSecretKeyVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff, contentDescription = "토글", tint = ColorTextSecondary) }

                }

            }

        )

    }

}



@Composable

fun SettingsScreen(

    modifier: Modifier = Modifier, initialNickname: String, initialProfileUrl: String, isPremium: Boolean,

    onProfileSaved: (String, String?) -> Unit, onBack: () -> Unit, onLogout: () -> Unit

) {

    val auth = FirebaseAuth.getInstance()

    val db = FirebaseFirestore.getInstance()

    val storage = FirebaseStorage.getInstance()

    val context = LocalContext.current

    val clipboardManager = LocalClipboardManager.current



    val tokenManager = remember { TokenManager.getInstance(context) }

    val uid = auth.currentUser?.uid ?: ""



    var tempNickname by remember { mutableStateOf(initialNickname) }

    var localPreviewUri by remember { mutableStateOf<Uri?>(null) }

    var isDuplicateChecked by remember { mutableStateOf(false) }

    var isNicknameAvailable by remember { mutableStateOf(false) }

    var isSavingInProgress by remember { mutableStateOf(false) }

    var showDeleteDialog by remember { mutableStateOf(false) }

    var isDeletingAccount by remember { mutableStateOf(false) }



    var showApiDialog by remember { mutableStateOf(false) }



    var acc1Input by remember { mutableStateOf("") }

    var appKey1Input by remember { mutableStateOf("") }

    var secretKey1Input by remember { mutableStateOf("") }

    var isAcc1Vis by remember { mutableStateOf(false) }

    var isApp1Vis by remember { mutableStateOf(false) }

    var isSec1Vis by remember { mutableStateOf(false) }



    var acc2Input by remember { mutableStateOf("") }

    var appKey2Input by remember { mutableStateOf("") }

    var secretKey2Input by remember { mutableStateOf("") }

    var isAcc2Vis by remember { mutableStateOf(false) }

    var isApp2Vis by remember { mutableStateOf(false) }

    var isSec2Vis by remember { mutableStateOf(false) }



    var acc3Input by remember { mutableStateOf("") }

    var appKey3Input by remember { mutableStateOf("") }

    var secretKey3Input by remember { mutableStateOf("") }

    var isAcc3Vis by remember { mutableStateOf(false) }

    var isApp3Vis by remember { mutableStateOf(false) }

    var isSec3Vis by remember { mutableStateOf(false) }



    androidx.activity.compose.BackHandler { onBack() }



    val nicknameError by remember(tempNickname) {

        derivedStateOf {

            if (tempNickname.contains(" ")) "❌ 띄어쓰기(공백)는 사용할 수 없습니다."

            else if (tempNickname.isNotEmpty() && !NICKNAME_REGEX.matches(tempNickname)) "❌ 한글, 영문, 숫자, 기호(-, _, [, ])만 가능합니다."

            else if (tempNickname.isNotEmpty() && (tempNickname.length < 2 || tempNickname.length > 12)) "❌ 닉네임은 2자 이상 12자 이하로 입력해 주세요."

            else {

                val foundBannedWord = BANNED_WORDS.find { tempNickname.contains(it, ignoreCase = true) }

                if (foundBannedWord != null) "❌ [$foundBannedWord]는 닉네임으로 사용할 수 없습니다." else null

            }

        }

    }



    val cropLauncher = rememberLauncherForActivityResult(contract = CropImageContract()) { result ->

        if (result.isSuccessful) localPreviewUri = result.uriContent else Toast.makeText(context, "편집 취소됨", Toast.LENGTH_SHORT).show()

    }

    val galleryLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri: Uri? ->

        if (uri != null) {

            cropLauncher.launch(CropImageContractOptions(uri = uri, cropImageOptions = CropImageOptions().apply {

                fixAspectRatio = true

                aspectRatioX = 1

                aspectRatioY = 1

                maxCropResultWidth = 500

                maxCropResultHeight = 500

            }))

        }

    }



    if (showDeleteDialog) {

        AlertDialog(

            containerColor = ColorSurface,

            onDismissRequest = { showDeleteDialog = false },

            title = { Text("회원 탈퇴", fontWeight = FontWeight.Bold, color = ColorUp) },

            text = { Text("탈퇴 시 모든 정보가 즉시 삭제되며 복구할 수 없습니다. \n\n정말 탈퇴하시겠습니까?", fontSize = 14.sp, color = ColorTextPrimary) },

            confirmButton = {

                TextButton(onClick = {

                    showDeleteDialog = false

                    isDeletingAccount = true



                    // 💡 핵심 보안 조치: 파이어베이스 삭제 전에 기기 금고 데이터 완벽 초기화

                    tokenManager.clearAllUserData(uid)



                    db.collection("users").document(uid).delete().addOnCompleteListener {

                        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).build()

                        GoogleSignIn.getClient(context, gso).revokeAccess().addOnCompleteListener {

                            auth.currentUser?.delete()?.addOnCompleteListener { task ->

                                isDeletingAccount = false

                                if (task.isSuccessful) { Toast.makeText(context, "회원 탈퇴가 완료되었습니다.", Toast.LENGTH_LONG).show(); onLogout() }

                                else { Toast.makeText(context, "보안을 위해 재로그인 후 탈퇴를 진행해 주세요.", Toast.LENGTH_LONG).show(); onLogout() }

                            }

                        }

                    }

                }) { Text("영구 탈퇴", color = ColorUp, fontWeight = FontWeight.Bold) }

            },

            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("취소", color = ColorTextSecondary) } }

        )

    }



    if (showApiDialog) {

        AlertDialog(

            containerColor = ColorSurface,

            onDismissRequest = { showApiDialog = false },

            title = { Text("오픈API 계좌 연동", fontWeight = FontWeight.Bold, color = ColorTextPrimary, fontSize = 18.sp) },

            text = {

                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {

                    Text(

                        text = "▶ 오픈API 앱키/시크릿키 발급 가이드 (필독)",

                        color = ColorDown,

                        fontSize = 13.sp,

                        fontWeight = FontWeight.Bold,

                        modifier = Modifier

                            .clickable {

                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://m.xn--6j1bp61aksejsj.com/page/dantetram.html"))

                                context.startActivity(intent)

                            }

                            .padding(bottom = 16.dp)

                    )



                    AccountSlotInputGroup(

                        slotTitle = "계좌 슬롯 1 (주계좌)",

                        accInput = acc1Input, onAccChange = { acc1Input = it }, isAccVisible = isAcc1Vis, onAccToggle = { isAcc1Vis = !isAcc1Vis },

                        appKeyInput = appKey1Input, onAppKeyChange = { appKey1Input = it }, isAppKeyVisible = isApp1Vis, onAppKeyToggle = { isApp1Vis = !isApp1Vis },

                        secretKeyInput = secretKey1Input, onSecretKeyChange = { secretKey1Input = it }, isSecretKeyVisible = isSec1Vis, onSecretKeyToggle = { isSec1Vis = !isSec1Vis }

                    )

                    Spacer(modifier = Modifier.height(16.dp))



                    AccountSlotInputGroup(

                        slotTitle = "계좌 슬롯 2",

                        accInput = acc2Input, onAccChange = { acc2Input = it }, isAccVisible = isAcc2Vis, onAccToggle = { isAcc2Vis = !isAcc2Vis },

                        appKeyInput = appKey2Input, onAppKeyChange = { appKey2Input = it }, isAppKeyVisible = isApp2Vis, onAppKeyToggle = { isApp2Vis = !isApp2Vis },

                        secretKeyInput = secretKey2Input, onSecretKeyChange = { secretKey2Input = it }, isSecretKeyVisible = isSec2Vis, onSecretKeyToggle = { isSec2Vis = !isSec2Vis }

                    )

                    Spacer(modifier = Modifier.height(16.dp))



                    AccountSlotInputGroup(

                        slotTitle = "계좌 슬롯 3",

                        accInput = acc3Input, onAccChange = { acc3Input = it }, isAccVisible = isAcc3Vis, onAccToggle = { isAcc3Vis = !isAcc3Vis },

                        appKeyInput = appKey3Input, onAppKeyChange = { appKey3Input = it }, isAppKeyVisible = isApp3Vis, onAppKeyToggle = { isApp3Vis = !isApp3Vis },

                        secretKeyInput = secretKey3Input, onSecretKeyChange = { secretKey3Input = it }, isSecretKeyVisible = isSec3Vis, onSecretKeyToggle = { isSec3Vis = !isSec3Vis }

                    )



                    Spacer(modifier = Modifier.height(16.dp))

                    Text("⚠️ 입력하신 키와 계좌는 기기에만 안전하게 암호화되어 저장됩니다.", color = ColorTextSecondary, fontSize = 11.sp)

                }

            },

            confirmButton = {

                TextButton(

                    onClick = {

                        val slots = listOf(

                            Triple(acc1Input.trim(), appKey1Input.trim(), secretKey1Input.trim()),

                            Triple(acc2Input.trim(), appKey2Input.trim(), secretKey2Input.trim()),

                            Triple(acc3Input.trim(), appKey3Input.trim(), secretKey3Input.trim())

                        )



                        val validAccounts = mutableListOf<String>()



                        for ((acc, appKey, secKey) in slots) {

                            if (acc.isNotEmpty()) {

                                if (appKey.isEmpty() || secKey.isEmpty()) {

                                    Toast.makeText(context, "⚠️ 계좌를 입력한 슬롯은 앱키와 시크릿키를 모두 입력해야 합니다.", Toast.LENGTH_LONG).show()

                                    return@TextButton

                                }

                                validAccounts.add(acc)

                            }

                        }



                        if (validAccounts.size != validAccounts.toSet().size) {

                            Toast.makeText(context, "⚠️ 동일한 계좌번호가 중복으로 입력되었습니다.", Toast.LENGTH_SHORT).show()

                            return@TextButton

                        }



                        tokenManager.saveAccountInfo(uid, 1, slots[0].first, slots[0].second, slots[0].third)

                        tokenManager.clearAccessToken(uid, slots[0].first)



                        tokenManager.saveAccountInfo(uid, 2, slots[1].first, slots[1].second, slots[1].third)

                        tokenManager.clearAccessToken(uid, slots[1].first)



                        tokenManager.saveAccountInfo(uid, 3, slots[2].first, slots[2].second, slots[2].third)

                        tokenManager.clearAccessToken(uid, slots[2].first)



                        Toast.makeText(context, "앱키 및 계좌번호가 저장되었습니다.", Toast.LENGTH_SHORT).show()

                        showApiDialog = false

                    }

                ) { Text("저장", color = ColorStatus, fontWeight = FontWeight.Bold) }

            },

            dismissButton = { TextButton(onClick = { showApiDialog = false }) { Text("취소", color = ColorTextSecondary) } }

        )

    }



    Column(modifier = modifier.fillMaxSize().background(ColorBg).padding(horizontal = 20.dp, vertical = 16.dp).verticalScroll(rememberScrollState())) {

        Row(verticalAlignment = Alignment.CenterVertically) {

            IconButton(onClick = onBack, modifier = Modifier.offset(x = (-12).dp)) { Icon(Icons.Default.ArrowBack, contentDescription = "뒤로가기", tint = ColorTextPrimary) }

            Text("계정 설정", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = ColorTextPrimary)

        }

        Spacer(Modifier.height(32.dp))



        Box(modifier = Modifier.align(Alignment.CenterHorizontally).clickable { galleryLauncher.launch("image/*") }, contentAlignment = Alignment.BottomEnd) {

            if (localPreviewUri != null) {

                Image(painter = rememberAsyncImagePainter(ImageRequest.Builder(context).data(localPreviewUri).crossfade(true).build()), contentDescription = "새 프로필", modifier = Modifier.size(100.dp).clip(CircleShape), contentScale = ContentScale.Crop)

            } else if (initialProfileUrl.isNotEmpty()) {

                Image(painter = rememberAsyncImagePainter(ImageRequest.Builder(context).data(initialProfileUrl).crossfade(true).build()), contentDescription = "서버 프로필", modifier = Modifier.size(100.dp).clip(CircleShape), contentScale = ContentScale.Crop)

            } else {

                Icon(Icons.Default.AccountCircle, contentDescription = "기본 프로필", modifier = Modifier.size(100.dp), tint = ColorTextSecondary)

            }

            Box(modifier = Modifier.padding(4.dp).size(28.dp).clip(CircleShape).background(ColorSurfaceVariant).padding(4.dp), contentAlignment = Alignment.Center) { Icon(Icons.Default.Edit, contentDescription = "변경", modifier = Modifier.size(18.dp), tint = ColorTextPrimary) }

        }



        if (isPremium) {

            Spacer(Modifier.height(16.dp))

            Row(modifier = Modifier.align(Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {

                Image(painter = painterResource(id = R.drawable.ic_verified), contentDescription = "인증 완료", modifier = Modifier.size(20.dp))

                Spacer(modifier = Modifier.width(6.dp))

                Text(text = "인증이 완료 된 계정입니다", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = ColorStatus)

            }

            Spacer(Modifier.height(16.dp))



            Button(

                onClick = {

                    val accInfo1 = tokenManager.getAccountInfo(uid, 1)

                    acc1Input = accInfo1?.accountNumber ?: ""

                    appKey1Input = accInfo1?.appKey ?: ""

                    secretKey1Input = accInfo1?.secretKey ?: ""



                    val accInfo2 = tokenManager.getAccountInfo(uid, 2)

                    acc2Input = accInfo2?.accountNumber ?: ""

                    appKey2Input = accInfo2?.appKey ?: ""

                    secretKey2Input = accInfo2?.secretKey ?: ""



                    val accInfo3 = tokenManager.getAccountInfo(uid, 3)

                    acc3Input = accInfo3?.accountNumber ?: ""

                    appKey3Input = accInfo3?.appKey ?: ""

                    secretKey3Input = accInfo3?.secretKey ?: ""



                    isAcc1Vis = false; isApp1Vis = false; isSec1Vis = false

                    isAcc2Vis = false; isApp2Vis = false; isSec2Vis = false

                    isAcc3Vis = false; isApp3Vis = false; isSec3Vis = false



                    showApiDialog = true

                },

                modifier = Modifier.align(Alignment.CenterHorizontally).height(40.dp),

                shape = RoundedCornerShape(20.dp),

                colors = ButtonDefaults.buttonColors(containerColor = ColorSurfaceVariant)

            ) {

                Icon(Icons.Default.Key, contentDescription = "키", modifier = Modifier.size(16.dp), tint = ColorTextPrimary)

                Spacer(modifier = Modifier.width(8.dp))

                Text("증권사 앱키 및 계좌번호 관리", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ColorTextPrimary)

            }

            Spacer(Modifier.height(24.dp))

        } else {

            Spacer(Modifier.height(40.dp))

        }



        Text("닉네임", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = ColorTextSecondary)

        Spacer(Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {

            OutlinedTextField(

                value = tempNickname,

                onValueChange = { input -> tempNickname = input; isDuplicateChecked = false; isNicknameAvailable = false },

                isError = nicknameError != null,

                modifier = Modifier.weight(1f).height(50.dp), singleLine = true, shape = RoundedCornerShape(8.dp),

                colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = Color.White, unfocusedContainerColor = Color.White, errorContainerColor = Color.White, focusedTextColor = Color.Black, unfocusedTextColor = Color.Black, errorTextColor = ColorUp, focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent, errorBorderColor = ColorUp),

                textStyle = TextStyle(fontSize = 14.sp)

            )

            Spacer(modifier = Modifier.width(8.dp))

            Button(

                onClick = {

                    val trimmed = tempNickname.trim()

                    if (trimmed.isEmpty() || nicknameError != null) return@Button

                    if (trimmed == initialNickname) { isDuplicateChecked = true; isNicknameAvailable = true; Toast.makeText(context, "현재 닉네임입니다.", Toast.LENGTH_SHORT).show(); return@Button }

                    db.collection("users").whereEqualTo("nickname", trimmed).get().addOnSuccessListener { query ->

                        val exists = query.documents.any { it.id != uid }

                        isDuplicateChecked = true

                        if (exists) { isNicknameAvailable = false; Toast.makeText(context, "❌ 이미 사용 중입니다.", Toast.LENGTH_SHORT).show() }

                        else { isNicknameAvailable = true; Toast.makeText(context, "✅ 사용 가능합니다.", Toast.LENGTH_SHORT).show() }

                    }

                }, modifier = Modifier.height(50.dp), shape = RoundedCornerShape(8.dp), colors = ButtonDefaults.buttonColors(containerColor = ColorSurfaceVariant)

            ) { Text("중복 검사", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ColorTextPrimary) }

        }



        if (nicknameError != null) {

            Spacer(Modifier.height(5.dp))

            Text(text = nicknameError!!, color = ColorUp, fontSize = 12.sp, fontWeight = FontWeight.Medium)

        }



        Spacer(Modifier.height(24.dp))

        Text("UID", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = ColorTextSecondary)

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(

            value = uid, onValueChange = {}, readOnly = true, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(8.dp), textStyle = TextStyle(fontSize = 14.sp, color = ColorTextSecondary),

            trailingIcon = { IconButton(onClick = { clipboardManager.setText(AnnotatedString(uid)); Toast.makeText(context, "UID 복사됨", Toast.LENGTH_SHORT).show() }) { Icon(Icons.Default.ContentCopy, "복사", modifier = Modifier.size(18.dp), tint = ColorTextSecondary) } },

            colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = ColorSurface, focusedContainerColor = ColorSurface, unfocusedBorderColor = Color.Transparent, focusedBorderColor = Color.Transparent)

        )



        Spacer(Modifier.height(48.dp))



        if (isSavingInProgress || isDeletingAccount) {

            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally), color = ColorTextPrimary)

        } else {

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {

                OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f).height(50.dp), shape = RoundedCornerShape(8.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = ColorTextPrimary)) { Text("취소", fontSize = 15.sp, fontWeight = FontWeight.Bold) }

                Button(

                    onClick = {

                        val finalName = tempNickname.trim()

                        if (nicknameError != null || finalName.isEmpty()) return@Button

                        if (finalName != initialNickname && (!isDuplicateChecked || !isNicknameAvailable)) { Toast.makeText(context, "닉네임 중복 검사를 완료하세요.", Toast.LENGTH_SHORT).show(); return@Button }



                        isSavingInProgress = true

                        fun saveProfileDataToFirestore(downloadUrl: String?) {

                            val userProfile = hashMapOf<String, Any>("nickname" to finalName)

                            if (downloadUrl != null) userProfile["profileImageUrl"] = downloadUrl

                            db.collection("users").document(uid).set(userProfile, SetOptions.merge())

                                .addOnSuccessListener { isSavingInProgress = false; Toast.makeText(context, "저장되었습니다.", Toast.LENGTH_SHORT).show(); onProfileSaved(finalName, downloadUrl) }

                        }

                        if (localPreviewUri != null) {

                            try {

                                val inputStream = context.contentResolver.openInputStream(localPreviewUri!!)

                                val originalBitmap = android.graphics.BitmapFactory.decodeStream(inputStream)

                                inputStream?.close()

                                if (originalBitmap != null) {

                                    val resizedBitmap = android.graphics.Bitmap.createScaledBitmap(originalBitmap, 200, 200, true)

                                    val baos = java.io.ByteArrayOutputStream()

                                    resizedBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, baos)

                                    val storageRef = storage.reference.child("profile_images/$uid.jpg")

                                    storageRef.putBytes(baos.toByteArray()).addOnSuccessListener { _ ->

                                        storageRef.downloadUrl.addOnSuccessListener { uri -> saveProfileDataToFirestore(uri.toString()) }

                                    }

                                }

                            } catch (e: Exception) { isSavingInProgress = false }

                        } else { saveProfileDataToFirestore(null) }

                    }, modifier = Modifier.weight(1.2f).height(50.dp), shape = RoundedCornerShape(8.dp), colors = ButtonDefaults.buttonColors(containerColor = ColorTextPrimary)

                ) { Text("변경 완료", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ColorBg) }

            }

        }



        Spacer(Modifier.height(48.dp))

        Button(

            onClick = { showDeleteDialog = true }, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(8.dp),

            colors = ButtonDefaults.buttonColors(containerColor = ColorSurfaceVariant)

        ) { Text("회원 탈퇴", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = ColorUp) }

    }

}