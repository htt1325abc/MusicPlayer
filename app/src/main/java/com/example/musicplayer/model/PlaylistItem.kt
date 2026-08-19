package com.example.musicplayer.model

import com.google.gson.annotations.SerializedName

/**
 * Model cho 1 playlist/album trong màn hình Home.
 *
 * Nguồn dữ liệu: endpoint /albums của Jamendo → { id, name, image, ... }
 * Field giữ NGUYÊN tên để không đụng UI/Room:
 *   encodeId  ← Jamendo `id` — dùng để gọi /albums/tracks/?id= lấy danh sách bài
 *   title     ← Jamendo `name`
 *   thumbnail ← Jamendo `image` (ảnh bìa album — có thật!)
 *   songCount ← Không có trong response → 0 (UI ẩn "x bài" khi 0)
 * @SerializedName để Gson map JSON key (id/name/image) sang tên field Kotlin.
 */
data class PlaylistItem(
    // JSON `id` → encodeId
    @SerializedName("id")
    val encodeId: String,
    // JSON `name` → title
    @SerializedName("name")
    val title: String,
    // JSON `image` (ảnh bìa album) → thumbnail
    @SerializedName("image")
    val thumbnail: String?,
    val songCount: Int = 0
) {
    /**
     * Format số bài hát cho UI. Ví dụ: 100 → "100 bài".
     * Trả chuỗi rỗng khi không biết số bài (album list của Jamendo không trả).
     */
    fun formatSongCount(): String = if (songCount > 0) "$songCount bài" else ""
}
