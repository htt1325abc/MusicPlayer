package com.example.musicplayer.presenter.base

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.viewbinding.ViewBinding
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.example.musicplayer.R
import com.example.musicplayer.common.IActivity
import com.example.musicplayer.common.IViewModel
import com.example.musicplayer.data.local.repository.RecentPlayedStore
import com.example.musicplayer.model.SongItem
import com.example.musicplayer.service.MusicPlaybackController
import com.example.musicplayer.service.MusicService
import com.example.musicplayer.service.PlaybackController
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

/**
 * BasePlayerActivity — lớp nền dùng chung logic PHÁT NHẠC cho các màn hình
 * (HomeActivity, SongListActivity, MainActivity).
 *
 * DI CHUYỂN từ `ui/` → `presenter/base/` và GIỜ KẾ THỪA [IActivity] (base class
 * chuẩn của project) — đúng convention project MẪU:
 *   AppCompatActivity ← IActivity ← BasePlayerActivity ← HomeActivity/SongListActivity/MainActivity
 *
 * TẠI SAO cần class này?
 * → Trước đây MainActivity tự viết toàn bộ: bind MusicService + mini player + phát bài.
 * → Giờ có 3+ màn hình cần phát nhạc → tách phần chung vào base → tránh duplicate code.
 *
 * Base này chỉ bổ sung phần "player", còn template vòng đời (onCreate → initViews →
 * initObservers → initListeners) đã do [IActivity] lo.
 */
abstract class BasePlayerActivity<VB : ViewBinding, VM : IViewModel<State>, State : IViewModel.IState> :
    IActivity<VB, VM, State>() {

    // Reference đến MusicService (qua Binder)
    protected var musicService: MusicService? = null
    private var isBound = false

    // "Nghe gần đây" — Koin tự bơm instance singleton (Room-backed)
    protected val recentStore: RecentPlayedStore by inject()

    // PlaybackController — Koin singleton (đăng ký theo INTERFACE PlaybackController).
    // Activity GÁN service vào khi bind xong để ViewModel gọi next()/previous() xuống Service.
    private val playbackController: PlaybackController by inject()

    // ViewModel inject interface PlaybackController, còn Activity cần gán `service`
    // (chỉ có ở class cụ thể) → cast an toàn về MusicPlaybackController khi cần.
    private val playbackImpl: MusicPlaybackController
        get() = playbackController as MusicPlaybackController

    // Hàng đợi đang CHỜ phát khi Service chưa bind xong (musicService = null).
    private var pendingPlay: Pair<List<SongItem>, Int>? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MusicService.MusicBinder
            musicService = binder.getService()
            isBound = true

            // Gán service vào PlaybackController → ViewModel gọi next()/prev() được
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

    /** Bind MusicService — gọi trong initViews của Activity con */
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
     * Gắn sự kiện bấm nút trên mini player (Prev · Play/Pause · Next):
     * - Play/Pause → qua PlaybackController
     * - Next/Prev → gọi hook onNext()/onPrevious() để Activity con ủy quyền xuống
     *   ViewModel.onState(State.Next/Previous) → PlaybackController → MusicService.
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

    /** Hook bấm nút Next — Activity con override để gọi ViewModel.onState(State.Next). */
    protected open fun onNext() {}

    /** Hook bấm nút Prev — Activity con override để gọi ViewModel.onState(State.Previous). */
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
     * → Activity chỉ đưa CẢ DANH SÁCH + vị trí cho Service. Service tự lấy URL bài,
     *   và khi bài phát xong sẽ TỰ ĐỘNG phát bài kế tiếp (auto-advance) kể cả khi app
     *   ở background (Service chạy nền).
     * → Đồng thời: hiện mini player ngay + lưu vào "Nghe gần đây" (Room).
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
