package com.example.musicplayer.network

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Singleton tạo Retrofit instance.
 *
 * TẠI SAO dùng object (Singleton)?
 * → Chỉ cần 1 instance Retrofit trong toàn app
 * → Tái sử dụng connection pool của OkHttp → tiết kiệm tài nguyên
 * → Tránh tạo nhiều instance gây memory leak
 */
object RetrofitClient {

    // ⚠️ THAY URL NÀY sau khi deploy server lên Render.com
    // Ví dụ: "https://musicplayer-api.onrender.com/"
    //
    // TẠI SAO dùng http://127.0.0.1:3000/ khi dev (thay vì 10.0.2.2)?
    // → 10.0.2.2 là alias của host loopback qua NAT của emulator, NHƯNG trên một số
    //   emulator (đặc biệt API mới, multi-network) app process không connect được.
    // → Giải pháp CHẮC CHẮN nhất: dùng adb reverse (chạy 1 lần khi mở terminal):
    //       adb reverse tcp:3000 tcp:3000
    //   Lệnh này chuyển port 3000 của DEVICE về port 3000 của HOST qua adb daemon
    //   → app gọi http://127.0.0.1:3000/ là tới được server trên máy phát triển.
    // → Khi deploy lên Render, chỉ cần đổi 1 dòng dưới đây sang URL https.
    private const val BASE_URL = "http://127.0.0.1:3000/"

    /**
     * OkHttpClient với logging interceptor.
     * TẠI SAO cần logging? → Xem được request/response trong Logcat khi debug
     * Timeout 30s vì server free tier Render có thể chậm khi cold start
     */
    private val okHttpClient: OkHttpClient by lazy {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            // BODY = log cả request body + response body → dễ debug JSON
            level = HttpLoggingInterceptor.Level.BODY
        }

        OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Retrofit instance — by lazy để chỉ khởi tạo khi cần lần đầu
     */
    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            // GsonConverterFactory tự convert JSON ↔ Kotlin data class
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    /**
     * API Service instance — dùng ở Repository để gọi API
     */
    val apiService: MusicApiService by lazy {
        retrofit.create(MusicApiService::class.java)
    }
}
