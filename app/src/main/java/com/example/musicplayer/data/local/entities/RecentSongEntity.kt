package com.example.musicplayer.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * RecentSongEntity — bảng "Nghe gần đây" trong Room.
 *
 * Map 1:1 từ [SongItem] (model API) sang bảng SQLite:
 * → `encodeId` là khóa chính (cũng là ID bài hát trên server).
 * → Thêm `playedAt` (timestamp lúc phát) để sắp xếp mới → cũ.
 *
 * TẠI SAO tách entity riêng khỏi SongItem?
 * → SongItem là model DTO từ JSON (đổi theo server).
 * → Entity là bảng lưu trữ local — thêm được field riêng (playedAt) mà không
 *   ảnh hưởng tới response API.
 *
 * TẠI SAO KHÔNG còn hàm map ở đây (so với trước)?
 * → Theo convention project MẪU, việc Entity ↔ Model chuyển về package riêng
 *   `data/local/mapper/` (RecentSongMapper) thay vì extension function nằm chung
 *   file entity. Entity giờ chỉ "thuần dữ liệu", mapper lo phần chuyển đổi.
 */
@Entity(tableName = "recent_songs")
data class RecentSongEntity(
    @PrimaryKey val encodeId: String,
    val title: String,
    val artistsNames: String,
    val thumbnail: String?,
    val thumbnailM: String?,
    val duration: Int,
    val playedAt: Long
)
