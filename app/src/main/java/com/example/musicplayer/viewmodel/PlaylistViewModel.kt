package com.example.musicplayer.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.musicplayer.model.SongItem
import com.example.musicplayer.repository.MusicRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * PlaylistViewModel — quản lý danh sách bài hát của 1 màn hình danh sách.
 *
 * Màn hình này được DÙNG LẠI cho 2 trường hợp (PHẦN A):
 * 1. Bấm vào 1 playlist → load bằng `loadPlaylist(playlistId)`
 * 2. Bấm vào 1 thể loại  → load bằng `searchSongs(genreName)` (search theo tên)
 *
 * Cũng dùng Koin inject `MusicRepository` qua constructor như HomeViewModel.
 */
class PlaylistViewModel(
    private val repository: MusicRepository
) : ViewModel() {

    // ---- State: Danh sách bài hát ----
    private val _songs = MutableStateFlow<List<SongItem>>(emptyList())
    val songs: StateFlow<List<SongItem>> = _songs.asStateFlow()

    // ---- State: Tiêu đề màn hình (tên playlist hoặc thể loại) ----
    private val _title = MutableStateFlow<String?>(null)
    val title: StateFlow<String?> = _title.asStateFlow()

    // ---- State: Loading / Lỗi ----
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    /**
     * Tải danh sách bài hát trong 1 playlist.
     */
    fun loadPlaylist(playlistId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            repository.getPlaylist(playlistId)
                .onSuccess { playlist ->
                    _title.value = playlist.title
                    _songs.value = playlist.songs
                    if (playlist.songs.isEmpty()) {
                        _errorMessage.value = "Playlist này hiện không có bài hát"
                    }
                }
                .onFailure { error ->
                    _errorMessage.value = error.message
                }
            _isLoading.value = false
        }
    }

    /**
     * Tải bài hát theo từ khóa (dùng cho thể loại — search theo tên thể loại).
     */
    fun searchSongs(keyword: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            repository.searchSongs(keyword)
                .onSuccess { list ->
                    _songs.value = list
                    if (list.isEmpty()) {
                        _errorMessage.value = "Không tìm thấy bài hát thể loại \"$keyword\""
                    }
                }
                .onFailure { error ->
                    _errorMessage.value = error.message
                }
            _isLoading.value = false
        }
    }

    /**
     * Set tiêu đề màn hình (khi vào từ thể loại — chưa biết tên playlist).
     */
    fun setTitle(title: String) {
        _title.value = title
    }
}
