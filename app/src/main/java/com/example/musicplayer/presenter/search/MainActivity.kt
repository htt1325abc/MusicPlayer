package com.example.musicplayer.presenter.search

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.musicplayer.adapter.SongAdapter
import com.example.musicplayer.databinding.ActivityMainBinding
import com.example.musicplayer.presenter.base.BasePlayerActivity
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

/**
 * MainActivity — màn hình TÌM KIẾM (không còn là launcher, mở từ nút search trên Home).
 *
 * DI CHUYỂN từ package gốc → `presenter/search/` và MIGRATE SANG BasePlayerActivity (→ IActivity).
 *
 * ⚠️ THAY ĐỔI LỚN so với bản cũ (để nhất quán toàn app):
 * → Trước: Activity tự bind service riêng + `viewModel.playSong()` (fetch URL rồi gửi cho
 *   Service) + tự viết mini player.
 * → Sau : kế thừa BasePlayerActivity — phần bind service + mini player + playQueue (đưa cả
 *   danh sách cho Service, Service tự fetch URL & auto-advance) dùng chung với Home/SongList.
 * → next/prev trên mini player: onNext()/onPrevious() → viewModel.onState(MusicState.Next/Previous).
 */
class MainActivity : BasePlayerActivity<ActivityMainBinding, MusicViewModel, MusicState>() {

    override fun getLazyViewModel() = viewModel<MusicViewModel>()
    override fun getLazyViewBinding() = lazy { ActivityMainBinding.inflate(layoutInflater) }

    private lateinit var songAdapter: SongAdapter

    // ---- Permission request cho notification (Android 13+) ----
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(this, "Bật notification để xem bài đang phát", Toast.LENGTH_SHORT).show()
        }
    }

    override fun setupInit() {
        super.setupInit()
        // Edge-to-edge phải gọi TRƯỚC setContentView (viewBinding chưa inflate — không được
        // truy cập binding ở đây, padding insets sẽ xử lý trong initViews).
        enableEdgeToEdge()
    }

    override fun initViews(savedInstanceState: Bundle?) {
        // Edge-to-edge: padding cho system bars
        ViewCompat.setOnApplyWindowInsetsListener(viewBinding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupRecyclerView()
        setupSearchInput()
        setupMiniPlayer()
        bindPlayerService()
        requestNotificationPermission()
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
                // Tập id yêu thích (Room) → cập nhật icon trái tim
                launch {
                    viewModel.favoriteIds.collect { ids ->
                        songAdapter.updateFavorites(ids)
                    }
                }
                // Lỗi / thông báo
                launch {
                    viewModel.errorMessage.collect { message ->
                        if (message != null) {
                            viewBinding.tvMessage.text = message
                            viewBinding.tvMessage.visibility = View.VISIBLE
                        } else {
                            viewBinding.tvMessage.visibility = View.GONE
                        }
                    }
                }
            }
        }
    }

    /**
     * Setup RecyclerView với SongAdapter.
     * Bấm bài → playQueue(cả danh sách, vị trí) → Service tự phát + auto-advance.
     */
    private fun setupRecyclerView() {
        songAdapter = SongAdapter(
            onItemClick = { _, position ->
                playQueue(viewModel.songs.value, position)
            },
            onFavoriteClick = { song, _ ->
                viewModel.onState(MusicState.ToggleFavorite(song))
            }
        )
        viewBinding.rvSongs.adapter = songAdapter
    }

    /**
     * Setup thanh tìm kiếm — TextWatcher + debounce (500ms bên trong ViewModel).
     */
    private fun setupSearchInput() {
        viewBinding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                // Gửi State Search xuống ViewModel (có debounce bên trong)
                viewModel.onState(MusicState.Search(s?.toString()?.trim() ?: ""))
            }
        })

        // Bấm nút Search trên bàn phím → search ngay (không đợi debounce)
        viewBinding.etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val query = viewBinding.etSearch.text?.toString()?.trim() ?: ""
                viewModel.onState(MusicState.Search(query))
                true
            } else false
        }
    }

    /**
     * Bấm nút Next trên mini player → gửi State xuống ViewModel (PHẦN 1).
     */
    override fun onNext() {
        viewModel.onState(MusicState.Next)
    }

    override fun onPrevious() {
        viewModel.onState(MusicState.Previous)
    }

    /**
     * Xin permission POST_NOTIFICATIONS cho Android 13+.
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
}
