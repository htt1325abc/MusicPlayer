package com.example.musicplayer.presenter.songlist

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.example.musicplayer.common.IViewModel
import com.example.musicplayer.domain.repository.MusicRepository
import com.example.musicplayer.model.SongItem
import com.example.musicplayer.service.PlaybackController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * PlaylistViewModel — quản lý danh sách bài hát của 1 màn hình danh sách.
 *
 * DI CHUYỂN từ `viewmodel/` → `presenter/songlist/` (cùng package với SongListActivity)
 * và ĐỔI sang kế thừa [IViewModel] + pattern `onState()` — đúng convention project MẪU.
 *
 * Màn hình này được DÙNG LẠI cho 2 trường hợp:
 * 1. Bấm vào 1 playlist → PlaylistState.LoadPlaylist(playlistId)
 * 2. Bấm vào 1 thể loại  → PlaylistState.SearchSongs(title, keyword)
 */
class PlaylistViewModel(
    application: Application,
    private val repository: MusicRepository,
    private val playbackController: PlaybackController
) : IViewModel<PlaylistState>(application) {

    // ---- State: Danh sách bài hát ----
    private val _songs = MutableStateFlow<List<SongItem>>(emptyList())
    internal val songs = _songs.asStateFlow()

    // ---- State: Tiêu đề màn hình (tên playlist hoặc thể loại) ----
    private val _title = MutableStateFlow<String?>(null)
    internal val title = _title.asStateFlow()

    // ---- State: Yêu thích (Room) ----
    private val _favorites = MutableStateFlow<List<SongItem>>(emptyList())
    internal val favoriteIds: StateFlow<Set<String>> = _favorites
        .map { list -> list.map { it.encodeId }.toSet() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    // ---- State: Lỗi ----
    private val _errorMessage = MutableStateFlow<String?>(null)
    internal val errorMessage = _errorMessage.asStateFlow()

    init {
        observeFavorites()
    }

    /**
     * Pattern COMMAND — UI gửi State, ViewModel quyết định xử lý.
     */
    override fun onState(state: PlaylistState) {
        when (state) {
            is PlaylistState.LoadPlaylist -> loadPlaylist(state.playlistId)
            is PlaylistState.SearchSongs -> {
                _title.value = state.title
                searchSongs(state.keyword)
            }
            is PlaylistState.ToggleFavorite -> toggleFavorite(state.song)
            // Next/Previous/TogglePlayPause → PlaybackController → MusicService (PHẦN 1)
            PlaylistState.Next -> playbackController.next()
            PlaylistState.Previous -> playbackController.previous()
            PlaylistState.TogglePlayPause -> playbackController.togglePlayPause()
        }
    }

    private fun observeFavorites() {
        launchBlock {
            repository.observeFavorites().collect { list ->
                _favorites.value = list
            }
        }
    }

    private fun toggleFavorite(song: SongItem) {
        launchBlock {
            repository.toggleFavorite(song)
        }
    }

    /**
     * Tải danh sách bài hát trong 1 playlist.
     */
    private fun loadPlaylist(playlistId: String) {
        launchBlock {
            setLoading(true)
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
            setLoading(false)
        }
    }

    /**
     * Tải bài hát theo từ khóa (dùng cho thể loại — search theo tên thể loại).
     */
    private fun searchSongs(keyword: String) {
        launchBlock {
            setLoading(true)
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
            setLoading(false)
        }
    }
}

/**
 * Sealed class State của màn hình danh sách bài hát — khai báo cuối file.
 */
sealed class PlaylistState : IViewModel.IState {
    data class LoadPlaylist(val playlistId: String) : PlaylistState()
    data class SearchSongs(val title: String, val keyword: String) : PlaylistState()
    data class ToggleFavorite(val song: SongItem) : PlaylistState()
    data object Next : PlaylistState()
    data object Previous : PlaylistState()
    data object TogglePlayPause : PlaylistState()
}
