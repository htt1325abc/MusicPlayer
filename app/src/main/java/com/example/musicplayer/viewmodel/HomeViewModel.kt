package com.example.musicplayer.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.musicplayer.model.GenreItem
import com.example.musicplayer.model.PlaylistItem
import com.example.musicplayer.model.SongItem
import com.example.musicplayer.repository.MusicRepository
import com.example.musicplayer.repository.RecentPlayedStore
import com.example.musicplayer.service.PlaybackController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * HomeViewModel — quản lý toàn bộ dữ liệu màn hình Home.
 *
 * ⚠️ KHÁC BIỆT KHI DÙNG KOIN (so với MusicViewModel cũ):
 * → MusicViewModel cũ tự `MusicRepository()` — ViewModel TỰ TẠO dependency.
 * → HomeViewModel nhận `MusicRepository` + `RecentPlayedStore` từ CONSTRUCTOR.
 *   ViewModel không biết ai tạo chúng, chỉ khai báo "tôi cần gì".
 * → Koin (qua viewModelModule) là người "bơm" (inject) vào.
 *   Kết quả: dễ test (truyền fake repository), dễ thay đổi implementation.
 *
 * Vì ViewModel có constructor tham số → KHÔNG dùng được `ViewModelProvider()`
 * thủ công như cũ, mà phải dùng Koin `by viewModel()` (xem HomeActivity).
 */
class HomeViewModel(
    private val repository: MusicRepository,
    private val recentStore: RecentPlayedStore,
    private val playbackController: PlaybackController
) : ViewModel() {

    // Thể loại nhạc — dữ liệu tĩnh, không cần gọi API
    val genres: List<GenreItem> = GenreItem.all

    // ---- State: Playlist nổi bật ----
    private val _featuredPlaylists = MutableStateFlow<List<PlaylistItem>>(emptyList())
    val featuredPlaylists: StateFlow<List<PlaylistItem>> = _featuredPlaylists.asStateFlow()

    // ---- State: Nghe gần đây (PHẦN 2: từ ROOM, tự cập nhật qua Flow) ----
    private val _recentSongs = MutableStateFlow<List<SongItem>>(emptyList())
    val recentSongs: StateFlow<List<SongItem>> = _recentSongs.asStateFlow()

    // ---- State: Yêu thích (PHẦN 2: từ ROOM) ----
    private val _favorites = MutableStateFlow<List<SongItem>>(emptyList())
    val favorites: StateFlow<List<SongItem>> = _favorites.asStateFlow()

    // Tập id bài đang yêu thích — Adapter dùng để đổi icon trái tim trên từng dòng
    val favoriteIds: StateFlow<Set<String>> = _favorites
        .map { list -> list.map { it.encodeId }.toSet() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    // ---- State: Đang loading? / Lỗi ----
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        // Khi ViewModel được tạo → load dữ liệu Home ngay lập tức
        loadHome()
        // Room Flow: collect 1 lần, tự emit lại khi bảng thay đổi (không cần loadRecent thủ công)
        observeRecentSongs()
        observeFavorites()
    }

    /**
     * Quan sát lịch sử "Nghe gần đây" từ Room.
     * Mỗi khi Activity phát bài mới (gọi `recentStore.add`), Room emit lại → UI tự cập nhật.
     */
    private fun observeRecentSongs() {
        viewModelScope.launch {
            recentStore.observeRecent().collect { list ->
                _recentSongs.value = list
            }
        }
    }

    /**
     * Quan sát danh sách yêu thích từ Room → cập nhật StateFlow favorites + favoriteIds.
     */
    private fun observeFavorites() {
        viewModelScope.launch {
            repository.observeFavorites().collect { list ->
                _favorites.value = list
            }
        }
    }

    /**
     * Tải playlist nổi bật từ server.
     */
    fun loadHome() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            repository.getFeaturedPlaylists()
                .onSuccess { list ->
                    _featuredPlaylists.value = list
                    if (list.isEmpty()) {
                        _errorMessage.value = "Không có playlist nào"
                    }
                }
                .onFailure { error ->
                    _errorMessage.value = error.message
                }
            _isLoading.value = false
        }
    }

    /**
     * Bật/tắt yêu thích 1 bài (ghi vào Room qua repository).
     * Adapter gọi khi user bấm trái tim trên dòng bài hát.
     */
    fun toggleFavorite(song: SongItem) {
        viewModelScope.launch {
            repository.toggleFavorite(song)
        }
    }

    // ---- Điều khiển phát nhạc (PHẦN 1: next/prev qua PlaybackController) ----
    // UI nút bấm → ViewModel.next()/previous() → PlaybackController → MusicService

    /** Chuyển bài kế tiếp */
    fun next() = playbackController.next()

    /** Chuyển về bài trước đó */
    fun previous() = playbackController.previous()

    /** Bật/tắt phát tạm dừng */
    fun togglePlayPause() = playbackController.togglePlayPause()
}
