package com.example.musicplayer.di

import com.example.musicplayer.repository.MusicRepository
import com.example.musicplayer.repository.RecentPlayedStore
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * repositoryModule — Koin cung cấp các class tầng Repository.
 *
 * TẠI SAO tách riêng theo tầng?
 * → Dễ đọc: thấy rõ mỗi tầng (Network → Repository → ViewModel) cần gì.
 * → Dễ test: thay 1 module là đổi cả hành vi tầng đó (VD: dùng fake repository).
 *
 * `single {}` = tạo 1 instance duy nhất, mọi nơi xài chung (giống singleton).
 *
 * - `RecentPlayedStore(androidContext())`:
 *     `androidContext()` → Koin tự lấy Application context của app (không cần
 *     Activity context) → an toàn, không rò rỉ memory.
 * - `MusicRepository(get())`:
 *     `get()` → Koin tìm `MusicApiService` (đã khai báo ở networkModule) rồi truyền vào.
 *     Repository NHẬN ApiService từ bên ngoài thay vì tự `RetrofitClient.apiService`.
 */
val repositoryModule = module {
    single { RecentPlayedStore(androidContext()) }
    single { MusicRepository(get()) }
}
