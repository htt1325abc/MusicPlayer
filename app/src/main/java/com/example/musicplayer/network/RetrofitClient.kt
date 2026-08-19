package com.example.musicplayer.network

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Singleton tạo Retrofit instance.
 *
 * ⚠️ LƯU Ý: File này HIỆN KHÔNG còn được dùng — Retrofit thật do Koin tạo trong
 * `di/NetworkModule.kt`. Giữ lại chỉ để tham khảo cấu hình + đảm bảo đồng bộ
 * nếu sau này có chỗ gọi `RetrofitClient.apiService`.
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
    //
    // 🆕 2026-08-19: App ĐÃ CHUYỂN sang Jamendo API công khai (không cần server local)
    //   → Base URL + client_id lấy từ JamendoConfig.

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
            // Tự động thêm client_id vào mọi request (giống NetworkModule)
            .addInterceptor { chain ->
                val original = chain.request()
                val url = original.url.newBuilder()
                    .addQueryParameter("client_id", JamendoConfig.CLIENT_ID)
                    .build()
                chain.proceed(original.newBuilder().url(url).build())
            }
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
            .baseUrl(JamendoConfig.BASE_URL)
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
