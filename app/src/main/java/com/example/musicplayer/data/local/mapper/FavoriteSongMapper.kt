package com.example.musicplayer.data.local.mapper

import com.example.musicplayer.data.local.entities.FavoriteSongEntity
import com.example.musicplayer.model.SongItem

/**
 * FavoriteSongMapper — chuyển đổi [FavoriteSongEntity] ↔ [SongItem].
 *
 * TẠI SAO `toEntity` tự gán addedAt = now?
 * → Entity chỉ được tạo khi "user vừa bấm yêu thích" → thời điểm đó là bây giờ.
 */
class FavoriteSongMapper : Mapper<FavoriteSongEntity, SongItem> {

    override fun toModel(entity: FavoriteSongEntity): SongItem = SongItem(
        encodeId = entity.encodeId,
        title = entity.title,
        artistsNames = entity.artistsNames,
        thumbnail = entity.thumbnail,
        thumbnailM = entity.thumbnailM,
        duration = entity.duration
    )

    override fun toEntity(model: SongItem): FavoriteSongEntity = FavoriteSongEntity(
        encodeId = model.encodeId,
        title = model.title,
        artistsNames = model.artistsNames,
        thumbnail = model.thumbnail,
        thumbnailM = model.thumbnailM,
        duration = model.duration,
        addedAt = System.currentTimeMillis()
    )
}
