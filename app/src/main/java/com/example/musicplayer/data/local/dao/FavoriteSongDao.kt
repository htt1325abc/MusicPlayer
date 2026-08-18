package com.example.musicplayer.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.musicplayer.data.local.entity.FavoriteSongEntity
import kotlinx.coroutines.flow.Flow

/**
 * FavoriteSongDao — truy vấn bảng "Yêu thích".
 *
 * Các hàm cơ bản theo đúng yêu cầu:
 * - insert: thêm bài vào danh sách yêu thích
 * - getAll (observeAll): trả Flow toàn bộ bài yêu thích (mới thêm trước)
 * - getById: kiểm tra 1 bài đã được yêu thích chưa (để đổi icon trái tim)
 * - delete: bỏ yêu thích / xóa sạch
 */
@Dao
interface FavoriteSongDao {

    /** Thêm bài vào yêu thích. REPLACE → tránh trùng lặp khi thêm lại. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(song: FavoriteSongEntity)

    /** Lấy toàn bộ bài yêu thích, mới thêm trước. Flow → UI tự cập nhật. */
    @Query("SELECT * FROM favorite_songs ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<FavoriteSongEntity>>

    /** Lấy 1 bài theo id — dùng để kiểm tra đã thích chưa. */
    @Query("SELECT * FROM favorite_songs WHERE encodeId = :id")
    suspend fun getById(id: String): FavoriteSongEntity?

    /** Bỏ yêu thích 1 bài. */
    @Query("DELETE FROM favorite_songs WHERE encodeId = :id")
    suspend fun deleteById(id: String)

    /** Xóa toàn bộ danh sách yêu thích. */
    @Query("DELETE FROM favorite_songs")
    suspend fun clear()
}
