package com.example.musicplayer.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.musicplayer.data.local.dao.FavoriteSongDao
import com.example.musicplayer.data.local.dao.PlaylistDao
import com.example.musicplayer.data.local.dao.RecentSongDao
import com.example.musicplayer.data.local.entities.FavoriteSongEntity
import com.example.musicplayer.data.local.entities.PlaylistEntity
import com.example.musicplayer.data.local.entities.RecentSongEntity

/**
 * MusicDatabase — RoomDatabase của app.
 *
 * TẠI SAO là abstract class + Room sinh implementation?
 * → Room đọc annotation @Database/@Entity/@Dao lúc build (qua KSP) và sinh
 *   class impl tự động → ta chỉ cần khai báo DAO, không cần viết SQLite thủ công.
 *
 * Bảng hiện tại:
 * - recent_songs    → lịch sử "Nghe gần đây"
 * - favorite_songs  → danh sách "Yêu thích"
 * - saved_playlists → playlist "đã lưu" (bookmark) — thêm ở version 2
 *
 * `exportSchema = false` → không xuất file schema JSON (đủ cho đồ án học tập).
 * Khi nâng cấp database sau này nhớ tăng `version` + viết Migration
 * (xem `data/local/Migrations.kt`).
 */
@Database(
    entities = [
        RecentSongEntity::class,
        FavoriteSongEntity::class,
        PlaylistEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class MusicDatabase : RoomDatabase() {
    abstract fun recentSongDao(): RecentSongDao
    abstract fun favoriteSongDao(): FavoriteSongDao
    abstract fun playlistDao(): PlaylistDao

    companion object {
        const val DATABASE_NAME = "music_player.db"
    }
}
