package com.example.musicplayer.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.musicplayer.data.local.dao.FavoriteSongDao
import com.example.musicplayer.data.local.dao.RecentSongDao
import com.example.musicplayer.data.local.entity.FavoriteSongEntity
import com.example.musicplayer.data.local.entity.RecentSongEntity

/**
 * MusicDatabase — RoomDatabase của app.
 *
 * TẠI SAO là abstract class + Room sinh implementation?
 * → Room đọc annotation @Database/@Entity/@Dao lúc build (qua KSP) và sinh
 *   class impl tự động → ta chỉ cần khai báo DAO, không cần viết SQLite thủ công.
 *
 * Bảng hiện tại:
 * - recent_songs  → lịch sử "Nghe gần đây"
 * - favorite_songs → danh sách "Yêu thích"
 *
 * `exportSchema = false` → không xuất file schema JSON (đủ cho đồ án học tập).
 * Khi nâng cấp database sau này nhớ tăng `version` + viết Migration.
 */
@Database(
    entities = [
        RecentSongEntity::class,
        FavoriteSongEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class MusicDatabase : RoomDatabase() {
    abstract fun recentSongDao(): RecentSongDao
    abstract fun favoriteSongDao(): FavoriteSongDao
}
