package com.example.musicplayer.domain.repository

import com.example.musicplayer.model.ChartData
import com.example.musicplayer.model.PlaylistData
import com.example.musicplayer.model.PlaylistItem
import com.example.musicplayer.model.SongItem
import kotlinx.coroutines.flow.Flow

/**
 * MusicRepository — INTERFACE của tầng Repository.
 *
 * TẠI SAO tách interface (domain) và impl (data) như project MẪU?
 * → Mẫu tách `GithubRepository` (interface) và `GithubRepositoryImpl` (impl):
 *   ViewModel chỉ phụ thuộc interface, KHÔNG biết data lấy từ đâu
 *   (Retrofit? Room? cache?).
 * → Lợi ích:
 *     1. Dễ TEST: truyền fake repository vào ViewModel.
 *     2. Dễ THAY thế: đổi server/DB mà ViewModel không cần sửa.
 *     3. Koin `singleOf(::MusicRepositoryImpl) bind MusicRepository::class`
 *        → nơi nào inject `MusicRepository` đều nhận đúng impl duy nhất.
 *
 * 2 nhóm method:
 * - Network: gọi API qua Retrofit (search, stream, chart, playlist).
 * - Local (Room): yêu thích + playlist đã lưu — trả Flow để UI tự cập nhật.
 */
interface MusicRepository {

    // ============ NETWORK (Retrofit) ============

    suspend fun searchSongs(query: String): Result<List<SongItem>>
    suspend fun getStreamUrl(songId: String): Result<String>
    suspend fun getChart(): Result<List<ChartData>>
    suspend fun getPlaylist(playlistId: String): Result<PlaylistData>
    suspend fun getFeaturedPlaylists(): Result<List<PlaylistItem>>

    // ============ LOCAL — YÊU THÍCH (Room) ============

    fun observeFavorites(): Flow<List<SongItem>>
    suspend fun isFavorite(songId: String): Boolean
    suspend fun toggleFavorite(song: SongItem)

    // ============ LOCAL — PLAYLIST ĐÃ LƯU (Room) ============

    fun observeSavedPlaylists(): Flow<List<PlaylistItem>>
    suspend fun isSavedPlaylist(playlistId: String): Boolean
    suspend fun toggleSavePlaylist(playlist: PlaylistItem)
}
