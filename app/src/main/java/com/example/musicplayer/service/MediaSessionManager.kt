package com.example.musicplayer.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle
import com.bumptech.glide.Glide
import com.example.musicplayer.R
import com.example.musicplayer.presenter.search.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * MediaStateProvider — giao diện để [MediaSessionManager] đọc trạng thái phát
 * từ [MusicService] mà KHÔNG phụ thuộc trực tiếp vào class (tránh vòng phụ thuộc).
 *
 * TẠI SAO cần interface này?
 * → Manager chỉ cần biết "bài đang phát gì, đang chạy hay pause, vị trí bao nhiêu"
 * → Service implement nó → Manager gọi qua interface → dễ test, dễ thay đổi.
 */
interface MediaStateProvider {
    /** Đang phát hay không */
    fun isPlaying(): Boolean

    /** Vị trí phát hiện tại (ms) — cho PlaybackState + progress trên lock screen */
    fun getCurrentPositionMs(): Long

    /** Tổng thời lượng bài (ms) */
    fun getDurationMs(): Long

    /** Tên bài đang phát */
    fun getTitle(): String

    /** Nghệ sĩ đang phát */
    fun getArtist(): String

    /** URL ảnh album (có thể null nếu bài không có ảnh) */
    fun getArtUrl(): String?

    /** Yêu cầu Service build notification mới + cập nhật lên foreground service */
    fun refreshNotification()
}

/**
 * MediaSessionManager — đóng gói MediaSessionCompat + build Notification (MediaStyle).
 *
 * NHIỆM VỤ:
 * 1. Tạo & quản lý [MediaSessionCompat] → hệ thống (lock screen, tai nghe, Bluetooth)
 *    "nhìn thấy" app đang phát nhạc và gửi lệnh điều khiển về qua callback.
 * 2. Cập nhật [PlaybackStateCompat] (STATE_PLAYING / STATE_PAUSED + actions) mỗi khi
 *    trạng thái phát thay đổi.
 * 3. Cập nhật [MediaMetadataCompat] (title/artist/album art) cho bài đang phát.
 * 4. Build notification MediaStyle với 3 nút: Previous - Play/Pause - Next.
 *
 * TẠI SAO tách ra class riêng (không nhét hết vào Service)?
 * → MusicService đã dài (queue, MediaPlayer, auto-advance...). Tách phần "giao tiếp
 *   với hệ thống" ra 1 class chuyên trách → Service ngắn gọn, dễ đọc, dễ test.
 */
class MediaSessionManager(
    private val context: Context,
    private val provider: MediaStateProvider,
    private val onMediaAction: (String) -> Unit
) {

    // ---- MediaSessionCompat: "cửa sổ" để hệ thống điều khiển app ----
    // isActive = true → hệ thống biết session đang "sống" → gửi media button
    // (lock screen, nút bấm tai nghe, Bluetooth) vào callback bên dưới.
    val session: MediaSessionCompat = MediaSessionCompat(context, "MusicService").apply {
        setCallback(object : MediaSessionCompat.Callback() {
            override fun onPlay() = onMediaAction(MusicService.ACTION_PLAY)
            override fun onPause() = onMediaAction(MusicService.ACTION_PAUSE)
            override fun onSkipToNext() = onMediaAction(MusicService.ACTION_NEXT)
            override fun onSkipToPrevious() = onMediaAction(MusicService.ACTION_PREVIOUS)
            override fun onStop() = onMediaAction(MusicService.ACTION_STOP)
        })
        isActive = true
    }

    // Ảnh album đã load xong (bitmap) → set vào large icon của notification
    private var artBitmap: Bitmap? = null

    // Coroutine scope riêng — dùng để load ảnh album ở background (không block main)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Handler + Runnable: cập nhật PlaybackState mỗi 1 giây khi đang phát
    // → lock screen / notification hiện progress đếm đúng theo thời gian thực.
    private val mainHandler = Handler(Looper.getMainLooper())
    private var positionUpdaterRunning = false
    private val positionUpdater = object : Runnable {
        override fun run() {
            updatePlaybackState()
            if (provider.isPlaying()) {
                mainHandler.postDelayed(this, 1000L)
            } else {
                positionUpdaterRunning = false
            }
        }
    }

    // Số thứ tự lần load ảnh — bỏ qua kết quả của ảnh CŨ khi user chuyển bài nhanh
    private var artGeneration = 0

    // ================== PUBLIC API (MusicService gọi) ==================

    /**
     * Cập nhật PlaybackStateCompat theo trạng thái thực tế của MediaPlayer.
     * Gọi mỗi khi: play/pause/next/prev hoặc khi cần đồng bộ vị trí phát.
     */
    fun updatePlaybackState() {
        val isPlaying = provider.isPlaying()
        val state = if (isPlaying) PlaybackStateCompat.STATE_PLAYING
                    else PlaybackStateCompat.STATE_PAUSED

        val playbackState = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                    PlaybackStateCompat.ACTION_PAUSE or
                    PlaybackStateCompat.ACTION_PLAY_PAUSE or
                    PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                    PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                    PlaybackStateCompat.ACTION_STOP
            )
            .setState(state, provider.getCurrentPositionMs(), if (isPlaying) 1f else 0f)
            .build()
        session.setPlaybackState(playbackState)

        // Bật vòng lặp cập nhật position nếu đang phát (tự tắt khi pause)
        if (isPlaying && !positionUpdaterRunning) {
            positionUpdaterRunning = true
            mainHandler.postDelayed(positionUpdater, 1000L)
        }
    }

    /**
     * Cập nhật MediaMetadataCompat (title/artist/album art) theo bài đang phát.
     * Set text ngay lập tức; ảnh album load ở background, có ảnh thì set bitmap
     * vào metadata + rebuild notification (giống Spotify hiện ảnh nghệ sĩ).
     */
    fun updateMetadata() {
        artGeneration++
        artBitmap = null // xóa ảnh cũ của bài trước → tránh hiện nhầm ảnh

        val title = provider.getTitle()
        val artist = provider.getArtist()
        val artUrl = provider.getArtUrl()

        setMediaMetadata(title, artist, artUrl, null)
        provider.refreshNotification()

        // Load ảnh album ở background — KHÔNG chặn main thread
        val generation = artGeneration
        scope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                try {
                    artUrl?.let {
                        Glide.with(context).asBitmap().load(it).submit().get()
                    }
                } catch (e: Exception) {
                    null // URL lỗi/hết hạn → bỏ qua, không crash
                }
            }
            // Chỉ áp dụng nếu user chưa chuyển sang bài khác
            if (generation == artGeneration && bitmap != null) {
                artBitmap = bitmap
                setMediaMetadata(title, artist, artUrl, bitmap)
                provider.refreshNotification()
            }
        }
    }

    /**
     * Build notification MediaStyle với 3 nút: Previous - Play/Pause - Next.
     * Icon play/pause tự đổi theo trạng thái phát hiện tại.
     */
    fun buildNotification(): Notification {
        // Bấm vào thân notification → mở app
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            context, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // PendingIntent cho 3 nút điều khiển + nút X (dừng hẳn)
        // LƯU Ý: mỗi action cần requestCode RIÊNG vì dùng FLAG_IMMUTABLE
        val prevPendingIntent = mediaActionPendingIntent(MusicService.ACTION_PREVIOUS, 100)
        val playPausePendingIntent = mediaActionPendingIntent(MusicService.ACTION_PLAY_PAUSE, 101)
        val nextPendingIntent = mediaActionPendingIntent(MusicService.ACTION_NEXT, 102)
        val stopPendingIntent = mediaActionPendingIntent(MusicService.ACTION_STOP, 103)

        val isPlaying = provider.isPlaying()
        val playPauseIcon = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        val playPauseText = if (isPlaying) "Tạm dừng" else "Phát"

        val style = MediaStyle()
            .setMediaSession(session.sessionToken)
            // Hiện đủ 3 nút Previous/PlayPause/Next ngay cả ở dạng thu gọn
            .setShowActionsInCompactView(0, 1, 2)
            // Nút X ở dạng mở rộng → dừng hẳn (tái dùng ACTION_STOP)
            .setCancelButtonIntent(stopPendingIntent)
        // LƯU Ý: KHÔNG cần setMediaButtonReceiver — MediaSessionCompat đang ACTIVE
        // sẽ tự nhận media button (lock screen / tai nghe / Bluetooth) qua hệ thống.

        return NotificationCompat.Builder(context, MusicService.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_music_note)
            .setContentTitle(provider.getTitle())
            .setContentText(provider.getArtist())
            .setLargeIcon(artBitmap)
            .setContentIntent(openAppPendingIntent)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(isPlaying) // đang phát → không vuốt dismiss được
            .setOnlyAlertOnce(true) // không rung/lại âm thanh mỗi lần cập nhật
            .addAction(R.drawable.ic_prev, "Bài trước", prevPendingIntent)
            .addAction(playPauseIcon, playPauseText, playPausePendingIntent)
            .addAction(R.drawable.ic_next, "Bài sau", nextPendingIntent)
            .setStyle(style)
            .build()
    }

    /** Giải phóng session + scope khi Service bị destroy */
    fun release() {
        mainHandler.removeCallbacks(positionUpdater)
        session.isActive = false
        session.release()
        scope.cancel()
    }

    // ================== PRIVATE ==================

    /** Tạo PendingIntent trỏ tới MusicService.onStartCommand với action tương ứng */
    private fun mediaActionPendingIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, MusicService::class.java).apply {
            this.action = action
        }
        return PendingIntent.getService(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** Gán metadata (title/artist/duration/album art) lên MediaSessionCompat */
    private fun setMediaMetadata(title: String, artist: String, artUrl: String?, bitmap: Bitmap?) {
        val builder = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, provider.getDurationMs())

        if (artUrl != null) {
            // URI để hệ thống (lock screen API 26+) tự load ảnh
            builder.putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, artUrl)
            builder.putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI, artUrl)
        }
        if (bitmap != null) {
            builder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, bitmap)
            builder.putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, bitmap)
        }
        session.setMetadata(builder.build())
    }
}
