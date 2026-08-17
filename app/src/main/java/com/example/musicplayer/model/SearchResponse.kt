package com.example.musicplayer.model

import com.google.gson.annotations.SerializedName

/**
 * Wrapper chung cho mọi response từ server.
 *
 * TẠI SAO dùng generic ApiResponse<T>?
 * → Mọi endpoint đều trả format: { "success": true, "data": ... }
 * → Dùng generic để 1 class bọc được mọi kiểu data khác nhau
 * → Tránh viết lặp lại wrapper cho từng endpoint
 */
data class ApiResponse<T>(
    val success: Boolean,
    val data: T?,
    val error: String? = null
)

/**
 * Model đại diện cho 1 bài hát trong kết quả tìm kiếm.
 *
 * Các field khớp với JSON server trả về (đã filter ở server/index.js).
 * Dùng data class → Kotlin tự sinh equals(), hashCode(), copy()
 * → Cần cho DiffUtil trong RecyclerView Adapter
 */
data class SongItem(
    val encodeId: String,         // ID duy nhất của bài hát trên ZingMP3
    val title: String,            // Tên bài hát
    val artistsNames: String,     // Tên ca sĩ (đã join: "Sơn Tùng M-TP, ...")
    val thumbnail: String?,       // URL ảnh nhỏ 94x94
    val thumbnailM: String?,      // URL ảnh trung 240x240
    val duration: Int             // Thời lượng bài hát (đơn vị: giây)
) {
    /**
     * Format duration từ giây sang "mm:ss"
     * Ví dụ: 262 giây → "4:22"
     */
    fun formatDuration(): String {
        val minutes = duration / 60
        val seconds = duration % 60
        return "%d:%02d".format(minutes, seconds)
    }
}
