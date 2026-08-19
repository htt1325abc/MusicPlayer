package com.example.musicplayer.model

import com.google.gson.annotations.SerializedName

/**
 * Model cho chi tiết playlist.
 *
 * Nguồn dữ liệu: /albums/tracks/ của Jamendo →
 * { headers, results: [ { id, name, image, artist_name, tracks: [...] } ] }
 *
 * Field giữ NGUYÊN tên (encodeId, title, songs...) để không đụng UI:
 *   encodeId    ← Jamendo `id`
 *   title       ← Jamendo `name`
 *   thumbnail   ← Jamendo `image` (ảnh bìa album — dùng fallback cho track)
 *   artistsNames← Jamendo `artist_name` (cấp album — dùng fallback cho track)
 *   songs       ← Jamendo `tracks`
 * @SerializedName để Gson map JSON key sang tên field Kotlin.
 */
data class PlaylistData(
    // JSON `id` → encodeId
    @SerializedName("id")
    val encodeId: String,
    // JSON `name` → title
    @SerializedName("name")
    val title: String,
    // JSON `image` (ảnh bìa album) → thumbnail
    @SerializedName("image")
    val thumbnail: String? = null,
    val thumbnailM: String? = null,
    // JSON `artist_name` (cấp album) → artistsNames
    @SerializedName("artist_name")
    val artistsNames: String? = null,
    // JSON `tracks` → songs
    @SerializedName("tracks")
    val songs: List<SongItem> = emptyList()      // Danh sách bài hát trong playlist
)
