package com.example.musicplayer.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.MediaPlayer
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.musicplayer.MainActivity
import com.example.musicplayer.R

/**
 * MusicService — Foreground Service phát nhạc nền.
 *
 * TẠI SAO dùng Foreground Service thay vì Background Service?
 * → Android 8+ (API 26) giới hạn Background Service rất nghiêm ngặt
 * → Foreground Service hiện notification → hệ thống không kill
 * → User thấy notification → biết app đang phát nhạc (UX tốt)
 *
 * TẠI SAO dùng Bound Service (Binder)?
 * → Activity cần điều khiển MediaPlayer (play/pause/stop)
 * → Binder cho phép Activity gọi trực tiếp method của Service
 * → Kết hợp: Bound (điều khiển) + Foreground (chạy nền)
 *
 * TẠI SAO dùng MediaPlayer thay vì ExoPlayer?
 * → MediaPlayer có sẵn trong Android SDK, không cần thêm dependency
 * → Đủ dùng cho đồ án học tập với streaming mp3 đơn giản
 * → ExoPlayer mạnh hơn nhưng phức tạp hơn (adaptive streaming, DRM...)
 */
class MusicService : Service() {

    companion object {
        const val CHANNEL_ID = "music_playback_channel"
        const val NOTIFICATION_ID = 1
        const val ACTION_PLAY_PAUSE = "com.example.musicplayer.PLAY_PAUSE"
        const val ACTION_STOP = "com.example.musicplayer.STOP"
    }

    // Binder instance để Activity kết nối
    private val binder = MusicBinder()

    // MediaPlayer — engine phát nhạc
    private var mediaPlayer: MediaPlayer? = null

    // Thông tin bài đang phát (hiển thị trên notification)
    private var currentTitle: String = ""
    private var currentArtist: String = ""

    // Cờ đánh dấu MediaPlayer hiện tại đã prepare xong (sẵn sàng start)
    // TẠI SAO cần? → tránh gọi start() khi player còn đang buffer (state PREPARING)
    //   → nếu gọi start() lúc đó, MediaPlayer báo lỗi -38 (INVALID_OPERATION) và hỏng
    private var isPrepared = false

    // Số thứ tự "phiên phát" — dùng để bỏ qua callback từ player CŨ (đã bị release)
    // TẠI SAO cần? → user bấm liên tục 2 bài, player cũ có thể gửi callback muộn
    //   → nếu không lọc, player cũ gọi start() trên instance đã release → crash
    private var playbackGeneration = 0

    // Callback để thông báo Activity khi trạng thái thay đổi
    var onPlaybackStateChanged: ((Boolean) -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    /**
     * Binder inner class — cho phép Activity lấy reference đến Service
     */
    inner class MusicBinder : Binder() {
        fun getService(): MusicService = this@MusicService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    /**
     * Xử lý intent từ notification buttons (Play/Pause, Stop)
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY_PAUSE -> togglePlayPause()
            ACTION_STOP -> {
                stopPlayback()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY // Không tự restart nếu bị system kill
    }

    /**
     * Phát nhạc từ URL stream.
     *
     * TẠI SAO dùng prepareAsync() thay vì prepare()?
     * → prepare() block Main Thread → app đơ khi buffer nhạc từ internet
     * → prepareAsync() chạy background, gọi callback khi sẵn sàng
     */
    fun playFromUrl(url: String, title: String, artist: String) {
        currentTitle = title
        currentArtist = artist

        // Tăng số phiên phát → mọi callback của player cũ sẽ bị bỏ qua
        playbackGeneration++
        val generation = playbackGeneration

        // Release player cũ nếu đang phát bài khác
        mediaPlayer?.release()
        mediaPlayer = null

        // Player mới CHƯA prepare xong → chưa cho phép start()
        isPrepared = false

        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(url)

                // Callback khi đã buffer xong → bắt đầu phát
                setOnPreparedListener {
                    // Bỏ qua callback từ player cũ (đã bị thay thế bằng bài mới)
                    if (generation != playbackGeneration) {
                        release()
                        return@setOnPreparedListener
                    }
                    isPrepared = true
                    start()
                    onPlaybackStateChanged?.invoke(true)
                    // Bắt đầu foreground service với notification
                    startForeground(NOTIFICATION_ID, buildNotification(true))
                }

                // Callback khi phát xong bài → cập nhật UI
                setOnCompletionListener {
                    if (generation != playbackGeneration) return@setOnCompletionListener
                    onPlaybackStateChanged?.invoke(false)
                    updateNotification(false)
                }

                // Callback khi có lỗi (URL hết hạn, network...)
                setOnErrorListener { _, what, extra ->
                    if (generation != playbackGeneration) return@setOnErrorListener true
                    isPrepared = false
                    onError?.invoke("Lỗi phát nhạc (code: $what/$extra)")
                    onPlaybackStateChanged?.invoke(false)
                    true // true = đã xử lý lỗi, không throw exception
                }

                // Bắt đầu buffer từ URL (non-blocking)
                prepareAsync()
            }
        } catch (e: Exception) {
            // TẠI SAO cần try-catch?
            // → setDataSource ném FileNotFoundException/IllegalArgumentException
            //   nếu URL không hợp lệ (ví dụ URL hết hạn, bài VIP chưa được lọc)
            // → Nếu không bắt, exception ném lên main thread → app CRASH
            // → Bắt tại đây + thông báo lỗi cho Activity là an toàn nhất
            onError?.invoke("Không thể phát: ${e.message}")
            onPlaybackStateChanged?.invoke(false)
        }
    }

    /**
     * Toggle play/pause
     *
     * TẠI SAO cần kiểm tra isPrepared trước khi start()?
     * → Nếu bấm play/pause NGAY trong lúc bài đang buffer (chưa prepare xong),
     *   gọi player.start() sẽ báo lỗi "start called in state 4" (error -38)
     *   → MediaPlayer hỏng, không phát được tiếng (đã từng gặp)
     * → Nếu chưa sẵn sàng thì BỎ QUA lượt bấm — bài sẽ TỰ ĐỘNG phát
     *   khi prepare xong nhờ onPreparedListener
     */
    fun togglePlayPause() {
        mediaPlayer?.let { player ->
            // Player chưa prepare xong → bỏ qua (tránh error -38)
            if (!isPrepared) return

            if (player.isPlaying) {
                player.pause()
                onPlaybackStateChanged?.invoke(false)
                updateNotification(false)
            } else {
                player.start()
                onPlaybackStateChanged?.invoke(true)
                updateNotification(true)
            }
        }
    }

    /**
     * Kiểm tra đang phát hay không
     */
    fun isPlaying(): Boolean = mediaPlayer?.isPlaying == true

    /**
     * Dừng phát nhạc hoàn toàn
     */
    fun stopPlayback() {
        mediaPlayer?.apply {
            if (isPlaying) stop()
            release()
        }
        mediaPlayer = null
        isPrepared = false
        onPlaybackStateChanged?.invoke(false)
    }

    /**
     * Tạo notification channel (bắt buộc từ Android 8+).
     * Channel tạo 1 lần duy nhất, gọi nhiều lần không sao.
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Phát nhạc",
                NotificationManager.IMPORTANCE_LOW // LOW = không phát âm thanh notification
            ).apply {
                description = "Thông báo khi đang phát nhạc"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    /**
     * Build notification hiển thị bài đang phát + nút điều khiển
     */
    private fun buildNotification(isPlaying: Boolean): Notification {
        // PendingIntent mở app khi bấm notification
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // PendingIntent cho nút Play/Pause
        val playPauseIntent = Intent(this, MusicService::class.java).apply {
            action = ACTION_PLAY_PAUSE
        }
        val playPausePendingIntent = PendingIntent.getService(
            this, 1, playPauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // PendingIntent cho nút Stop
        val stopIntent = Intent(this, MusicService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 2, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseIcon = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        val playPauseText = if (isPlaying) "Tạm dừng" else "Phát"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(currentTitle)
            .setContentText(currentArtist)
            .setSmallIcon(R.drawable.ic_music_note)
            .setContentIntent(openAppPendingIntent)
            .setOngoing(isPlaying) // ongoing = không vuốt dismiss được khi đang phát
            .addAction(playPauseIcon, playPauseText, playPausePendingIntent)
            .addAction(R.drawable.ic_stop, "Dừng", stopPendingIntent)
            // Không dùng MediaStyle để tránh thêm dependency androidx.media
            // Notification vẫn hiện đầy đủ nút play/pause + stop
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    /**
     * Cập nhật notification khi trạng thái thay đổi (play ↔ pause)
     */
    private fun updateNotification(isPlaying: Boolean) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(isPlaying))
    }

    /**
     * Cleanup khi Service bị destroy
     */
    override fun onDestroy() {
        mediaPlayer?.release()
        mediaPlayer = null
        super.onDestroy()
    }
}
