package com.example.musicplayer.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migrations — các bản nâng cấp schema database (giống project MẪU có `Migrations.kt`).
 *
 * TẠI SAO cần Migration thay vì xóa app cài lại?
 * → Room giữ nguyên dữ liệu cũ (nghe gần đây, yêu thích) khi tăng `version`.
 * → Nếu chỉ đổi version mà không có Migration, Room sẽ CRASH với
 *   "Room cannot verify the data integrity" khi user mở app bản mới.
 *
 * Lưu ý học tập:
 * → Migration(1, 2): "từ schema version 1 nâng lên version 2".
 * → Trong migrate() ta viết SQL thủ công (CREATE TABLE...) giống hệt cấu trúc
 *   mà Room sẽ tạo — cột phải khớp với Entity.
 */
object Migrations {

    /** version 1 → 2: thêm bảng saved_playlists (PlaylistEntity). */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS saved_playlists (" +
                    "encodeId TEXT NOT NULL PRIMARY KEY, " +
                    "title TEXT NOT NULL, " +
                    "thumbnail TEXT, " +
                    "songCount INTEGER NOT NULL, " +
                    "savedAt INTEGER NOT NULL)"
            )
        }
    }

    /** Danh sách tất cả migration — đăng ký trong RepositoryModule khi build database. */
    val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2)
}
