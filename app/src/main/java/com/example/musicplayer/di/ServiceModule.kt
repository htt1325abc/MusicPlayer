package com.example.musicplayer.di

import com.example.musicplayer.service.MusicPlaybackController
import com.example.musicplayer.service.PlaybackController
import org.koin.dsl.module

/**
 * serviceModule — Koin cung cấp các component tầng Service/Playback.
 *
 * `single<PlaybackController> { MusicPlaybackController() }`:
 * → Cả app dùng chung 1 controller (giữ reference đến MusicService đang bind).
 * → ⚠️ QUAN TRỌNG: khai báo theo INTERFACE `PlaybackController` (không phải class
 *   cụ thể) vì ViewModel inject `PlaybackController` (loại trừu tượng).
 *   Nếu viết `single { MusicPlaybackController() }`, Koin chỉ đăng ký type class
 *   cụ thể → khi inject interface sẽ báo `NoBeanDefFoundException` (đã từng crash).
 * → ViewModel nào cần điều khiển nhạc (next/prev/play/pause) chỉ cần inject
 *   `PlaybackController` này — không phụ thuộc trực tiếp vào Android Service.
 */
val serviceModule = module {
    // Đăng ký theo INTERFACE PlaybackController — mọi nơi inject PlaybackController
    // đều nhận cùng instance này (ViewModel inject interface, không inject class cụ thể).
    single<PlaybackController> { MusicPlaybackController() }
}
