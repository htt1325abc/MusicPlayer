package com.example.musicplayer.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.musicplayer.data.local.entities.PlaylistEntity
import kotlinx.coroutines.flow.Flow

/**
 * PlaylistDao — truy vấn bảng "Playlist đã lưu" (bookmark).
 *
 * Các hàm cơ bản (giống pattern các DAO khác trong project):
 * - insert: thêm playlist vào danh sách đã lưu (REPLACE → không trùng encodeId)
 * - observeAll: trả Flow toàn bộ playlist đã lưu (lưu gần nhất trước)
 * - getById: kiểm tra 1 playlist đã lưu chưa (để đổi icon bookmark)
 * - deleteById: bỏ lưu / xóa sạch
 *
 * TẠI SAO trả Flow<> thay vì List<>?
 * → Flow phát ra dữ liệu MỖI KHI bảng thay đổi → ViewModel collect 1 lần,
 *   UI tự cập nhật (single source of truth) — không cần gọi lại query thủ công.
 */
@Dao
interface PlaylistDao {

    /** Thêm playlist vào danh sách đã lưu. REPLACE → tránh trùng lặp khi lưu lại. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(playlist: PlaylistEntity)

    /** Lấy toàn bộ playlist đã lưu, lưu gần nhất trước. */
    @Query("SELECT * FROM saved_playlists ORDER BY savedAt DESC")
    fun observeAll(): Flow<List<PlaylistEntity>>

    /** Lấy 1 playlist theo id — dùng để kiểm tra đã lưu chưa. */
    @Query("SELECT * FROM saved_playlists WHERE encodeId = :id")
    suspend fun getById(id: String): PlaylistEntity?

    /** Bỏ lưu 1 playlist. */
    @Query("DELETE FROM saved_playlists WHERE encodeId = :id")
    suspend fun deleteById(id: String)

    /** Xóa toàn bộ playlist đã lưu. */
    @Query("DELETE FROM saved_playlists")
    suspend fun clear()
}
