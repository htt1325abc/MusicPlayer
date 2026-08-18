package com.example.musicplayer.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.musicplayer.model.SongItem

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

/** Chuyển Entity → model hiển thị [SongItem] */
fun RecentSongEntity.toSongItem(): SongItem = SongItem(
    encodeId = encodeId,
    title = title,
    artistsNames = artistsNames,
    thumbnail = thumbnail,
    thumbnailM = thumbnailM,
    duration = duration
)

/** Chuyển [SongItem] → Entity để lưu vào Room, kèm thời điểm phát */
fun SongItem.toRecentSongEntity(playedAt: Long): RecentSongEntity = RecentSongEntity(
    encodeId = encodeId,
    title = title,
    artistsNames = artistsNames,
    thumbnail = thumbnail,
    thumbnailM = thumbnailM,
    duration = duration,
    playedAt = playedAt
)
