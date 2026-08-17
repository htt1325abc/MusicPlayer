package com.example.musicplayer.model

/**
 * Model cho chi tiết playlist/album.
 *
 * Server trả về: { encodeId, title, thumbnail, songs: [...] }
 */
data class PlaylistData(
    val encodeId: String,
    val title: String,
    val thumbnail: String?,
    val thumbnailM: String?,
    val artistsNames: String?,
    val songs: List<SongItem>      // Danh sách bài hát trong playlist
)
