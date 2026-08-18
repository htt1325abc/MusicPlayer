package com.example.musicplayer.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.musicplayer.data.local.entity.RecentSongEntity
import kotlinx.coroutines.flow.Flow

/**
 * RecentSongDao — truy vấn bảng "Nghe gần đây".
 *
 * Các hàm cơ bản theo đúng yêu cầu:
 * - insert/upsert (REPLACE theo encodeId — phát lại bài cũ không tạo bản trùng)
 * - getAll: trả Flow (Room tự emit lại khi data thay đổi → UI cập nhật tự động)
 * - getById: kiểm tra 1 bài cụ thể
 * - delete: xóa 1 bài hoặc xóa sạch lịch sử
 *
 * TẠI SAO trả Flow<> thay vì List<>?
 * → Flow phát ra dữ liệu MỖI KHI bảng thay đổi → ViewModel collect 1 lần,
 *   UI tự cập nhật (single source of truth) — không cần gọi lại query thủ công.
 */
@Dao
interface RecentSongDao {

    /** Thêm/sửa 1 bài. REPLACE → nếu đã có cùng encodeId thì ghi đè. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(song: RecentSongEntity)

    /** Lấy toàn bộ lịch sử, mới nhất trước, giới hạn `limit` bài. */
    @Query("SELECT * FROM recent_songs ORDER BY playedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 20): Flow<List<RecentSongEntity>>

    /** Lấy 1 bài theo id (dùng để kiểm tra tồn tại / đọc nhanh). */
    @Query("SELECT * FROM recent_songs WHERE encodeId = :id")
    suspend fun getById(id: String): RecentSongEntity?

    /** Xóa 1 bài khỏi lịch sử. */
    @Query("DELETE FROM recent_songs WHERE encodeId = :id")
    suspend fun deleteById(id: String)

    /** Xóa toàn bộ lịch sử nghe. */
    @Query("DELETE FROM recent_songs")
    suspend fun clear()
}
