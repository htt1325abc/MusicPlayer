package com.example.musicplayer.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.musicplayer.model.SongItem

/**
 * FavoriteSongEntity — bảng "Yêu thích" trong Room.
 *
 * Giống cấu trúc [SongItem] + thêm `addedAt` (thời điểm thêm vào yêu thích)
 * để sắp xếp "mới thêm trước".
 *
 * TẠI SAO cần bảng này?
 * → Tính năng "Yêu thích" (trái tim) phải SỐNG SÓT khi tắt app.
 * → Room cho phép truy vấn nhanh: danh sách yêu thích, kiểm tra 1 bài đã thích chưa.
 */
@Entity(tableName = "favorite_songs")
data class FavoriteSongEntity(
    @PrimaryKey val encodeId: String,
    val title: String,
    val artistsNames: String,
    val thumbnail: String?,
    val thumbnailM: String?,
    val duration: Int,
    val addedAt: Long
)

/** Chuyển Entity → model hiển thị [SongItem] */
fun FavoriteSongEntity.toSongItem(): SongItem = SongItem(
    encodeId = encodeId,
    title = title,
    artistsNames = artistsNames,
    thumbnail = thumbnail,
    thumbnailM = thumbnailM,
    duration = duration
)

/** Chuyển [SongItem] → Entity yêu thích, kèm thời điểm thêm */
fun SongItem.toFavoriteSongEntity(addedAt: Long): FavoriteSongEntity = FavoriteSongEntity(
    encodeId = encodeId,
    title = title,
    artistsNames = artistsNames,
    thumbnail = thumbnail,
    thumbnailM = thumbnailM,
    duration = duration,
    addedAt = addedAt
)
