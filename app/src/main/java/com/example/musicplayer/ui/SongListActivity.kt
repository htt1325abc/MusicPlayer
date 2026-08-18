package com.example.musicplayer.ui

import android.os.Bundle
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.musicplayer.adapter.SongAdapter
import com.example.musicplayer.databinding.ActivitySongListBinding
import com.example.musicplayer.viewmodel.PlaylistViewModel
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

/**
 * SongListActivity — màn hình danh sách bài hát, DÙNG LẠI SongAdapter.
 *
 * Được mở từ Home khi bấm vào:
 * 1. 1 PLAYLIST  → mode = MODE_PLAYLIST → PlaylistViewModel.loadPlaylist(id)
 * 2. 1 THỂ LOẠI  → mode = MODE_SEARCH   → PlaylistViewModel.searchSongs(tên thể loại)
 *
 * Cũng dùng Koin: `by viewModel<PlaylistViewModel>()` + `by inject()` cho repository.
 * So với MainActivity: phần bind MusicService + mini player nằm trong BasePlayerActivity.
 */
class SongListActivity : BasePlayerActivity() {

    private lateinit var binding: ActivitySongListBinding

    private val playlistViewModel: PlaylistViewModel by viewModel()

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySongListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Toolbar có nút back.
        // ⚠️ QUAN TRỌNG: KHÔNG dùng setSupportActionBar() cho toolbar này!
        // → Khi dùng setSupportActionBar(), AppCompat chiếm quyền điều khiển nút back
        //   của MaterialToolbar và "nuốt" click → nút back không hoạt động (đã test thực tế).
        // → Giải pháp: dùng toolbar ĐỘC LẬP + tự gắn click bằng setNavigationOnClickListener()
        //   (API gốc của Toolbar → chắc chắn được gọi khi bấm nút mũi tên).
        binding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()   // tương đương nút Back hệ thống
        }

        // DÙNG LẠI SongAdapter có sẵn — bấm bài → phát theo cả danh sách (queue)
        // → Service tự phát bài kế tiếp khi bài hiện tại hết (auto-advance)
        // PHẦN 2: nút trái tim trên mỗi dòng → toggleFavorite (ghi Room qua ViewModel)
        songAdapter = SongAdapter(
            onItemClick = { _, position ->
                playQueue(playlistViewModel.songs.value, position)
            },
            onFavoriteClick = { song, _ ->
                playlistViewModel.toggleFavorite(song)
            }
        )
        binding.rvSongs.layoutManager = LinearLayoutManager(this)
        binding.rvSongs.adapter = songAdapter

        bindPlayerService()
        setupMiniPlayer()
        observeViewModel()

        // Đọc tham số từ Intent và tải dữ liệu tương ứng
        val title = intent.getStringExtra(EXTRA_TITLE) ?: ""
        val id = intent.getStringExtra(EXTRA_ID) ?: ""
        val mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_SEARCH

        binding.toolbar.title = title
        when (mode) {
            MODE_PLAYLIST -> playlistViewModel.loadPlaylist(id)
            else -> {
                playlistViewModel.setTitle(title)
                playlistViewModel.searchSongs(id)
            }
        }
    }

    /** Observe StateFlow từ PlaylistViewModel → cập nhật danh sách */
    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Danh sách bài hát
                launch {
                    playlistViewModel.songs.collect { songs ->
                        songAdapter.submitList(songs)
                        binding.rvSongs.visibility =
                            if (songs.isNotEmpty()) View.VISIBLE else View.GONE
                    }
                }
                // Loading
                launch {
                    playlistViewModel.isLoading.collect { isLoading ->
                        binding.progressBar.visibility =
                            if (isLoading) View.VISIBLE else View.GONE
                    }
                }
                // Lỗi / trống
                launch {
                    playlistViewModel.errorMessage.collect { message ->
                        binding.tvMessage.visibility =
                            if (message != null) View.VISIBLE else View.GONE
                        binding.tvMessage.text = message
                    }
                }
                // Yêu thích (PHẦN 2): cập nhật icon trái tim trên từng dòng
                launch {
                    playlistViewModel.favoriteIds.collect { ids ->
                        songAdapter.updateFavorites(ids)
                    }
                }
            }
        }
    }

    /**
     * Bấm nút Next trên mini player → xuống Service qua ViewModel (PHẦN 1).
     * UI nút bấm → onNext() → playlistViewModel.next() → PlaybackController → MusicService.next()
     */
    override fun onNext() {
        playlistViewModel.next()
    }

    /**
     * Bấm nút Prev trên mini player → xuống Service qua ViewModel.
     */
    override fun onPrevious() {
        playlistViewModel.previous()
    }
}
