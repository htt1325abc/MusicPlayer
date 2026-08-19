package com.example.musicplayer.data.local.mapper

import com.example.musicplayer.data.local.entities.PlaylistEntity
import com.example.musicplayer.model.PlaylistItem

/**
 * PlaylistMapper — chuyển đổi [PlaylistEntity] ↔ [PlaylistItem] (playlist đã lưu).
 *
 * TẠI SAO `toEntity` tự gán savedAt = now?
 * → Entity chỉ được tạo khi "user vừa bấm lưu playlist" → thời điểm đó là bây giờ.
 */
class PlaylistMapper : Mapper<PlaylistEntity, PlaylistItem> {

    override fun toModel(entity: PlaylistEntity): PlaylistItem = PlaylistItem(
        encodeId = entity.encodeId,
        title = entity.title,
        thumbnail = entity.thumbnail,
        songCount = entity.songCount
    )

    override fun toEntity(model: PlaylistItem): PlaylistEntity = PlaylistEntity(
        encodeId = model.encodeId,
        title = model.title,
        thumbnail = model.thumbnail,
        songCount = model.songCount,
        savedAt = System.currentTimeMillis()
    )
}
