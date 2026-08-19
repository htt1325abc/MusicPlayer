package com.example.musicplayer.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * FavoriteSongEntity — bảng "Yêu thích" trong Room.
 *
 * Giống cấu trúc [SongItem] + thêm `addedAt` (thời điểm thêm vào yêu thích)
 * để sắp xếp "mới thêm trước".
 *
 * TẠI SAO cần bảng này?
 * → Tính năng "Yêu thích" (trái tim) phải SỐNG SÓT khi tắt app.
 * → Room cho phép truy vấn nhanh: danh sách yêu thích, kiểm tra 1 bài đã thích chưa.
 *
 * TẠI SAO KHÔNG còn hàm map ở đây (so với trước)?
 * → Chuyển đổi Entity ↔ Model nằm trong `data/local/mapper/FavoriteSongMapper`
 *   theo convention project MẪU.
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
