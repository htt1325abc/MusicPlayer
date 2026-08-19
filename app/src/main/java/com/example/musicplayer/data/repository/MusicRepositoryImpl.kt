package com.example.musicplayer.data.repository

import com.example.musicplayer.data.local.dao.FavoriteSongDao
import com.example.musicplayer.data.local.dao.PlaylistDao
import com.example.musicplayer.data.local.mapper.FavoriteSongMapper
import com.example.musicplayer.data.local.mapper.PlaylistMapper
import com.example.musicplayer.domain.repository.MusicRepository
import com.example.musicplayer.model.ChartData
import com.example.musicplayer.model.PlaylistData
import com.example.musicplayer.model.PlaylistItem
import com.example.musicplayer.model.SongItem
import com.example.musicplayer.network.MusicApiService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * MusicRepositoryImpl — IMPLEMENTATION của [MusicRepository], nằm ở tầng `data/`
 * (giống project MẪU: interface ở domain, impl ở data).
 *
 * TẠI SAO là constructor-injected (nhận dependency từ Koin)?
 * → Repository KHÔNG tự quyết định lấy dependency ở đâu — Koin bơm qua `get()`:
 *     - `apiService`        (networkModule) — gọi Retrofit.
 *     - `favoriteSongDao`   (Room) — yêu thích.
 *     - `playlistDao`       (Room) — playlist đã lưu.
 *     - `favoriteMapper` / `playlistMapper` — chuyển Entity ↔ Model.
 *
 * TẠI SAO dùng Result<T> cho network?
 * → Kotlin built-in, bọc success value HOẶC exception → ViewModel chỉ cần
 *   `result.getOrNull()` hoặc `onSuccess/onFailure`.
 */
class MusicRepositoryImpl(
    private val apiService: MusicApiService,
    private val favoriteSongDao: FavoriteSongDao,
    private val playlistDao: PlaylistDao,
    private val favoriteMapper: FavoriteSongMapper,
    private val playlistMapper: PlaylistMapper
) : MusicRepository {

    // ============ NETWORK (Retrofit) ============

    override suspend fun searchSongs(query: String): Result<List<SongItem>> {
        return try {
            val response = apiService.searchSongs(query)
            if (response.success && response.data != null) {
                Result.success(response.data)
            } else {
                Result.failure(Exception(response.error ?: "Không tìm thấy kết quả"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Lỗi mạng: ${e.localizedMessage}"))
        }
    }

    override suspend fun getStreamUrl(songId: String): Result<String> {
        return try {
            val response = apiService.getStreamUrl(songId)
            if (response.success && response.data != null) {
                val url = response.data.getBestStreamUrl()
                if (url != null) {
                    Result.success(url)
                } else {
                    Result.failure(Exception("Bài hát VIP, không phát được miễn phí"))
                }
            } else {
                Result.failure(Exception(response.error ?: "Không lấy được link nhạc"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Lỗi mạng: ${e.localizedMessage}"))
        }
    }

    override suspend fun getChart(): Result<List<ChartData>> {
        return try {
            val response = apiService.getChart()
            if (response.success && response.data != null) {
                Result.success(response.data)
            } else {
                Result.failure(Exception(response.error ?: "Không tải được bảng xếp hạng"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Lỗi mạng: ${e.localizedMessage}"))
        }
    }

    override suspend fun getPlaylist(playlistId: String): Result<PlaylistData> {
        return try {
            val response = apiService.getPlaylistDetail(playlistId)
            if (response.success && response.data != null) {
                Result.success(response.data)
            } else {
                Result.failure(Exception(response.error ?: "Không tìm thấy playlist"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Lỗi mạng: ${e.localizedMessage}"))
        }
    }

    override suspend fun getFeaturedPlaylists(): Result<List<PlaylistItem>> {
        return try {
            val response = apiService.getFeaturedPlaylists()
            if (response.success && response.data != null) {
                Result.success(response.data)
            } else {
                Result.failure(Exception(response.error ?: "Không tải được playlist"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Lỗi mạng: ${e.localizedMessage}"))
        }
    }

    // ============ LOCAL — YÊU THÍCH (Room) ============

    /**
     * Quan sát danh sách bài yêu thích (mới thêm trước).
     * Room trả [Flow] → ViewModel collect 1 lần, UI tự cập nhật khi bảng đổi.
     */
    override fun observeFavorites(): Flow<List<SongItem>> =
        favoriteSongDao.observeAll()
            .map { entities -> entities.map(favoriteMapper::toModel) }

    override suspend fun isFavorite(songId: String): Boolean =
        favoriteSongDao.getById(songId) != null

    /**
     * Bật/tắt yêu thích 1 bài:
     * - Chưa thích → thêm vào bảng favorite_songs.
     * - Đã thích → xóa khỏi bảng.
     */
    override suspend fun toggleFavorite(song: SongItem) {
        if (favoriteSongDao.getById(song.encodeId) != null) {
            favoriteSongDao.deleteById(song.encodeId)
        } else {
            favoriteSongDao.insert(favoriteMapper.toEntity(song))
        }
    }

    // ============ LOCAL — PLAYLIST ĐÃ LƯU (Room) ============

    /**
     * Quan sát danh sách playlist đã lưu (lưu gần nhất trước).
     * Flow → section "Đã lưu" trên Home tự cập nhật khi user lưu/bỏ lưu.
     */
    override fun observeSavedPlaylists(): Flow<List<PlaylistItem>> =
        playlistDao.observeAll()
            .map { entities -> entities.map(playlistMapper::toModel) }

    override suspend fun isSavedPlaylist(playlistId: String): Boolean =
        playlistDao.getById(playlistId) != null

    /**
     * Bật/tắt lưu 1 playlist:
     * - Chưa lưu → thêm vào bảng saved_playlists (kèm savedAt = now qua mapper).
     * - Đã lưu → xóa khỏi bảng.
     */
    override suspend fun toggleSavePlaylist(playlist: PlaylistItem) {
        if (playlistDao.getById(playlist.encodeId) != null) {
            playlistDao.deleteById(playlist.encodeId)
        } else {
            playlistDao.insert(playlistMapper.toEntity(playlist))
        }
    }
}
