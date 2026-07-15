package com.example.dk250403.network



import okhttp3.OkHttpClient

import okhttp3.logging.HttpLoggingInterceptor

import retrofit2.Retrofit

import retrofit2.converter.gson.GsonConverterFactory

import java.util.concurrent.TimeUnit



object RetrofitClient {

    // LS증권 실전투자 API 기본 주소 (모의투자의 경우 도메인이 다를 수 있으므로 LS증권 포털 확인 필요)

    private const val BASE_URL = "https://openapi.ls-sec.co.kr:8080/"



    private val loggingInterceptor = HttpLoggingInterceptor().apply {

        level = HttpLoggingInterceptor.Level.BODY // 통신 로그 확인 (출시 전에는 NONE으로 변경 권장)

    }



    private val okHttpClient = OkHttpClient.Builder()

        .addInterceptor(loggingInterceptor)

        .connectTimeout(15, TimeUnit.SECONDS)

        .readTimeout(15, TimeUnit.SECONDS)

        .build()



    val lsApi: LsApiService by lazy {

        Retrofit.Builder()

            .baseUrl(BASE_URL)

            .client(okHttpClient)

            .addConverterFactory(GsonConverterFactory.create())

            .build()

            .create(LsApiService::class.java)

    }

}

