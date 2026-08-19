package com.example.musicplayer.data.local.mapper

import com.example.musicplayer.data.local.entities.RecentSongEntity
import com.example.musicplayer.model.SongItem

/**
 * RecentSongMapper — chuyển đổi [RecentSongEntity] ↔ [SongItem].
 *
 * TẠI SAO `toEntity` tự gán playedAt = now?
 * → Entity này chỉ được tạo khi "user vừa phát 1 bài" → thời điểm đó chính là bây giờ.
 * → Caller (RecentPlayedStore.add) không cần truyền thời gian, mapper tự lo.
 */
class RecentSongMapper : Mapper<RecentSongEntity, SongItem> {

    override fun toModel(entity: RecentSongEntity): SongItem = SongItem(
        encodeId = entity.encodeId,
        title = entity.title,
        artistsNames = entity.artistsNames,
        thumbnail = entity.thumbnail,
        thumbnailM = entity.thumbnailM,
        duration = entity.duration
    )

    override fun toEntity(model: SongItem): RecentSongEntity = RecentSongEntity(
        encodeId = model.encodeId,
        title = model.title,
        artistsNames = model.artistsNames,
        thumbnail = model.thumbnail,
        thumbnailM = model.thumbnailM,
        duration = model.duration,
        playedAt = System.currentTimeMillis()
    )
}
