package com.example.musicplayer.di

import androidx.room.Room
import com.example.musicplayer.data.local.MusicDatabase
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
 * - `RecentPlayedStore(get())`:
 *     `get()` → Koin tìm `RecentSongDao` (từ Room) rồi truyền vào.
 *     Store chỉ làm nhiệm vụ "map Entity ↔ SongItem", còn SQL do Room lo.
 * - `MusicRepository(get(), get())`:
 *     `get()` → Koin tìm `MusicApiService` (networkModule) + `FavoriteSongDao` (Room).
 *     Repository NHẬN cả network lẫn local persistence từ bên ngoài thay vì tự tạo.
 *
 * ⚠️ PHẦN 2 (Room): database + DAO cũng khai báo `single` tại đây —
 * cùng module với Repository để mọi tầng data dùng chung 1 instance Room.
 */
val repositoryModule = module {

    // ---- Room database (PHẦN 2) ----
    // `single` → toàn app dùng chung 1 database instance (Room tự quản lý connection pool)
    single {
        Room.databaseBuilder(
            androidContext(),
            MusicDatabase::class.java,
            "music_player.db"
        ).build()
    }
    // DAO — Room sinh implementation, `get()` lấy database đã tạo ở trên
    single { get<MusicDatabase>().recentSongDao() }
    single { get<MusicDatabase>().favoriteSongDao() }

    // ---- Repository layer ----
    single { RecentPlayedStore(get()) }
    single { MusicRepository(get(), get()) }
}
