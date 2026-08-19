package com.example.musicplayer.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.musicplayer.MainActivity
import com.example.musicplayer.R
import com.example.musicplayer.adapter.GenreAdapter
import com.example.musicplayer.adapter.PlaylistAdapter
import com.example.musicplayer.adapter.RecentSongAdapter
import com.example.musicplayer.databinding.ActivityHomeBinding
import com.example.musicplayer.viewmodel.HomeViewModel
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

/**
 * HomeActivity — màn hình chính MỚI (launcher).
 * Gồm 3 section: Thể loại nhạc · Playlist nổi bật · Nghe gần đây.
 *
 * ⚠️ KHÁC BIỆT LỚN VỀ DI (KOIN) so với MainActivity:
 * ┌────────────────────────────────────────────────────────────┐
 * │ MainActivity (CŨ)                          │
 * │  viewModel = ViewModelProvider(this)       │ ← Android tự tạo ViewModel
 * │            [MusicViewModel::class.java]    │   KHÔNG có tham số → ViewModel
 * │  → MusicViewModel tự new MusicRepository() │   tự new dependency bên trong
 * ├────────────────────────────────────────────────────────────┤
 * │ HomeActivity (MỚI)                         │
 * │  private val vm: HomeViewModel by viewModel() ← Koin tạo
 * │  private val repo: MusicRepository by inject()  ← Koin tạo
 * │  → Koin đọc viewModelModule: "HomeViewModel cần MusicRepository
 * │    + RecentPlayedStore" → tự tạo & bơm vào constructor
 * └────────────────────────────────────────────────────────────┘
 */
class HomeActivity : BasePlayerActivity() {

    private lateinit var binding: ActivityHomeBinding

    // Koin tự khởi tạo HomeViewModel + bơm MusicRepository & RecentPlayedStore
    private val homeViewModel: HomeViewModel by viewModel()

    private lateinit var genreAdapter: GenreAdapter
    private lateinit var playlistAdapter: PlaylistAdapter
    private lateinit var recentAdapter: RecentSongAdapter
    private lateinit var favoriteAdapter: RecentSongAdapter

    // Xin quyền POST_NOTIFICATIONS (Android 13+/API 33+).
    // TẠI SAO cần? → Android 13+ CHẶN notification (kể cả media notification)
    //   nếu app chưa được cấp quyền → người dùng không thấy nút điều khiển
    //   trên lock screen / thanh trạng thái.
    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Kết quả true/false — không bắt buộc xử lý thêm, chỉ cần xin khi chạy */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Toolbar + menu tìm kiếm
        setSupportActionBar(binding.toolbar)

        setupAdapters()
        bindPlayerService()
        setupMiniPlayer()
        observeViewModel()

        // Xin quyền hiện notification (chỉ trên Android 13+)
        requestNotificationPermissionIfNeeded()

        // Load playlist nổi bật từ server.
        // LƯU Ý (PHẦN 2): "Nghe gần đây" & "Yêu thích" giờ đến từ Room Flow → ViewModel
        // collect 1 lần trong init, Room TỰ emit lại khi data đổi → không cần load thủ công.
        homeViewModel.loadHome()
    }

    /**
     * Xin quyền POST_NOTIFICATIONS trên Android 13+ (API 33) nếu chưa được cấp.
     * Các bản cũ hơn (API < 33) không cần — permission cấp ngầm khi cài đặt.
     */
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun setupAdapters() {
        // ---- Section 1: Thể loại nhạc (cuộn ngang) ----
        genreAdapter = GenreAdapter { genre ->
            // Bấm thể loại → mở danh sách bài hát theo tên thể loại (search mode)
            openSongList(
                title = genre.name,
                mode = SongListActivity.MODE_SEARCH,
                id = genre.name
            )
        }
        binding.rvGenres.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.rvGenres.adapter = genreAdapter
        genreAdapter.submitList(homeViewModel.genres)

        // ---- Section 2: Playlist nổi bật (cuộn ngang) ----
        playlistAdapter = PlaylistAdapter { playlist ->
            // Bấm playlist → mở danh sách bài hát trong playlist
            openSongList(
                title = playlist.title,
                mode = SongListActivity.MODE_PLAYLIST,
                id = playlist.encodeId
            )
        }
        binding.rvPlaylists.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.rvPlaylists.adapter = playlistAdapter

        // ---- Section 3: Nghe gần đây (list dọc) ----
        // Bấm bài gần đây → phát theo cả danh sách gần đây (queue auto-advance)
        recentAdapter = RecentSongAdapter(
            onSongClick = { _, position ->
                playQueue(homeViewModel.recentSongs.value, position)
            },
            onPlayClick = { _, position ->
                playQueue(homeViewModel.recentSongs.value, position)
            }
        )
        binding.rvRecent.layoutManager = LinearLayoutManager(this)
        binding.rvRecent.adapter = recentAdapter

        // ---- Section: Yêu thích (PHẦN 2 - Room) ----
        // DÙNG LẠI RecentSongAdapter (danh sách SongItem có nút play).
        // Bấm bài yêu thích → phát theo cả danh sách yêu thích (queue auto-advance).
        favoriteAdapter = RecentSongAdapter(
            onSongClick = { _, position ->
                playQueue(homeViewModel.favorites.value, position)
            },
            onPlayClick = { _, position ->
                playQueue(homeViewModel.favorites.value, position)
            }
        )
        binding.rvFavorites.layoutManager = LinearLayoutManager(this)
        binding.rvFavorites.adapter = favoriteAdapter
    }

    /**
     * Bấm nút Next trên mini player → gọi xuống Service qua ViewModel (PHẦN 1).
     * Chuỗi: UI nút bấm → onNext() → homeViewModel.next() → PlaybackController → MusicService.next()
     */
    override fun onNext() {
        homeViewModel.next()
    }

    /**
     * Bấm nút Prev trên mini player → gọi xuống Service qua ViewModel.
     */
    override fun onPrevious() {
        homeViewModel.previous()
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

    /** Observe StateFlow từ HomeViewModel → cập nhật UI */
    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Playlist nổi bật
                launch {
                    homeViewModel.featuredPlaylists.collect { list ->
                        playlistAdapter.submitList(list)
                        binding.rvPlaylists.visibility =
                            if (list.isEmpty()) View.GONE else View.VISIBLE
                    }
                }
                // Nghe gần đây
                launch {
                    homeViewModel.recentSongs.collect { list ->
                        recentAdapter.submitList(list)
                        binding.rvRecent.visibility =
                            if (list.isEmpty()) View.GONE else View.VISIBLE
                    }
                }
                // Yêu thích (PHẦN 2 - Room)
                launch {
                    homeViewModel.favorites.collect { list ->
                        favoriteAdapter.submitList(list)
                        val visible = list.isNotEmpty()
                        binding.rvFavorites.visibility = if (visible) View.VISIBLE else View.GONE
                        binding.tvFavoritesHeader.visibility =
                            if (visible) View.VISIBLE else View.GONE
                    }
                }
                // Loading
                launch {
                    homeViewModel.isLoading.collect { isLoading ->
                        binding.progressBar.visibility =
                            if (isLoading) View.VISIBLE else View.GONE
                    }
                }
                // Lỗi / thông báo
                launch {
                    homeViewModel.errorMessage.collect { message ->
                        binding.tvMessage.visibility =
                            if (message != null) View.VISIBLE else View.GONE
                        binding.tvMessage.text = message
                    }
                }
            }
        }
    }

    // ---- Toolbar menu: nút tìm kiếm mở MainActivity (màn hình cũ) ----
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_home, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_search -> {
                // MainActivity giữ nguyên là màn hình TÌM KIẾM (bản cũ, DI thủ công)
                startActivity(Intent(this, MainActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
