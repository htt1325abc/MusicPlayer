package com.example.musicplayer.presenter.home

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.musicplayer.R
import com.example.musicplayer.adapter.GenreAdapter
import com.example.musicplayer.adapter.PlaylistAdapter
import com.example.musicplayer.adapter.RecentSongAdapter
import com.example.musicplayer.databinding.ActivityHomeBinding
import com.example.musicplayer.presenter.base.BasePlayerActivity
import com.example.musicplayer.presenter.search.MainActivity
import com.example.musicplayer.presenter.songlist.SongListActivity
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

/**
 * HomeActivity — màn hình chính (launcher).
 * Gồm: Thể loại nhạc · Playlist nổi bật · Playlist đã lưu · Yêu thích · Nghe gần đây.
 *
 * DI CHUYỂN từ `ui/` → `presenter/home/` và GIỜ KẾ THỪA BasePlayerActivity (→ IActivity),
 * theo đúng template vòng đời của project MẪU:
 *   onCreate → initViews() → initObservers() → initListeners()
 *
 * PHẦN 1 (next/prev): nút trên mini player → onNext()/onPrevious()
 *   → viewModel.onState(HomeState.Next/Previous) → PlaybackController → MusicService.
 * PHẦN 2 (Room): "Yêu thích" + "Nghe gần đây" đến từ Room Flow → UI tự cập nhật.
 * PHẦN 3 (Room): "Playlist đã lưu" (bookmark) — nút lưu trên card playlist.
 */
class HomeActivity : BasePlayerActivity<ActivityHomeBinding, HomeViewModel, HomeState>() {

    // Koin tự khởi tạo HomeViewModel + bơm dependency qua constructor
    override fun getLazyViewModel() = viewModel<HomeViewModel>()
    override fun getLazyViewBinding() = lazy { ActivityHomeBinding.inflate(layoutInflater) }

    private lateinit var genreAdapter: GenreAdapter
    private lateinit var playlistAdapter: PlaylistAdapter
    private lateinit var savedPlaylistAdapter: PlaylistAdapter
    private lateinit var recentAdapter: RecentSongAdapter
    private lateinit var favoriteAdapter: RecentSongAdapter

    // Xin quyền POST_NOTIFICATIONS (Android 13+/API 33+) để hiện media notification
    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Không bắt buộc xử lý thêm */ }

    override fun initViews(savedInstanceState: Bundle?) {
        // Toolbar + menu tìm kiếm
        setSupportActionBar(viewBinding.toolbar)

        setupAdapters()
        bindPlayerService()
        setupMiniPlayer()

        requestNotificationPermissionIfNeeded()
        // Lưu ý: không cần gọi viewModel.loadHome() ở đây nữa — ViewModel tự load trong init.
    }

    override fun initObservers() {
        // Loading — dùng helper có sẵn trong IActivity (collect viewModel.isLoading)
        observerLoadingState(
            onLoading = { viewBinding.progressBar.visibility = View.VISIBLE },
            onLoaded = { viewBinding.progressBar.visibility = View.GONE }
        )

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Playlist nổi bật
                launch {
                    viewModel.featuredPlaylists.collect { list ->
                        playlistAdapter.submitList(list)
                        viewBinding.rvPlaylists.visibility =
                            if (list.isEmpty()) View.GONE else View.VISIBLE
                    }
                }
                // Nghe gần đây
                launch {
                    viewModel.recentSongs.collect { list ->
                        recentAdapter.submitList(list)
                        viewBinding.rvRecent.visibility =
                            if (list.isEmpty()) View.GONE else View.VISIBLE
                    }
                }
                // Yêu thích (Room)
                launch {
                    viewModel.favorites.collect { list ->
                        favoriteAdapter.submitList(list)
                        val visible = list.isNotEmpty()
                        viewBinding.rvFavorites.visibility = if (visible) View.VISIBLE else View.GONE
                        viewBinding.tvFavoritesHeader.visibility =
                            if (visible) View.VISIBLE else View.GONE
                    }
                }
                // Playlist đã lưu (Room — PHẦN 3)
                launch {
                    viewModel.savedPlaylists.collect { list ->
                        savedPlaylistAdapter.submitList(list)
                        val visible = list.isNotEmpty()
                        viewBinding.rvSavedPlaylists.visibility =
                            if (visible) View.VISIBLE else View.GONE
                        viewBinding.tvSavedPlaylistsHeader.visibility =
                            if (visible) View.VISIBLE else View.GONE
                    }
                }
                // Icon bookmark trên card playlist (PHẦN 3)
                launch {
                    viewModel.savedPlaylistIds.collect { ids ->
                        playlistAdapter.updateSaved(ids)
                        savedPlaylistAdapter.updateSaved(ids)
                    }
                }
                // Lỗi / thông báo
                launch {
                    viewModel.errorMessage.collect { message ->
                        viewBinding.tvMessage.visibility =
                            if (message != null) View.VISIBLE else View.GONE
                        viewBinding.tvMessage.text = message
                    }
                }
            }
        }
    }

    /**
     * Bấm nút Next trên mini player → gửi State xuống ViewModel (PHẦN 1).
     * UI → onNext() → viewModel.onState(HomeState.Next) → PlaybackController → MusicService.next()
     */
    override fun onNext() {
        viewModel.onState(HomeState.Next)
    }

    override fun onPrevious() {
        viewModel.onState(HomeState.Previous)
    }

    private fun setupAdapters() {
        // ---- Section 1: Thể loại nhạc (cuộn ngang) ----
        genreAdapter = GenreAdapter { genre ->
            openSongList(
                title = genre.name,
                mode = SongListActivity.MODE_SEARCH,
                id = genre.name
            )
        }
        viewBinding.rvGenres.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        viewBinding.rvGenres.adapter = genreAdapter
        genreAdapter.submitList(viewModel.genres)

        // ---- Section 2: Playlist nổi bật (cuộn ngang) + nút lưu bookmark (PHẦN 3) ----
        val onPlaylistClick: (com.example.musicplayer.model.PlaylistItem) -> Unit = { playlist ->
            openSongList(
                title = playlist.title,
                mode = SongListActivity.MODE_PLAYLIST,
                id = playlist.encodeId
            )
        }
        val onSaveClick: (com.example.musicplayer.model.PlaylistItem) -> Unit = { playlist ->
            viewModel.onState(HomeState.ToggleSavePlaylist(playlist))
        }

        playlistAdapter = PlaylistAdapter(onPlaylistClick, onSaveClick)
        viewBinding.rvPlaylists.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        viewBinding.rvPlaylists.adapter = playlistAdapter

        // ---- Section: Playlist đã lưu (Room — PHẦN 3) ----
        savedPlaylistAdapter = PlaylistAdapter(onPlaylistClick, onSaveClick)
        viewBinding.rvSavedPlaylists.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        viewBinding.rvSavedPlaylists.adapter = savedPlaylistAdapter

        // ---- Section: Nghe gần đây (list dọc) ----
        recentAdapter = RecentSongAdapter(
            onSongClick = { _, position ->
                playQueue(viewModel.recentSongs.value, position)
            },
            onPlayClick = { _, position ->
                playQueue(viewModel.recentSongs.value, position)
            }
        )
        viewBinding.rvRecent.layoutManager = LinearLayoutManager(this)
        viewBinding.rvRecent.adapter = recentAdapter

        // ---- Section: Yêu thích (Room) ----
        favoriteAdapter = RecentSongAdapter(
            onSongClick = { _, position ->
                playQueue(viewModel.favorites.value, position)
            },
            onPlayClick = { _, position ->
                playQueue(viewModel.favorites.value, position)
            }
        )
        viewBinding.rvFavorites.layoutManager = LinearLayoutManager(this)
        viewBinding.rvFavorites.adapter = favoriteAdapter
    }

    /** Mở SongListActivity với tham số mode + id */
    private fun openSongList(title: String, mode: String, id: String) {
        val intent = Intent(this, SongListActivity::class.java).apply {
            putExtra(SongListActivity.EXTRA_TITLE, title)
            putExtra(SongListActivity.EXTRA_MODE, mode)
            putExtra(SongListActivity.EXTRA_ID, id)
        }
        startActivity(intent)
    }

    /** Xin quyền POST_NOTIFICATIONS trên Android 13+ (API 33) nếu chưa được cấp. */
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // ---- Toolbar menu: nút tìm kiếm mở MainActivity (màn hình search) ----
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_home, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_search -> {
                startActivity(Intent(this, MainActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
