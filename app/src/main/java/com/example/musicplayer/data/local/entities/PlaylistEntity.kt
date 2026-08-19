package com.example.musicplayer.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * PlaylistEntity — bảng "Playlist đã lưu" (bookmark) trong Room.
 *
 * TẠI SAO cần bảng này?
 * → User có thể bấm nút "lưu" trên 1 playlist nổi bật để xem lại sau.
 * → Dữ liệu phải SỐNG SÓT khi đóng app → lưu vào Room.
 * → `encodeId` là khóa chính (cùng ID playlist trên server).
 * → `savedAt` (thời điểm lưu) để sắp xếp "lưu gần nhất lên đầu".
 *
 * TẠI SAO map 1:1 với [PlaylistItem]?
 * → PlaylistItem là model DTO từ JSON (đổi theo server).
 * → Entity là bảng lưu trữ local — thêm field riêng (savedAt) mà không ảnh hưởng
 *   tới response API. Việc chuyển đổi nằm trong `mapper/PlaylistMapper`.
 */
@Entity(tableName = "saved_playlists")
data class PlaylistEntity(
    @PrimaryKey val encodeId: String,
    val title: String,
    val thumbnail: String?,
    val songCount: Int,
    val savedAt: Long
)
