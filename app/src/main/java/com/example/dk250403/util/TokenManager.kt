package com.example.dk250403.util

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class TokenManager(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences = EncryptedSharedPreferences.create(
        context,
        "ls_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveAccessToken(uid: String, token: String) {
        sharedPreferences.edit().putString("ACCESS_TOKEN_$uid", token).apply()
    }

    fun getAccessToken(uid: String): String? {
        return sharedPreferences.getString("ACCESS_TOKEN_$uid", null)
    }

    fun saveAppCredentials(uid: String, appKey: String, secretKey: String) {
        sharedPreferences.edit()
            .putString("APP_KEY_$uid", appKey)
            .putString("SECRET_KEY_$uid", secretKey)
            .apply()
    }

    fun getAppKey(uid: String): String? = sharedPreferences.getString("APP_KEY_$uid", null)
    fun getSecretKey(uid: String): String? = sharedPreferences.getString("SECRET_KEY_$uid", null)

    // --- 추가: 계좌번호 최대 3개 저장 및 불러오기 ---
    fun saveAccountNumbers(uid: String, acc1: String, acc2: String, acc3: String) {
        sharedPreferences.edit()
            .putString("ACC_NO_1_$uid", acc1)
            .putString("ACC_NO_2_$uid", acc2)
            .putString("ACC_NO_3_$uid", acc3)
            .apply()
    }

    // 등록된 계좌 중 빈 값이 아닌 것만 리스트로 반환
    fun getAccountNumbers(uid: String): List<String> {
        val acc1 = sharedPreferences.getString("ACC_NO_1_$uid", "") ?: ""
        val acc2 = sharedPreferences.getString("ACC_NO_2_$uid", "") ?: ""
        val acc3 = sharedPreferences.getString("ACC_NO_3_$uid", "") ?: ""

        return listOf(acc1, acc2, acc3).filter { it.isNotBlank() }
    }
}