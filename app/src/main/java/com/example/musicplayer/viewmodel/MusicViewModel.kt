package com.example.musicplayer.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.musicplayer.model.SongItem
import com.example.musicplayer.repository.MusicRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel cho màn hình chính — quản lý state theo MVVM.
 *
 * TẠI SAO dùng ViewModel?
 * → ViewModel tồn tại qua configuration change (xoay màn hình)
 * → Giữ data tìm kiếm không bị mất khi xoay màn hình
 * → viewModelScope tự cancel coroutine khi ViewModel bị destroy → tránh memory leak
 *
 * TẠI SAO dùng StateFlow thay vì LiveData?
 * → StateFlow là Kotlin-native, hoạt động tốt với Coroutines
 * → Luôn có giá trị khởi tạo (không null) → an toàn hơn LiveData
 * → Có thể dùng combine, map, filter... như Flow operator
 * → LiveData vẫn dùng được, nhưng StateFlow phổ biến hơn trong project Kotlin mới
 */
class MusicViewModel : ViewModel() {

    private val repository = MusicRepository()

    // ---- State: Danh sách bài hát ----
    private val _songs = MutableStateFlow<List<SongItem>>(emptyList())
    val songs: StateFlow<List<SongItem>> = _songs.asStateFlow()

    // ---- State: Đang loading? ----
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // ---- State: Thông báo lỗi ----
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // ---- State: Bài đang phát (tên + ca sĩ hiển thị mini player) ----
    private val _currentSong = MutableStateFlow<SongItem?>(null)
    val currentSong: StateFlow<SongItem?> = _currentSong.asStateFlow()

    // ---- State: URL stream đang phát (gửi cho MusicService) ----
    private val _streamUrl = MutableStateFlow<String?>(null)
    val streamUrl: StateFlow<String?> = _streamUrl.asStateFlow()

    // Job debounce search — cancel request cũ khi user gõ nhanh
    private var searchJob: Job? = null

    // Danh sách bài đang hiển thị khi user bấm phát — dùng cho auto-advance
    // (Service cần cả danh sách để phát bài kế tiếp khi bài hiện tại hết)
    private var currentQueue: List<SongItem> = emptyList()

    /** Lấy danh sách bài đang hiển thị lúc user bấm phát (để Service phát tiếp theo) */
    fun getCurrentQueue(): List<SongItem> = currentQueue

    /**
     * Tìm kiếm bài hát với debounce 500ms.
     *
     * TẠI SAO cần debounce?
     * → User gõ "Sơn Tùng" → phát sinh 8 ký tự → 8 API call liên tiếp
     * → Debounce 500ms: chờ user ngừng gõ 500ms mới gọi API
     * → Giảm tải server, tiết kiệm bandwidth, UX mượt hơn
     */
    fun search(query: String) {
        // Cancel search trước đó (nếu user vẫn đang gõ)
        searchJob?.cancel()

        if (query.isBlank()) {
            _songs.value = emptyList()
            _errorMessage.value = null
            return
        }

        searchJob = viewModelScope.launch {
            // Đợi 500ms — nếu bị cancel (user gõ thêm) thì dừng luôn
            delay(500)

            _isLoading.value = true
            _errorMessage.value = null

            val result = repository.searchSongs(query)

            result.onSuccess { songList ->
                _songs.value = songList
                if (songList.isEmpty()) {
                    _errorMessage.value = "Không tìm thấy bài hát \"$query\""
                }
            }.onFailure { error ->
                _songs.value = emptyList()
                _errorMessage.value = error.message
            }

            _isLoading.value = false
        }
    }

    /**
     * Lấy link stream và phát bài hát được chọn.
     *
     * Flow: User bấm bài → gọi hàm này → lấy URL mp3 → Activity observe
     * streamUrl → gửi URL cho MusicService
     */
    fun playSong(song: SongItem) {
        // Nhớ danh sách đang hiển thị → Service tự phát bài kế tiếp khi hết bài này
        currentQueue = _songs.value
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            _currentSong.value = song

            val result = repository.getStreamUrl(song.encodeId)

            result.onSuccess { url ->
                _streamUrl.value = url
            }.onFailure { error ->
                _errorMessage.value = error.message
                _streamUrl.value = null
            }

            _isLoading.value = false
        }
    }

    /**
     * Reset stream URL sau khi đã gửi cho MusicService.
     * TẠI SAO cần reset? → Tránh phát lại bài cũ khi observe lại StateFlow
     */
    fun onStreamUrlConsumed() {
        _streamUrl.value = null
    }

    /**
     * Xóa thông báo lỗi (khi user dismiss)
     */
    fun clearError() {
        _errorMessage.value = null
    }
}
