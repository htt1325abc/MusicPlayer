package com.example.musicplayer.service

/**
 * PlaybackController — interface điều khiển phát nhạc.
 *
 * TẠI SAO cần interface này?
 * → ViewModel KHÔNG nên phụ thuộc trực tiếp vào `MusicService` (Android Service)
 *   vì Service chỉ tồn tại khi Activity đã bind nó.
 * → ViewModel chỉ cần biết "có thể play/pause/next/prev" — không cần biết chi tiết
 *   Service hoạt động thế nào.
 * → Vi phạm dependency nếu để ViewModel giữ reference Service; dùng interface này
 *   giúp: dễ test (mock), dễ thay đổi implementation (VD: đổi sang Media3/ExoPlayer).
 *
 * Implementation thật là [MusicPlaybackController] (Koin singleton) — nó giữ tham
 * chiếu đến Service đã bind (do Activity gán vào khi onServiceConnected).
 *
 * Luồng gọi: UI (nút bấm) → ViewModel.next()/previous() → PlaybackController →
 * MusicService.next()/previous() → MediaPlayer chuyển bài.
 */
interface PlaybackController {

    /** Phát nhạc (nếu đang pause) */
    fun play()

    /** Tạm dừng (nếu đang phát) */
    fun pause()

    /** Bật/tắt phát — dùng cho nút play/pause */
    fun togglePlayPause()

    /** Chuyển sang bài kế tiếp trong queue */
    fun next()

    /** Chuyển về bài trước đó trong queue */
    fun previous()

    /** Service có đang phát không */
    fun isPlaying(): Boolean
}
