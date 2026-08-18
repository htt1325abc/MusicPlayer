package com.example.musicplayer.ui

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.example.musicplayer.R
import com.example.musicplayer.model.SongItem
import com.example.musicplayer.repository.RecentPlayedStore
import androidx.lifecycle.lifecycleScope
import com.example.musicplayer.service.MusicPlaybackController
import com.example.musicplayer.service.MusicService
import com.example.musicplayer.service.PlaybackController
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

/**
 * BasePlayerActivity — lớp nền dùng chung logic PHÁT NHẠC cho các màn hình MỚI
 * (HomeActivity, SongListActivity).
 *
 * TẠI SAO cần class này?
 * → Trước đây MainActivity tự viết toàn bộ: bind MusicService + mini player + phát bài.
 * → Giờ có 2+ màn hình cần phát nhạc → tách phần chung vào base → tránh duplicate code.
 * → MainActivity GIỮ NGUYÊN cách cũ (không extends base) để bạn so sánh trước/sau.
 *
 * KHÁC BIỆT DI (KOIN) so với MainActivity:
 * → MainActivity cũ: `musicService?.onPlaybackStateChanged = {...}` tự gán trong Activity,
 *   tự `MusicRepository()` khi cần.
 * → Base này: `RecentPlayedStore` được Koin INJECT (`by inject()`), Activity không tự new.
 */
abstract class BasePlayerActivity : AppCompatActivity() {

    // Reference đến MusicService (qua Binder) — giống hệt MainActivity
    protected var musicService: MusicService? = null
    private var isBound = false

    // "Nghe gần đây" — Koin tự bơm instance singleton (Room-backed từ PHẦN 2)
    protected val recentStore: RecentPlayedStore by inject()

    // PlaybackController — Koin singleton (đăng ký theo INTERFACE PlaybackController).
    // Activity GÁN service vào khi bind xong để ViewModel có thể gọi next()/previous()
    // xuống Service (PHẦN 1).
    private val playbackController: PlaybackController by inject()

    // ViewModel inject interface PlaybackController, còn Activity cần gán `service`
    // (chỉ có ở class cụ thể) → cast an toàn về MusicPlaybackController khi cần.
    private val playbackImpl: MusicPlaybackController
        get() = playbackController as MusicPlaybackController

    // Hàng đợi đang CHỜ phát khi Service chưa bind xong (musicService = null).
    // TẠI SAO cần? → User có thể bấm bài NGAY trước khi Service connected.
    // → Nếu không lưu, URL/danh sách bị mất → bấm không phát được.
    private var pendingPlay: Pair<List<SongItem>, Int>? = null

    /**
     * ServiceConnection — callback khi bind/unbind MusicService.
     * Giống MainActivity, nhưng gom chung vào 1 nơi để mọi màn hình dùng lại.
     */
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MusicService.MusicBinder
            musicService = binder.getService()
            isBound = true

            // Gán service vào PlaybackController → ViewModel gọi next()/prev() được (PHẦN 1)
            playbackImpl.service = musicService

            // Lắng nghe trạng thái phát → cập nhật icon play/pause trên mini player
            musicService?.onPlaybackStateChanged = { isPlaying ->
                runOnUiThread { updatePlayPauseIcon(isPlaying) }
            }
            // Lắng nghe lỗi từ service → hiện Toast
            musicService?.onError = { message ->
                runOnUiThread {
                    Toast.makeText(this@BasePlayerActivity, message, Toast.LENGTH_SHORT).show()
                }
            }
            // Lắng nghe khi service TỰ ĐỘNG chuyển bài (auto-advance) → cập nhật mini player
            musicService?.onSongChanged = { song ->
                runOnUiThread { showMiniPlayer(song) }
            }

            onServiceReady()

            // Phát bài đang chờ (nếu user bấm trước khi Service sẵn sàng)
            pendingPlay?.let { (songs, index) ->
                musicService?.playQueue(songs, index)
                pendingPlay = null
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            // Xóa reference → controller no-op thay vì gọi service đã mất
            playbackImpl.service = null
            musicService = null
            isBound = false
        }
    }

    /** Bind MusicService — gọi trong onCreate của Activity con */
    protected fun bindPlayerService() {
        val intent = Intent(this, MusicService::class.java)
        bindService(intent, serviceConnection, BIND_AUTO_CREATE)
    }

    /** Unbind MusicService — gọi trong onDestroy (tránh memory leak) */
    protected fun unbindPlayerService() {
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
    }

    /** Hook cho Activity con: làm gì đó sau khi service sẵn sàng (mặc định không làm gì) */
    protected open fun onServiceReady() {}

    /**
     * Gắn sự kiện bấm nút trên mini player (PHẦN 1):
     * - Play/Pause → đi qua PlaybackController (đã gán service)
     * - Next/Prev → gọi hook onNext()/onPrevious() để Activity con quyết định
     *   (thường là ủy quyền xuống ViewModel.next()/previous())
     */
    protected fun setupMiniPlayer() {
        findViewById<ImageButton>(R.id.btnMiniPlayPause)?.setOnClickListener {
            playbackController.togglePlayPause()
        }
        findViewById<ImageButton>(R.id.btnMiniNext)?.setOnClickListener {
            onNext()
        }
        findViewById<ImageButton>(R.id.btnMiniPrev)?.setOnClickListener {
            onPrevious()
        }
    }

    /**
     * Hook bấm nút Next — Activity con override để gọi ViewModel.next()
     * (VD: HomeActivity → homeViewModel.next() → PlaybackController → MusicService.next()).
     */
    protected open fun onNext() {}

    /**
     * Hook bấm nút Prev — Activity con override để gọi ViewModel.previous().
     */
    protected open fun onPrevious() {}

    /** Hiện mini player + cập nhật thông tin bài đang phát */
    protected fun showMiniPlayer(song: SongItem) {
        findViewById<View>(R.id.miniPlayerBar)?.visibility = View.VISIBLE
        findViewById<TextView>(R.id.tvMiniTitle)?.text = song.title
        findViewById<TextView>(R.id.tvMiniArtist)?.text = song.artistsNames

        Glide.with(this)
            .load(song.thumbnailM ?: song.thumbnail)
            .apply(
                RequestOptions()
                    .placeholder(R.drawable.ic_music_note)
                    .error(R.drawable.ic_music_note)
            )
            .into(findViewById(R.id.ivMiniThumbnail))
    }

    /** Cập nhật icon play/pause theo trạng thái thực tế của MediaPlayer */
    protected fun updatePlayPauseIcon(isPlaying: Boolean) {
        findViewById<ImageButton>(R.id.btnMiniPlayPause)?.setImageResource(
            if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        )
    }

    /**
     * Phát 1 danh sách bài hát (hàng đợi) bắt đầu từ vị trí index.
     *
     * ⚠️ THAY ĐỔI so với playSong() cũ:
     * → Trước: Activity tự `getStreamUrl()` rồi gửi URL cho Service → Service phát 1 bài,
     *   bài xong là DỪNG (không tự chuyển bài tiếp theo).
     * → Sau : Activity chỉ đưa CẢ DANH SÁCH + vị trí cho Service. Service tự lấy URL bài,
     *   và khi bài phát xong sẽ TỰ ĐỘNG phát bài kế tiếp (auto-advance) — kể cả khi
     *   app ở background (Service chạy nền nên vẫn hoạt động).
     *
     * Ngoài ra vẫn: hiện mini player ngay + lưu vào "Nghe gần đây".
     */
    protected fun playQueue(songs: List<SongItem>, index: Int) {
        val song = songs.getOrNull(index) ?: return

        // Hiện mini player ngay (feedback tức thì)
        showMiniPlayer(song)
        // Lưu vào lịch sử nghe gần đây (Room — ghi bất đồng bộ, không block UI)
        lifecycleScope.launch {
            recentStore.add(song)
        }

        // Service chưa bind xong → lưu lại, phát khi connected
        if (musicService == null) {
            pendingPlay = songs to index
        } else {
            musicService?.playQueue(songs, index)
        }
    }

    override fun onDestroy() {
        unbindPlayerService()
        super.onDestroy()
    }
}
