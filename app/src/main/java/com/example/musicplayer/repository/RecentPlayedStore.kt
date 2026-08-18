package com.example.musicplayer.repository

import com.example.musicplayer.data.local.dao.RecentSongDao
import com.example.musicplayer.data.local.entity.toRecentSongEntity
import com.example.musicplayer.data.local.entity.toSongItem
import com.example.musicplayer.model.SongItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * RecentPlayedStore — nơi lưu danh sách "Nghe gần đây".
 *
 * TẠI SAO cần class này?
 * → Màn hình Home có section "Nghe gần đây" — cần biết user đã nghe bài nào.
 * → Dữ liệu phải SỐNG SÓT khi đóng app → lưu vào ROOM (bảng `recent_songs`).
 * → ViewModel (HomeViewModel) quan sát Flow; Activity (khi phát bài) ghi vào.
 *
 * ⚠️ PHẦN 2 (Room): trước đây lưu SharedPreferences (JSON), GIỜ dùng Room —
 * query SQL, trả Flow để UI tự cập nhật (single source of truth).
 *
 * TẠI SAO là Koin singleton (`single {}`)?
 * → Koin `get()` bơm `RecentSongDao` (1 instance, cùng 1 bảng Room).
 * → Mọi nơi trong app ghi/đọc trên cùng 1 dữ liệu, không lệch nhau.
 */
class RecentPlayedStore(
    private val dao: RecentSongDao
) {

    /** Số bài tối đa giữ trong lịch sử */
    private val maxItems = 20

    /**
     * Quan sát lịch sử "Nghe gần đây" (mới nhất trước).
     * Trả [Flow] — mỗi khi có bài mới phát, Room tự emit lại → UI tự cập nhật,
     * không cần Activity phải tự gọi lại query (single source of truth).
     */
    fun observeRecent(): Flow<List<SongItem>> =
        dao.observeRecent(limit = maxItems)
            .map { entities -> entities.map { it.toSongItem() } }

    /**
     * Đọc nhanh 1 lần (snapshot) — dùng khi cần danh sách tức thì mà không cần quan sát.
     */
    suspend fun getRecentSnapshot(): List<SongItem> =
        dao.observeRecent(limit = maxItems).first().map { it.toSongItem() }

    /**
     * Thêm 1 bài vào lịch sử (suspend vì ghi Room ở background thread).
     * Room REPLACE theo encodeId → phát lại bài cũ chỉ cập nhật playedAt, không trùng.
     */
    suspend fun add(song: SongItem) {
        dao.upsert(song.toRecentSongEntity(playedAt = System.currentTimeMillis()))
    }
}
