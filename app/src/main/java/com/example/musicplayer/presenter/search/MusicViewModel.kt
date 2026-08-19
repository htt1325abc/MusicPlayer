package com.example.musicplayer.presenter.search

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.example.musicplayer.common.IViewModel
import com.example.musicplayer.domain.repository.MusicRepository
import com.example.musicplayer.model.SongItem
import com.example.musicplayer.service.PlaybackController
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * MusicViewModel — ViewModel cho màn hình TÌM KIẾM (MainActivity).
 *
 * DI CHUYỂN từ `viewmodel/` → `presenter/search/` (cùng package với MainActivity)
 * và ĐỔI sang kế thừa [IViewModel] + pattern `onState()` — đúng convention project MẪU.
 *
 * ⚠️ THAY ĐỔI so với bản cũ:
 * → Bỏ hẳn luồng `playSong()/streamUrl/currentSong` — MainActivity giờ dùng
 *   `BasePlayerActivity.playQueue()` (đưa cả queue cho Service, Service tự fetch URL
 *   và auto-advance) như Home/SongList. Giảm code trùng lặp, nhất quán toàn app.
 */
class MusicViewModel(
    application: Application,
    private val repository: MusicRepository,
    private val playbackController: PlaybackController
) : IViewModel<MusicState>(application) {

    // ---- State: Danh sách bài hát ----
    private val _songs = MutableStateFlow<List<SongItem>>(emptyList())
    internal val songs = _songs.asStateFlow()

    // ---- State: Yêu thích (Room) ----
    private val _favorites = MutableStateFlow<List<SongItem>>(emptyList())
    internal val favoriteIds: StateFlow<Set<String>> = _favorites
        .map { list -> list.map { it.encodeId }.toSet() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    // ---- State: Lỗi ----
    private val _errorMessage = MutableStateFlow<String?>(null)
    internal val errorMessage = _errorMessage.asStateFlow()

    // Job debounce search — cancel request cũ khi user gõ nhanh
    private var searchJob: Job? = null

    init {
        observeFavorites()
    }

    /**
     * Pattern COMMAND — UI gửi State, ViewModel quyết định xử lý.
     */
    override fun onState(state: MusicState) {
        when (state) {
            is MusicState.Search -> search(state.query)
            is MusicState.ToggleFavorite -> toggleFavorite(state.song)
            // Next/Previous/TogglePlayPause → PlaybackController → MusicService (PHẦN 1)
            MusicState.Next -> playbackController.next()
            MusicState.Previous -> playbackController.previous()
            MusicState.TogglePlayPause -> playbackController.togglePlayPause()
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
     * Tìm kiếm bài hát với debounce 500ms.
     *
     * TẠI SAO cần debounce?
     * → User gõ "Sơn Tùng" → phát sinh 8 ký tự → 8 API call liên tiếp
     * → Debounce 500ms: chờ user ngừng gõ 500ms mới gọi API → giảm tải server.
     */
    private fun search(query: String) {
        // Cancel search trước đó (nếu user vẫn đang gõ)
        searchJob?.cancel()

        if (query.isBlank()) {
            _songs.value = emptyList()
            _errorMessage.value = null
            return
        }

        searchJob = launchBlock {
            // Đợi 500ms — nếu bị cancel (user gõ thêm) thì dừng luôn
            delay(500)

            setLoading(true)
            _errorMessage.value = null

            repository.searchSongs(query)
                .onSuccess { songList ->
                    _songs.value = songList
                    if (songList.isEmpty()) {
                        _errorMessage.value = "Không tìm thấy bài hát \"$query\""
                    }
                }
                .onFailure { error ->
                    _songs.value = emptyList()
                    _errorMessage.value = error.message
                }

            setLoading(false)
        }
    }
}

/**
 * Sealed class State của màn hình tìm kiếm — khai báo cuối file.
 */
sealed class MusicState : IViewModel.IState {
    data class Search(val query: String) : MusicState()
    data class ToggleFavorite(val song: SongItem) : MusicState()
    data object Next : MusicState()
    data object Previous : MusicState()
    data object TogglePlayPause : MusicState()
}
