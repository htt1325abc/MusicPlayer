package com.example.musicplayer.model

/**
 * Model cho bài hát trong bảng xếp hạng.
 * Kế thừa các field giống SongItem, thêm rakingStatus.
 *
 * TẠI SAO dùng typealias thay vì class mới?
 * → Chart items có cấu trúc giống hệt SongItem
 * → Dùng typealias để tránh duplicate code
 * → Nếu sau cần thêm field riêng cho chart, dễ dàng chuyển thành class mới
 */
typealias ChartData = SongItem
