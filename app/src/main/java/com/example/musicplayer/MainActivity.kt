package com.example.musicplayer

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions
import com.example.musicplayer.adapter.SongAdapter
import com.example.musicplayer.databinding.ActivityMainBinding
import com.example.musicplayer.model.SongItem
import com.example.musicplayer.service.MusicService
import com.example.musicplayer.viewmodel.MusicViewModel
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

/**
 * Màn hình chính — Tìm kiếm + phát nhạc.
 *
 * Kiến trúc MVVM:
 * - View (Activity): hiển thị UI, bắt sự kiện user
 * - ViewModel: xử lý logic, giữ state
 * - Model: data class + Repository
 *
 * Activity KHÔNG chứa business logic, chỉ:
 * 1. Observe state từ ViewModel → cập nhật UI
 * 2. Bắt sự kiện user → gọi method ViewModel
 * 3. Quản lý ServiceConnection cho MusicService
 */
class MainActivity : AppCompatActivity() {

    // ViewBinding — truy cập view an toàn, không cần findViewById
    private lateinit var binding: ActivityMainBinding

    // ViewModel — survive configuration change (xoay màn hình)
    // ⚠️ KHỞI TẠO QUA KOIN: MusicViewModel giờ cần dependency (MusicRepository + PlaybackController)
    // → `by viewModel()` để Koin tự bơm, không dùng ViewModelProvider() thủ công nữa.
    private val viewModel: MusicViewModel by viewModel()

    // Adapter cho RecyclerView
    private lateinit var songAdapter: SongAdapter

    // Reference đến MusicService (qua Binder)
    private var musicService: MusicService? = null
    private var isBound = false

    // ---- Pending playback ----
    // TẠI SAO cần? → User có thể bấm bài hát TRƯỚC khi Service bind xong (musicService = null).
    // Nếu không lưu lại, URL stream sẽ bị mất và không phát được.
    // Lưu song + url vào đây, phát ngay khi Service sẵn sàng.
    private var pendingSong: SongItem? = null
    private var pendingUrl: String? = null

    // ---- ServiceConnection: callback khi bind/unbind MusicService ----
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MusicService.MusicBinder
            musicService = binder.getService()
            isBound = true

            // Lắng nghe trạng thái phát nhạc từ Service → cập nhật nút play/pause
            musicService?.onPlaybackStateChanged = { isPlaying ->
                runOnUiThread {
                    binding.btnMiniPlayPause.setImageResource(
                        if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
                    )
                }
            }

            // Lắng nghe lỗi từ Service
            musicService?.onError = { message ->
                runOnUiThread {
                    Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
                }
            }

            // Service đã sẵn sàng → phát bài đang chờ (nếu có)
            // Đưa cả queue để Service phát tiếp bài sau khi hết bài này
            pendingUrl?.let { url ->
                val queue = viewModel.getCurrentQueue()
                val song = pendingSong
                val startIndex = queue.indexOfFirst { it.encodeId == song?.encodeId }
                    .coerceAtLeast(0)
                musicService?.playQueue(queue, startIndex, preFetchedUrl = url)
                pendingUrl = null
                pendingSong = null
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            musicService = null
            isBound = false
        }
    }

    // ---- Permission request cho notification (Android 13+) ----
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        // Không cần xử lý gì đặc biệt, app vẫn hoạt động không có notification
        if (!isGranted) {
            Toast.makeText(this, "Bật notification để xem bài đang phát", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Khởi tạo ViewBinding
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Edge-to-edge: padding cho system bars
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Xin permission notification (Android 13+)
        requestNotificationPermission()

        // Setup UI components
        setupRecyclerView()
        setupSearchInput()
        setupMiniPlayer()

        // Observe state từ ViewModel → cập nhật UI
        observeViewModel()

        // Bind MusicService
        bindMusicService()
    }

    /**
     * Setup RecyclerView với SongAdapter.
     * Khi user bấm vào bài hát → gọi ViewModel.playSong()
     */
    private fun setupRecyclerView() {
        // Callback giờ có cả vị trí (position) — ViewModel nhớ danh sách đang hiển thị
        // để Service tự phát bài kế tiếp khi bài hiện tại hết (auto-advance)
        // ⚠️ SongAdapter (PHẦN 2) cần 2 callback: bấm bài để phát + bấm tim để yêu thích
        songAdapter = SongAdapter(
            onItemClick = { song, _ ->
                viewModel.playSong(song)
            },
            onFavoriteClick = { song, _ ->
                viewModel.toggleFavorite(song)   // ghi Room qua Repository
            }
        )
        binding.rvSongs.adapter = songAdapter
        // LayoutManager đã set trong XML (app:layoutManager)
    }

    /**
     * Setup thanh tìm kiếm.
     *
     * Dùng TextWatcher thay vì SearchView vì:
     * → TextInputLayout đẹp hơn, nhất quán với Material3
     * → TextWatcher cho phép debounce search dễ dàng
     */
    private fun setupSearchInput() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                // Mỗi lần text thay đổi → gọi ViewModel.search() (có debounce 500ms bên trong)
                viewModel.search(s?.toString()?.trim() ?: "")
            }
        })

        // Bấm nút Search trên bàn phím → search ngay (không đợi debounce)
        binding.etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val query = binding.etSearch.text?.toString()?.trim() ?: ""
                viewModel.search(query)
                true
            } else false
        }
    }

    /**
     * Setup mini player bar — nút play/pause
     */
    private fun setupMiniPlayer() {
        binding.btnMiniPlayPause.setOnClickListener {
            musicService?.togglePlayPause()
        }
    }

    /**
     * Observe StateFlow từ ViewModel.
     *
     * TẠI SAO dùng repeatOnLifecycle(STARTED)?
     * → Chỉ collect khi Activity ở trạng thái STARTED trở lên
     * → Tự dừng collect khi Activity ở background → tiết kiệm tài nguyên
     * → Tự resume collect khi Activity quay lại foreground
     */
    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Observe danh sách bài hát
                launch {
                    viewModel.songs.collect { songs ->
                        songAdapter.submitList(songs)
                        // Hiện RecyclerView nếu có data, ẩn message
                        binding.rvSongs.visibility = if (songs.isNotEmpty()) View.VISIBLE else View.GONE
                    }
                }

                // Observe danh sách id yêu thích (PHẦN 2 - Room) → cập nhật icon trái tim
                launch {
                    viewModel.favoriteIds.collect { ids ->
                        songAdapter.updateFavorites(ids)
                    }
                }

                // Observe trạng thái loading
                launch {
                    viewModel.isLoading.collect { isLoading ->
                        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
                    }
                }

                // Observe thông báo lỗi
                launch {
                    viewModel.errorMessage.collect { message ->
                        if (message != null) {
                            binding.tvMessage.text = message
                            binding.tvMessage.visibility = View.VISIBLE
                        } else {
                            binding.tvMessage.visibility = View.GONE
                        }
                    }
                }

                // Observe bài đang phát → hiện mini player
                launch {
                    viewModel.currentSong.collect { song ->
                        if (song != null) {
                            showMiniPlayer(song)
                        }
                    }
                }

                // Observe stream URL → gửi cho MusicService phát nhạc
                launch {
                    viewModel.streamUrl.collect { url ->
                        if (url != null) {
                            val song = viewModel.currentSong.value
                            // Đưa CẢ danh sách đang hiển thị cho Service → khi hết bài
                            // này Service tự phát bài kế tiếp (auto-advance).
                            // URL bài đầu đã có sẵn → truyền luôn để không fetch lại.
                            val queue = viewModel.getCurrentQueue()
                            val startIndex = queue.indexOfFirst { it.encodeId == song?.encodeId }
                                .coerceAtLeast(0)
                            if (musicService != null) {
                                // Service đã sẵn sàng → phát luôn
                                musicService?.playQueue(queue, startIndex, preFetchedUrl = url)
                            } else {
                                // Service chưa bind xong → lưu lại, phát khi connected
                                pendingUrl = url
                                pendingSong = song
                            }
                            // Đánh dấu đã consume URL → không phát lại khi observe lại
                            viewModel.onStreamUrlConsumed()
                        }
                    }
                }
            }
        }
    }

    /**
     * Hiện mini player bar với thông tin bài đang phát
     */
    private fun showMiniPlayer(song: SongItem) {
        binding.miniPlayerBar.visibility = View.VISIBLE
        binding.tvMiniTitle.text = song.title
        binding.tvMiniArtist.text = song.artistsNames

        // Load thumbnail vào mini player
        Glide.with(this)
            .load(song.thumbnail)
            .apply(
                RequestOptions()
                    .placeholder(R.drawable.ic_music_note)
                    .error(R.drawable.ic_music_note)
                    .transform(RoundedCorners(8))
            )
            .into(binding.ivMiniThumbnail)
    }

    /**
     * Bind MusicService — tạo kết nối để điều khiển MediaPlayer
     */
    private fun bindMusicService() {
        val intent = Intent(this, MusicService::class.java)
        bindService(intent, serviceConnection, BIND_AUTO_CREATE)
    }

    /**
     * Xin permission POST_NOTIFICATIONS cho Android 13+
     */
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
    }
}