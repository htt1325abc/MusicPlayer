package com.example.musicplayer.model

import com.google.gson.annotations.SerializedName

/**
 * Model cho response stream URL.
 *
 * Server trả về: { "128": "url", "320": "url" }
 * Trong đó:
 * - "128" = chất lượng 128kbps (miễn phí, hầu hết bài đều có)
 * - "320" = chất lượng 320kbps (VIP, nhiều bài trả null)
 *
 * TẠI SAO dùng @SerializedName?
 * → JSON key là số ("128", "320"), không đặt được tên biến Kotlin bằng số
 * → @SerializedName mapping JSON key → Kotlin property name
 */
data class StreamData(
    @SerializedName("128")
    val quality128: String?,   // Link mp3 128kbps (thường có)

    @SerializedName("320")
    val quality320: String?    // Link mp3 320kbps (VIP, có thể null)
) {
    /**
     * Lấy link stream tốt nhất có sẵn.
     * Ưu tiên 320kbps, fallback về 128kbps.
     * Trả null nếu bài hát VIP và không có link nào.
     *
     * TẠI SAO phải lọc chuỗi "VIP"?
     * → ZingMP3 (và server proxy) trả "VIP" cho bài không phát miễn phí được
     * → Nếu không lọc, app sẽ coi "VIP" là URL thật → MediaPlayer cố mở file
     *   tên "VIP" → FileNotFoundException → crash
     */
    fun getBestStreamUrl(): String? {
        val best = quality320 ?: quality128
        return if (best != null && best != "VIP") best else null
    }
}
