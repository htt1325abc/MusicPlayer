package com.example.musicplayer.di

import androidx.room.Room
import com.example.musicplayer.data.local.Migrations
import com.example.musicplayer.data.local.MusicDatabase
import com.example.musicplayer.data.local.mapper.FavoriteSongMapper
import com.example.musicplayer.data.local.mapper.PlaylistMapper
import com.example.musicplayer.data.local.mapper.RecentSongMapper
import com.example.musicplayer.data.local.repository.RecentPlayedStore
import com.example.musicplayer.data.repository.MusicRepositoryImpl
import com.example.musicplayer.domain.repository.MusicRepository
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * repositoryModule — Koin cung cấp các class tầng Repository.
 *
 * TẠI SAO tách riêng theo tầng?
 * → Dễ đọc: thấy rõ mỗi tầng (Network → Repository → ViewModel) cần gì.
 * → Dễ test: thay 1 module là đổi cả hành vi tầng đó.
 *
 * THAY ĐỔI THEO CONVENTION MẪU:
 * → Repository chính đăng ký theo INTERFACE: `singleOf(::MusicRepositoryImpl) bind MusicRepository::class`
 *   (giống mẫu: `singleOf(::GithubRepositoryImpl) bind GithubRepository::class`).
 *   Mọi nơi inject `MusicRepository` đều nhận đúng 1 impl duy nhất.
 * → Entity ↔ Model map qua các mapper trong `data/local/mapper/` (đăng ký single để dùng chung).
 * → `RecentPlayedStore` giờ nằm ở `data/local/repository/` (local repository của Room).
 *
 * `single {}` = tạo 1 instance duy nhất, mọi nơi xài chung (giống singleton).
 * `get()` = Koin tự tìm dependency đã khai báo để truyền vào.
 */
val repositoryModule = module {

    // ---- Room database (cùng module với Repository để mọi tầng data dùng chung 1 instance) ----
    single {
        Room.databaseBuilder(
            androidContext(),
            MusicDatabase::class.java,
            MusicDatabase.DATABASE_NAME
        )
            // Có Migration (1 → 2 thêm bảng saved_playlists) → giữ dữ liệu cũ khi nâng cấp
            .addMigrations(*Migrations.ALL_MIGRATIONS)
            .build()
    }
    // DAO — Room sinh implementation, `get()` lấy database đã tạo ở trên
    single { get<MusicDatabase>().recentSongDao() }
    single { get<MusicDatabase>().favoriteSongDao() }
    single { get<MusicDatabase>().playlistDao() }

    // ---- Mapper Entity ↔ Model (convention mẫu: data/local/mapper) ----
    single { RecentSongMapper() }
    single { FavoriteSongMapper() }
    single { PlaylistMapper() }

    // ---- Local repository (đọc/ghi Room: nghe gần đây) ----
    single { RecentPlayedStore(get(), get()) }

    // ---- Repository chính — đăng ký theo INTERFACE (giống mẫu: singleOf + bind) ----
    // `get()` các dependency: MusicApiService (network) + FavoriteSongDao/PlaylistDao (Room)
    // + FavoriteSongMapper/PlaylistMapper (map Entity ↔ Model).
    singleOf(::MusicRepositoryImpl) bind MusicRepository::class
}
