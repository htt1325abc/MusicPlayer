package com.example.musicplayer.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.media.MediaPlayer
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.media.session.MediaButtonReceiver
import com.example.musicplayer.domain.repository.MusicRepository
import com.example.musicplayer.model.SongItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

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
class MusicService : Service(), KoinComponent, MediaStateProvider {

    companion object {
        // Tag dùng chung để lọc logcat: `adb logcat -s MusicService`
        private const val TAG = "MusicService"

        const val CHANNEL_ID = "music_playback_channel"
        const val NOTIFICATION_ID = 1

        // Các action từ nút bấm trên notification / media button (tai nghe, Bluetooth)
        const val ACTION_PLAY = "com.example.musicplayer.PLAY"
        const val ACTION_PAUSE = "com.example.musicplayer.PAUSE"
        const val ACTION_PLAY_PAUSE = "com.example.musicplayer.PLAY_PAUSE"
        const val ACTION_NEXT = "com.example.musicplayer.NEXT"
        const val ACTION_PREVIOUS = "com.example.musicplayer.PREVIOUS"
        const val ACTION_STOP = "com.example.musicplayer.STOP"
    }

    // Binder instance để Activity kết nối
    private val binder = MusicBinder()

    // Repository để service TỰ lấy URL bài hát khi auto-advance (chạy nền).
    // TẠI SAO service cần repository?
    // → Khi bài A phát xong, service phải tự lấy URL bài B rồi phát tiếp.
    // → Nếu đợi Activity lấy URL thì khi app ở background/khóa màn hình
    //   Activity có thể không còn sống → nhạc ngừng giữa chừng.
    // → KoinComponent giúp service `by inject()` MusicRepository (Koin đã start ở App).
    private val repository: MusicRepository by inject()

    // CoroutineScope riêng cho service — dùng để lấy URL stream khi auto-advance
    private val playbackScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // ---- Hàng đợi phát nhạc (queue) ----
    // TẠI SAO cần queue?
    // → Trước đây phát 1 bài xong là DỪNG (bài "Dấu Chân..." hết 5:35 là hết nhạc).
    // → Giờ khi user bấm 1 bài trong playlist/list, service lưu cả danh sách + vị trí.
    // → Bài nào phát xong → TỰ ĐỘNG phát bài kế tiếp trong danh sách (như app nhạc thật).
    private var queue: List<SongItem> = emptyList()
    private var currentIndex = -1

    // Bài đang phát hiện tại (cho UI mini player cập nhật khi tự chuyển bài)
    var currentSong: SongItem? = null
        private set

    // Callback khi TỰ ĐỘNG chuyển sang bài mới (auto-advance) → Activity cập nhật mini player
    var onSongChanged: ((SongItem) -> Unit)? = null

    // MediaPlayer — engine phát nhạc
    private var mediaPlayer: MediaPlayer? = null

    // Thông tin bài đang phát (hiển thị trên notification)
    private var currentTitle: String = ""
    private var currentArtist: String = ""
    private var currentArtUrl: String? = null

    // MediaSessionManager — quản lý MediaSessionCompat + notification (MediaStyle).
    // Tạo trong onCreate, giải phóng trong onDestroy.
    private lateinit var mediaSessionManager: MediaSessionManager

    // Cờ đánh dấu MediaPlayer hiện tại đã prepare xong (sẵn sàng start)
    // TẠI SAO cần? → tránh gọi start() khi player còn đang buffer (state PREPARING)
    //   → nếu gọi start() lúc đó, MediaPlayer báo lỗi -38 (INVALID_OPERATION) và hỏng
    private var isPrepared = false

    // Số thứ tự "phiên phát" — dùng để bỏ qua callback từ player CŨ (đã bị release)
    // TẠI SAO cần? → user bấm liên tục 2 bài, player cũ có thể gửi callback muộn
    //   → nếu không lọc, player cũ gọi start() trên instance đã release → crash
    private var playbackGeneration = 0

    // Cờ chặn bấm Next/Previous LIÊN TỤC trong lúc đang fetch URL bài mới.
    // TẠI SAO cần? → bấm 2 lần quá nhanh, 2 coroutine fetch URL chạy song song
    //   → player chỉ nhận kết quả của lần đầu, lần sau bị playbackGeneration chặn
    //   → currentIndex đã tăng 2 nhưng nhạc chỉ chuyển 1 bài → mất đồng bộ.
    @Volatile
    private var isSwitchingSong = false

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

        // Khởi tạo MediaSession + bộ build notification (MediaStyle)
        mediaSessionManager = MediaSessionManager(
            context = this,
            provider = this,
            onMediaAction = { action ->
                // Callback từ MediaSessionCompat (lock screen / tai nghe / Bluetooth)
                when (action) {
                    ACTION_PLAY -> play()
                    ACTION_PAUSE -> pause()
                    ACTION_NEXT -> next()
                    ACTION_PREVIOUS -> previous()
                    ACTION_STOP -> {
                        stopPlayback()
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                }
            }
        )
    }

    /**
     * Xử lý intent từ notification buttons (Play/Pause, Stop)
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Log mọi action nhận được → debug nút notification có tới đây không
        Log.d(TAG, "onStartCommand: action=${intent?.action}")
        when (intent?.action) {
            // Nút bấm từ notification: Play / Pause / Next / Previous
            ACTION_PLAY -> {
                Log.d(TAG, "→ play()")
                play()
            }
            ACTION_PAUSE -> {
                Log.d(TAG, "→ pause()")
                pause()
            }
            ACTION_PLAY_PAUSE -> {
                Log.d(TAG, "→ togglePlayPause()")
                togglePlayPause()
            }
            ACTION_NEXT -> {
                Log.d(TAG, "→ next()")
                next()
            }
            ACTION_PREVIOUS -> {
                Log.d(TAG, "→ previous()")
                previous()
            }
            ACTION_STOP -> {
                Log.d(TAG, "→ stopPlayback()")
                stopPlayback()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            // Media button từ tai nghe/Bluetooth (qua MediaButtonReceiver)
            // → chuyển KeyEvent vào MediaSessionCompat để kích hoạt callback
            Intent.ACTION_MEDIA_BUTTON ->
                MediaButtonReceiver.handleIntent(mediaSessionManager.session, intent)
        }
        return START_NOT_STICKY // Không tự restart nếu bị system kill
    }

    /**
     * Phát 1 danh sách bài hát (hàng đợi) bắt đầu từ vị trí startIndex.
     * Khi 1 bài phát xong → TỰ ĐỘNG chuyển sang bài tiếp theo trong danh sách.
     *
     * @param preFetchedUrl URL bài đầu tiên đã có sẵn (màn hình cũ MainActivity
     *        fetch trước rồi truyền vào). Nếu null → service tự lấy qua repository.
     */
    fun playQueue(songs: List<SongItem>, startIndex: Int, preFetchedUrl: String? = null) {
        if (songs.isEmpty()) return
        queue = songs
        currentIndex = startIndex.coerceIn(0, songs.size - 1)

        val song = queue[currentIndex]
        currentSong = song
        onSongChanged?.invoke(song)

        if (preFetchedUrl != null) {
            // URL đã có sẵn → phát thẳng, không fetch lại
            playInternal(preFetchedUrl, song.title, song.artistsNames)
        } else {
            // Tự lấy URL rồi phát
            playCurrent()
        }
    }

    /**
     * Phát bài tại vị trí currentIndex trong queue.
     * Lấy URL stream ở background → playInternal → thông báo bài mới cho UI.
     */
    private fun playCurrent() {
        if (currentIndex !in queue.indices) {
            stopPlayback()
            return
        }
        val song = queue[currentIndex]
        currentSong = song
        onSongChanged?.invoke(song)

        // Cờ chặn bấm liên tiếp → chỉ 1 lần fetch URL tại 1 thời điểm
        isSwitchingSong = true

        // Lấy URL ở background; có kết quả mới phát (tránh block main thread)
        val generation = playbackGeneration
        playbackScope.launch {
            try {
                repository.getStreamUrl(song.encodeId)
                    .onSuccess { url ->
                        // Bỏ qua nếu user đã chọn bài khác trong lúc buffer
                        if (generation == playbackGeneration) {
                            playInternal(url, song.title, song.artistsNames)
                        }
                    }
                    .onFailure { error ->
                        // ⚠️ BUG CŨ ĐÃ SỬA: trước đây lỗi mạng sẽ đệ quy
                        //   `currentIndex++; playCurrent()` — chạy qua CẢ danh sách,
                        //   bài nào cũng fetch fail → nhạc KHÔNG đổi, notification KHÔNG đổi,
                        //   còn currentIndex nhảy về cuối list → bấm Next sau đó no-op vĩnh viễn.
                        // → giờ: báo lỗi rõ ràng, GIỮ NGUYÊN bài đang phát, không dịch index.
                        Log.e(TAG, "playCurrent() FAILED [${song.title}]: ${error.message}")
                        onError?.invoke(error.message ?: "Không lấy được link nhạc")
                        // Đồng bộ lại trạng thái + notification (bài cũ vẫn đang phát)
                        onPlaybackStateChanged?.invoke(mediaPlayer?.isPlaying == true)
                        mediaSessionManager.updatePlaybackState()
                        refreshNotification()
                    }
            } finally {
                // Luôn nhả cờ để lần bấm sau được xử lý
                isSwitchingSong = false
            }
        }
    }

    /**
     * Phát 1 bài đơn lẻ (KHÔNG auto-advance) — dùng cho màn hình cũ MainActivity.
     * Xóa queue để bài phát xong KHÔNG tự chuyển sang bài khác bất ngờ.
     */
    fun playFromUrl(url: String, title: String, artist: String) {
        queue = emptyList()
        currentIndex = -1
        playInternal(url, title, artist)
    }

    /**
     * Phần lõi phát nhạc: tạo MediaPlayer mới, buffer rồi start.
     * Tách riêng để cả playQueue lẫn playFromUrl dùng chung.
     */
    private fun playInternal(url: String, title: String, artist: String) {
        currentTitle = title
        currentArtist = artist
        currentArtUrl = currentSong?.thumbnailM ?: currentSong?.thumbnail

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
                    // Cập nhật metadata (title/artist/album art) + trạng thái phát
                    mediaSessionManager.updateMetadata()
                    mediaSessionManager.updatePlaybackState()
                    // Bắt đầu foreground service với notification MediaStyle (3 nút)
                    startForeground(NOTIFICATION_ID, mediaSessionManager.buildNotification())
                }

                // Callback khi phát xong bài → TỰ ĐỘNG chuyển bài kế tiếp trong queue
                setOnCompletionListener {
                    if (generation != playbackGeneration) return@setOnCompletionListener
                    onPlaybackStateChanged?.invoke(false)
                    mediaSessionManager.updatePlaybackState()
                    if (currentIndex + 1 < queue.size) {
                        // Còn bài tiếp theo → phát luôn (auto-advance, chạy cả khi app ở background)
                        currentIndex++
                        playCurrent()
                    } else {
                        // Hết danh sách → dừng, cập nhật notification (icon pause)
                        refreshNotification()
                    }
                }

                // Callback khi có lỗi (URL hết hạn, network...)
                setOnErrorListener { _, what, extra ->
                    if (generation != playbackGeneration) return@setOnErrorListener true
                    isPrepared = false
                    onError?.invoke("Lỗi phát nhạc (code: $what/$extra)")
                    onPlaybackStateChanged?.invoke(false)
                    mediaSessionManager.updatePlaybackState()
                    refreshNotification()
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
            } else {
                player.start()
                onPlaybackStateChanged?.invoke(true)
            }
            // Đồng bộ MediaSession + notification theo trạng thái mới
            mediaSessionManager.updatePlaybackState()
            refreshNotification()
        }
    }

    /**
     * Phát (nếu đang pause).
     * PlaybackController.play() → ủy quyền vào đây.
     */
    fun play() {
        mediaPlayer?.let { player ->
            // Chưa prepare xong → bỏ qua (tránh error -38)
            if (!isPrepared) return
            if (!player.isPlaying) {
                player.start()
                onPlaybackStateChanged?.invoke(true)
                mediaSessionManager.updatePlaybackState()
                refreshNotification()
            }
        }
    }

    /**
     * Tạm dừng (nếu đang phát).
     * PlaybackController.pause() → ủy quyền vào đây.
     */
    fun pause() {
        mediaPlayer?.let { player ->
            if (!isPrepared) return
            if (player.isPlaying) {
                player.pause()
                onPlaybackStateChanged?.invoke(false)
                mediaSessionManager.updatePlaybackState()
                refreshNotification()
            }
        }
    }

    /**
     * Chuyển sang bài kế tiếp trong queue (auto-advance thủ công).
     * PlaybackController.next() → ủy quyền vào đây.
     */
    fun next() {
        Log.d(TAG, "next(): currentIndex=$currentIndex queue.size=${queue.size}")
        // Chặn bấm liên tục trong lúc đang fetch URL bài mới
        if (isSwitchingSong) {
            Log.d(TAG, "next(): bỏ qua — đang chuyển bài")
            return
        }
        if (currentIndex + 1 < queue.size) {
            currentIndex++
            playCurrent()
        } else {
            Log.d(TAG, "next(): đã ở bài cuối, không có bài tiếp")
        }
    }

    /**
     * Chuyển về bài trước đó trong queue.
     * PlaybackController.previous() → ủy quyền vào đây.
     */
    fun previous() {
        Log.d(TAG, "previous(): currentIndex=$currentIndex queue.size=${queue.size}")
        if (isSwitchingSong) {
            Log.d(TAG, "previous(): bỏ qua — đang chuyển bài")
            return
        }
        if (currentIndex - 1 >= 0) {
            currentIndex--
            playCurrent()
        } else {
            Log.d(TAG, "previous(): đã ở bài đầu, không có bài trước")
        }
    }

    /**
     * Kiểm tra đang phát hay không (MediaStateProvider + PlaybackController dùng chung)
     */
    override fun isPlaying(): Boolean = mediaPlayer?.isPlaying == true

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
        queue = emptyList()
        currentIndex = -1
        currentSong = null
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

    // ================== MediaStateProvider (MediaSessionManager gọi để đọc trạng thái) ==================

    /** Vị trí phát hiện tại (ms) — cho PlaybackState + progress trên lock screen */
    override fun getCurrentPositionMs(): Long = mediaPlayer?.currentPosition?.toLong() ?: 0L

    /** Tổng thời lượng bài (ms) — 0 nếu chưa biết (chưa prepare xong) */
    override fun getDurationMs(): Long = mediaPlayer?.duration?.toLong() ?: 0L

    override fun getTitle(): String = currentTitle

    override fun getArtist(): String = currentArtist

    override fun getArtUrl(): String? = currentArtUrl

    /**
     * Build notification mới rồi cập nhật lên foreground service.
     * Gọi mỗi khi trạng thái play/pause, bài mới, hoặc ảnh album load xong.
     */
    override fun refreshNotification() {
        startForeground(NOTIFICATION_ID, mediaSessionManager.buildNotification())
    }

    /**
     * Cleanup khi Service bị destroy
     */
    override fun onDestroy() {
        playbackScope.cancel()
        mediaPlayer?.release()
        mediaPlayer = null
        mediaSessionManager.release()
        super.onDestroy()
    }
}
