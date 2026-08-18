package com.example.musicplayer.model

/**
 * Model cho 1 playlist/album trong màn hình Home.
 *
 * Server trả về từ endpoint GET /api/playlists (lấy từ ZingMP3 getTop100):
 * { encodeId, title, thumbnail, songCount }
 *
 * @property encodeId  ID playlist — dùng để gọi GET /api/playlist/:id lấy danh sách bài
 * @property title     Tên playlist
 * @property thumbnail URL ảnh bìa playlist
 * @property songCount Số bài hát (hiển thị "100 bài" trên card)
 */
data class PlaylistItem(
    val encodeId: String,
    val title: String,
    val thumbnail: String?,
    val songCount: Int = 0
) {
    /**
     * Format số bài hát cho UI. Ví dụ: 100 → "100 bài"
     */
    fun formatSongCount(): String = "$songCount bài"
}
