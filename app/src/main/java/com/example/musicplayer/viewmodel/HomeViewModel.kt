package com.example.musicplayer.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.musicplayer.model.GenreItem
import com.example.musicplayer.model.PlaylistItem
import com.example.musicplayer.model.SongItem
import com.example.musicplayer.repository.MusicRepository
import com.example.musicplayer.repository.RecentPlayedStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val recentStore: RecentPlayedStore
) : ViewModel() {

    // Thể loại nhạc — dữ liệu tĩnh, không cần gọi API
    val genres: List<GenreItem> = GenreItem.all

    // ---- State: Playlist nổi bật ----
    private val _featuredPlaylists = MutableStateFlow<List<PlaylistItem>>(emptyList())
    val featuredPlaylists: StateFlow<List<PlaylistItem>> = _featuredPlaylists.asStateFlow()

    // ---- State: Nghe gần đây ----
    private val _recentSongs = MutableStateFlow<List<SongItem>>(emptyList())
    val recentSongs: StateFlow<List<SongItem>> = _recentSongs.asStateFlow()

    // ---- State: Đang loading? / Lỗi ----
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        // Khi ViewModel được tạo → load dữ liệu Home ngay lập tức
        loadHome()
        loadRecent()
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
     * Đọc lại danh sách "Nghe gần đây" từ RecentPlayedStore.
     * Được gọi khi Activity quay lại Home (onResume) để cập nhật bài mới nghe.
     */
    fun loadRecent() {
        _recentSongs.value = recentStore.getRecent()
    }
}
