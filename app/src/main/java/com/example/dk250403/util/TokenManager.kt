package com.example.dk250403.util



import android.content.Context

import androidx.security.crypto.EncryptedSharedPreferences

import androidx.security.crypto.MasterKey



data class AccountInfo(

    val slot: Int,

    val accountNumber: String,

    val appKey: String,

    val secretKey: String

)



class TokenManager private constructor(context: Context) {



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



    companion object {

        @Volatile

        private var instance: TokenManager? = null



        fun getInstance(context: Context): TokenManager {

            return instance ?: synchronized(this) {

                instance ?: TokenManager(context.applicationContext).also { instance = it }

            }

        }

    }



    fun saveAccountInfo(uid: String, slot: Int, accNo: String, appKey: String, secretKey: String) {

        sharedPreferences.edit()

            .putString("ACC_NO_${slot}_$uid", accNo)

            .putString("APP_KEY_${slot}_$uid", appKey)

            .putString("SECRET_KEY_${slot}_$uid", secretKey)

            .apply()

    }



    fun getAccountInfo(uid: String, slot: Int): AccountInfo? {

        val accNo = sharedPreferences.getString("ACC_NO_${slot}_$uid", "") ?: ""

        if (accNo.isBlank()) return null



        val appKey = sharedPreferences.getString("APP_KEY_${slot}_$uid", "") ?: ""

        val secretKey = sharedPreferences.getString("SECRET_KEY_${slot}_$uid", "") ?: ""



        return AccountInfo(slot, accNo, appKey, secretKey)

    }



    fun getAllAccounts(uid: String): List<AccountInfo> {

        return (1..3).mapNotNull { getAccountInfo(uid, it) }

    }



    fun getAccountNumbers(uid: String): List<String> {

        return getAllAccounts(uid).map { it.accountNumber }

    }



    fun getCredentialsForAccount(uid: String, targetAccNo: String): AccountInfo? {

        return getAllAccounts(uid).find { it.accountNumber == targetAccNo }

    }



    fun saveAccessToken(uid: String, accNo: String, token: String) {

        sharedPreferences.edit().putString("ACCESS_TOKEN_${uid}_$accNo", token).apply()

    }



    fun getAccessToken(uid: String, accNo: String): String? {

        return sharedPreferences.getString("ACCESS_TOKEN_${uid}_$accNo", null)

    }



    fun clearAccessToken(uid: String, accNo: String) {

        sharedPreferences.edit().remove("ACCESS_TOKEN_${uid}_$accNo").apply()

    }



    // 💡 회원 탈퇴 시 호출: 해당 UID와 연관된 모든 로컬 암호화 데이터를 영구 파기

    fun clearAllUserData(uid: String) {

        val editor = sharedPreferences.edit()



        // 1. 발급된 모든 계좌의 토큰 삭제

        val accounts = getAccountNumbers(uid)

        for (accNo in accounts) {

            editor.remove("ACCESS_TOKEN_${uid}_$accNo")

        }



        // 2. 슬롯별 앱키, 시크릿키, 계좌번호 삭제

        for (slot in 1..3) {

            editor.remove("ACC_NO_${slot}_$uid")

            editor.remove("APP_KEY_${slot}_$uid")

            editor.remove("SECRET_KEY_${slot}_$uid")

        }



        editor.apply()

    }

}

