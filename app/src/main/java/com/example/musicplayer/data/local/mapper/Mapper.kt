package com.example.musicplayer.data.local.mapper

/**
 * Mapper — interface chuyển đổi giữa Entity (bảng Room) và Model (domain/UI),
 * học theo `data/local/mapper/Mapper.kt` của project MẪU.
 *
 * TẠI SAO cần interface này?
 * → Ép mọi mapper implement đủ 2 chiều: Entity → Model và Model → Entity.
 * → Đúng convention của mẫu: logic map không nằm lẫn trong Entity hay ViewModel,
 *   mà nằm gọn trong package `mapper/` — dễ test, dễ đọc.
 *
 * @param Entity Kiểu entity trong Room (VD: RecentSongEntity).
 * @param Model  Kiểu model dùng ở tầng trên (VD: SongItem).
 */
interface Mapper<Entity, Model> {
    fun toModel(entity: Entity): Model
    fun toEntity(model: Model): Entity
}
