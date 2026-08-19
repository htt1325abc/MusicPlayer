package com.example.musicplayer.model

import com.google.gson.annotations.SerializedName

/**
 * Wrapper response của JAMENDO API.
 *
 * Khác với local server cũ ({ "success": true, "data": ... }), Jamendo trả:
 * { "headers": { "status": "success", "code": 0, "error_message": "" }, "results": [...] }
 *
 * TẠI SAO dùng generic JamendoResponse<T>?
 * → Mọi endpoint của Jamendo đều có chung khung: headers + results
 * → Dùng generic để 1 class bọc được mọi kiểu data khác nhau (Track/Playlist...)
 * → Tránh viết lặp lại wrapper cho từng endpoint
 */
data class JamendoHeaders(
    val status: String,             // "success" | "failed"
    val code: Int,                  // 0 = thành công
    val error_message: String? = null,
    val warnings: String? = null,
    val results_count: Int? = null
)

data class JamendoResponse<T>(
    val headers: JamendoHeaders,
    val results: List<T> = emptyList()
) {
    /** Kiểm tra request thành công: status == "success" VÀ code == 0 */
    val isSuccess: Boolean
        get() = headers.status == "success" && headers.code == 0
}

/**
 * Model đại diện cho 1 bài hát.
 *
 * Các field giữ NGUYÊN tên (encodeId, title, artistsNames...) để KHÔNG phải
 * sửa Room/Adapter/UI — chỉ thay nguồn map sang Jamendo:
 *   encodeId     ← Jamendo `id`
 *   title        ← Jamendo `name`
 *   artistsNames ← Jamendo `artist_name`
 *   thumbnail    ← Jamendo `image` (dùng chung URL)
 *   thumbnailM   ← Jamendo `image` (dùng chung URL)
 *   duration     ← Jamendo `duration` (giây) ✓ giống nhau
 *   audio        ← Jamendo `audio` (URL stream mp3 — RIÊNG cho getStreamUrl)
 *
 * ⚠️ TẠI SAO cần @SerializedName?
 * → Gson map JSON theo TÊN FIELD. Tên field Jamendo (id, name, artist_name, image)
 *   KHÁC tên field Kotlin (encodeId, title, artistsNames, thumbnail).
 * → @SerializedName khai báo "JSON key ↔ tên thuộc tính Kotlin" để Gson map đúng.
 *   Nếu thiếu, field sẽ nhận null → UI vỡ, không phát được nhạc.
 *
 * ⚠️⚠️ LƯU Ý GSON 2.11: KHÔNG được để 2 field cùng map 1 JSON key.
 * → Nếu cả thumbnail lẫn thumbnailM cùng @SerializedName("image") thì Gson
 *   ném "declares multiple JSON fields named 'image'" (lỗi đã gặp 2026-08-19).
 * → Jamendo chỉ trả 1 ảnh `image` → chỉ thumbnail map sang image, thumbnailM = null
 *   (mọi UI dùng `thumbnailM ?: thumbnail` nên vẫn hiện ảnh đúng).
 *
 * Dùng data class → Kotlin tự sinh equals(), hashCode(), copy()
 * → Cần cho DiffUtil trong RecyclerView Adapter
 */
data class SongItem(
    // JSON `id` → encodeId
    @SerializedName("id")
    val encodeId: String,          // ID duy nhất của bài hát trên Jamendo
    // JSON `name` → title
    @SerializedName("name")
    val title: String,             // Tên bài hát
    // JSON `artist_name` → artistsNames
    @SerializedName("artist_name")
    val artistsNames: String,      // Tên nghệ sĩ
    // JSON `image` → thumbnail (ảnh cover — Jamendo chỉ trả 1 ảnh)
    @SerializedName("image")
    val thumbnail: String?,
    // KHÔNG map JSON (tránh duplicate "image") — luôn null từ network,
    // UI dùng `thumbnailM ?: thumbnail` nên fallback về thumbnail. Giữ field
    // này để Room (favorites/recent cũ) vẫn đọc được ảnh đã lưu.
    val thumbnailM: String? = null,
    // JSON `duration` → duration (tên trùng → không cần annotation)
    val duration: Int,             // Thời lượng bài hát (đơn vị: giây)
    // JSON `audio` → audio (tên trùng → không cần annotation)
    val audio: String? = null
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
