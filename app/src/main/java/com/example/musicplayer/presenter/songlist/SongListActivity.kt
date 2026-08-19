package com.example.musicplayer.presenter.songlist

import android.os.Bundle
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.musicplayer.adapter.SongAdapter
import com.example.musicplayer.databinding.ActivitySongListBinding
import com.example.musicplayer.presenter.base.BasePlayerActivity
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

/**
 * SongListActivity — màn hình danh sách bài hát, DÙNG LẠI SongAdapter.
 *
 * DI CHUYỂN từ `ui/` → `presenter/songlist/` và GIỜ KẾ THỪA BasePlayerActivity (→ IActivity).
 *
 * Được mở từ Home khi bấm vào:
 * 1. 1 PLAYLIST  → PlaylistState.LoadPlaylist(id)
 * 2. 1 THỂ LOẠI  → PlaylistState.SearchSongs(title, keyword)
 */
class SongListActivity : BasePlayerActivity<ActivitySongListBinding, PlaylistViewModel, PlaylistState>() {

    override fun getLazyViewModel() = viewModel<PlaylistViewModel>()
    override fun getLazyViewBinding() = lazy { ActivitySongListBinding.inflate(layoutInflater) }

    private lateinit var songAdapter: SongAdapter

    companion object {
        // Key cho Intent extras
        const val EXTRA_MODE = "mode"
        const val EXTRA_TITLE = "title"
        const val EXTRA_ID = "id"

        // Các mode màn hình
        const val MODE_PLAYLIST = "playlist"
        const val MODE_SEARCH = "search"
    }

    override fun initViews(savedInstanceState: Bundle?) {
        // Toolbar có nút back.
        // ⚠️ KHÔNG dùng setSupportActionBar() cho toolbar này — AppCompat "nuốt" click back.
        // Dùng toolbar ĐỘC LẬP + tự gắn click bằng setNavigationOnClickListener().
        viewBinding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        songAdapter = SongAdapter(
            onItemClick = { _, position ->
                // Phát theo CẢ danh sách (queue) → Service tự phát bài kế tiếp (auto-advance)
                playQueue(viewModel.songs.value, position)
            },
            onFavoriteClick = { song, _ ->
                // Bấm tim → gửi State xuống ViewModel (ghi Room)
                viewModel.onState(PlaylistState.ToggleFavorite(song))
            }
        )
        viewBinding.rvSongs.layoutManager = LinearLayoutManager(this)
        viewBinding.rvSongs.adapter = songAdapter

        bindPlayerService()
        setupMiniPlayer()

        // Đọc tham số từ Intent và tải dữ liệu tương ứng (pattern onState)
        val title = intent.getStringExtra(EXTRA_TITLE) ?: ""
        val id = intent.getStringExtra(EXTRA_ID) ?: ""
        val mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_SEARCH

        viewBinding.toolbar.title = title
        when (mode) {
            MODE_PLAYLIST -> viewModel.onState(PlaylistState.LoadPlaylist(id))
            else -> viewModel.onState(PlaylistState.SearchSongs(title, id))
        }
    }

    override fun initObservers() {
        observerLoadingState(
            onLoading = { viewBinding.progressBar.visibility = View.VISIBLE },
            onLoaded = { viewBinding.progressBar.visibility = View.GONE }
        )

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Danh sách bài hát
                launch {
                    viewModel.songs.collect { songs ->
                        songAdapter.submitList(songs)
                        viewBinding.rvSongs.visibility =
                            if (songs.isNotEmpty()) View.VISIBLE else View.GONE
                    }
                }
                // Tiêu đề (tên playlist / thể loại) — cập nhật toolbar
                launch {
                    viewModel.title.collect { title ->
                        title?.let { viewBinding.toolbar.title = it }
                    }
                }
                // Lỗi / trống
                launch {
                    viewModel.errorMessage.collect { message ->
                        viewBinding.tvMessage.visibility =
                            if (message != null) View.VISIBLE else View.GONE
                        viewBinding.tvMessage.text = message
                    }
                }
                // Yêu thích (Room): cập nhật icon trái tim trên từng dòng
                launch {
                    viewModel.favoriteIds.collect { ids ->
                        songAdapter.updateFavorites(ids)
                    }
                }
            }
        }
    }

    /**
     * Bấm nút Next trên mini player → gửi State xuống ViewModel (PHẦN 1).
     * UI → onNext() → viewModel.onState(PlaylistState.Next) → PlaybackController → MusicService.next()
     */
    override fun onNext() {
        viewModel.onState(PlaylistState.Next)
    }

    override fun onPrevious() {
        viewModel.onState(PlaylistState.Previous)
    }
}
