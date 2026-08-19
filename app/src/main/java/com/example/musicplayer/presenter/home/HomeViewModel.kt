package com.example.musicplayer.presenter.home

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.example.musicplayer.common.IViewModel
import com.example.musicplayer.data.local.repository.RecentPlayedStore
import com.example.musicplayer.domain.repository.MusicRepository
import com.example.musicplayer.model.GenreItem
import com.example.musicplayer.model.PlaylistItem
import com.example.musicplayer.model.SongItem
import com.example.musicplayer.service.PlaybackController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * HomeViewModel — quản lý toàn bộ dữ liệu màn hình Home.
 *
 * DI CHUYỂN từ `viewmodel/` → `presenter/home/` (cùng package với HomeActivity)
 * và ĐỔI sang kế thừa [IViewModel] — đúng convention project MẪU:
 *   - Mẫu: `HomeViewModel : IViewModel<HomeState>` + sealed class `HomeState`
 *     khai báo cuối file.
 *   - UI gọi `viewModel.onState(HomeState.Xxx)` thay vì gọi hàm public trực tiếp.
 *
 * StateFlow giữ nguyên (Mẫu cũng dùng StateFlow cho categories/collections).
 */
class HomeViewModel(
    application: Application,
    private val repository: MusicRepository,
    private val recentStore: RecentPlayedStore,
    private val playbackController: PlaybackController
) : IViewModel<HomeState>(application) {

    // Thể loại nhạc — dữ liệu tĩnh, không cần gọi API
    val genres: List<GenreItem> = GenreItem.all

    // ---- State: Playlist nổi bật ----
    private val _featuredPlaylists = MutableStateFlow<List<PlaylistItem>>(emptyList())
    internal val featuredPlaylists = _featuredPlaylists.asStateFlow()

    // ---- State: Nghe gần đây (Room — tự cập nhật qua Flow) ----
    private val _recentSongs = MutableStateFlow<List<SongItem>>(emptyList())
    internal val recentSongs = _recentSongs.asStateFlow()

    // ---- State: Yêu thích (Room) ----
    private val _favorites = MutableStateFlow<List<SongItem>>(emptyList())
    internal val favorites = _favorites.asStateFlow()

    // ---- State: Playlist đã lưu (Room — bookmark, PHẦN 3) ----
    private val _savedPlaylists = MutableStateFlow<List<PlaylistItem>>(emptyList())
    internal val savedPlaylists = _savedPlaylists.asStateFlow()

    // Tập id bài đang yêu thích — Adapter dùng để đổi icon trái tim trên từng dòng
    internal val favoriteIds: StateFlow<Set<String>> = _favorites
        .map { list -> list.map { it.encodeId }.toSet() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    // Tập id playlist đã lưu — Adapter dùng để đổi icon bookmark trên từng card
    internal val savedPlaylistIds: StateFlow<Set<String>> = _savedPlaylists
        .map { list -> list.map { it.encodeId }.toSet() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    // ---- State: Lỗi ----
    private val _errorMessage = MutableStateFlow<String?>(null)
    internal val errorMessage = _errorMessage.asStateFlow()

    init {
        // Room Flow: collect 1 lần, tự emit lại khi bảng thay đổi (single source of truth)
        observeRecentSongs()
        observeFavorites()
        observeSavedPlaylists()
        // Load playlist nổi bật ngay khi ViewModel được tạo (không cần Activity gọi)
        onState(HomeState.FetchFeaturedPlaylists)
    }

    /**
     * Pattern COMMAND (giống mẫu): UI gửi State, ViewModel quyết định xử lý.
     */
    override fun onState(state: HomeState) {
        when (state) {
            HomeState.FetchFeaturedPlaylists -> loadHome()
            is HomeState.ToggleFavorite -> toggleFavorite(state.song)
            is HomeState.ToggleSavePlaylist -> toggleSavePlaylist(state.playlist)
            // Next/Previous/TogglePlayPause → PlaybackController → MusicService (PHẦN 1)
            HomeState.Next -> playbackController.next()
            HomeState.Previous -> playbackController.previous()
            HomeState.TogglePlayPause -> playbackController.togglePlayPause()
        }
    }

    private fun observeRecentSongs() {
        launchBlock {
            recentStore.observeRecent().collect { list ->
                _recentSongs.value = list
            }
        }
    }

    private fun observeFavorites() {
        launchBlock {
            repository.observeFavorites().collect { list ->
                _favorites.value = list
            }
        }
    }

    private fun observeSavedPlaylists() {
        launchBlock {
            repository.observeSavedPlaylists().collect { list ->
                _savedPlaylists.value = list
            }
        }
    }

    /**
     * Tải playlist nổi bật từ server.
     */
    private fun loadHome() {
        launchBlock {
            setLoading(true)
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
            setLoading(false)
        }
    }

    /**
     * Bật/tắt yêu thích 1 bài (ghi vào Room qua repository).
     */
    private fun toggleFavorite(song: SongItem) {
        launchBlock {
            repository.toggleFavorite(song)
        }
    }

    /**
     * Bật/tắt lưu 1 playlist (ghi vào Room qua repository) — PHẦN 3.
     */
    private fun toggleSavePlaylist(playlist: PlaylistItem) {
        launchBlock {
            repository.toggleSavePlaylist(playlist)
        }
    }
}

/**
 * Sealed class State của màn hình Home — khai báo CUỐI file (đúng convention mẫu).
 * Mỗi object/data class là 1 "ý định" mà UI gửi tới ViewModel.
 */
sealed class HomeState : IViewModel.IState {
    data object FetchFeaturedPlaylists : HomeState()
    data class ToggleFavorite(val song: SongItem) : HomeState()
    data class ToggleSavePlaylist(val playlist: PlaylistItem) : HomeState()
    data object Next : HomeState()
    data object Previous : HomeState()
    data object TogglePlayPause : HomeState()
}
