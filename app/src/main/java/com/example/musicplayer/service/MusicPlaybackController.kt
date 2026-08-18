package com.example.musicplayer.service

/**
 * MusicPlaybackController — implementation của [PlaybackController].
 *
 * TẠI SAO là Koin singleton (`single { }`) mà không phải object?
 * → Cần ViewModel (qua Koin) inject được → `single` là chuẩn DI của project.
 * → Giữ tham chiếu `service` đến MusicService ĐANG ĐƯỢC BIND (có thể null nếu
 *   chưa bind hoặc đã unbind) — Activity gán vào khi `onServiceConnected`.
 *
 * TẠI SAO dùng var service (nullable) thay vì tạo Service ở đây?
 * → MusicService là Bound Service gắn với vòng đời Activity (bind/unbind).
 * → Controller chỉ là "cầu nối" — mọi method ủy quyền cho Service nếu có sẵn.
 * → Service = null (chưa bind) → các lệnh bấm nút đều no-op an toàn, không crash.
 *
 * LƯU Ý: không giữ Activity/Context ở đây → không lo rò rỉ memory.
 */
class MusicPlaybackController : PlaybackController {

    /**
     * Service đang bind (do Activity gán khi connected, xóa khi disconnected).
     * `@Volatile` vì có thể được gán từ nhiều thread (Binder callback).
     */
    @Volatile
    var service: MusicService? = null

    // Dùng block body (không phải expression) để trả đúng Unit,
    // vì `service?.play()` có thể trả Unit? khi service = null.
    override fun play() {
        service?.play()
    }

    override fun pause() {
        service?.pause()
    }

    override fun togglePlayPause() {
        service?.togglePlayPause()
    }

    override fun next() {
        service?.next()
    }

    override fun previous() {
        service?.previous()
    }

    override fun isPlaying(): Boolean = service?.isPlaying() == true
}
