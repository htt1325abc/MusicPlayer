package com.example.musicplayer.di

import com.example.musicplayer.network.MusicApiService
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.koin.dsl.module
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * networkModule — nơi Koin "sản xuất" các dependency tầng Network.
 *
 * TẠI SAO dùng Koin thay vì `RetrofitClient.apiService` (object singleton)?
 * → Trước đây: RetrofitClient là object tự khởi tạo nội bộ, mọi nơi gọi
 *   `RetrofitClient.apiService` là lấy CÙNG 1 instance (khó test, khó thay thế).
 * → Với Koin: module khai báo "cách tạo" + "scope" của từng object.
 *   - `single {}`  → tạo 1 lần duy nhất, tái dùng toàn app (giống singleton cũ)
 *   - `get()`      → Koin tự tìm dependency đã khai báo để truyền vào
 *   Khi cần đổi (VD: thêm fake API cho test), chỉ cần sửa 1 chỗ trong module.
 */
val networkModule = module {

    // OkHttpClient với logging interceptor — dùng `get()`? Không cần, tự tạo.
    single {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    // Retrofit — `get()` lấy OkHttpClient Koin vừa tạo ở trên.
    single {
        Retrofit.Builder()
            .baseUrl("http://127.0.0.1:3000/")
            .client(get())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    // ApiService — `get()` lấy Retrofit ở trên rồi create.
    // QUAN TRỌNG: `single` → chỉ 1 ApiService cho toàn app.
    single {
        get<Retrofit>().create(MusicApiService::class.java)
    }
}
