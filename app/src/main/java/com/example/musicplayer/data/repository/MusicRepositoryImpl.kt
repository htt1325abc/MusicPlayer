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
            if (response.isSuccess) {
                Result.success(response.results)
            } else {
                Result.failure(Exception(response.headers.error_message ?: "Không tìm thấy kết quả"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Lỗi mạng: ${e.localizedMessage}"))
        }
    }

    override suspend fun getStreamUrl(songId: String): Result<String> {
        return try {
            // Jamendo trả URL stream ngay trong field `audio` của track
            // (KHÔNG còn khái niệm VIP / map {"128":..., "320":...} như ZingMP3).
            // ⚠️ Một số track KHÔNG có bản mp32 → Jamendo trả danh sách RỖNG
            // (không phải lỗi HTTP) → phải fallback về mp31 (96kbps, mọi track đều có).
            val audioUrl = fetchAudioUrl(songId, "mp32") ?: fetchAudioUrl(songId, "mp31")
            if (!audioUrl.isNullOrBlank()) {
                Result.success(audioUrl)
            } else {
                Result.failure(Exception("Bài hát không có link phát"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Lỗi mạng: ${e.localizedMessage}"))
        }
    }

    /**
     * Gọi tracks/?id=<songId>&audioformat=<format>, trả URL audio nếu tìm thấy.
     * Trả null khi request fail hoặc danh sách rỗng (track không có bản format đó).
     */
    private suspend fun fetchAudioUrl(songId: String, format: String): String? {
        val response = apiService.getStreamUrl(songId, audioFormat = format)
        if (!response.isSuccess) return null
        return response.results.firstOrNull()?.audio?.takeIf { it.isNotBlank() }
    }

    override suspend fun getChart(): Result<List<ChartData>> {
        return try {
            val response = apiService.getChart()
            if (response.isSuccess) {
                Result.success(response.results)
            } else {
                Result.failure(Exception(response.headers.error_message ?: "Không tải được bảng xếp hạng"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Lỗi mạng: ${e.localizedMessage}"))
        }
    }

    override suspend fun getPlaylist(playlistId: String): Result<PlaylistData> {
        return try {
            val response = apiService.getPlaylistDetail(playlistId)
            if (response.isSuccess) {
                val playlist = response.results.firstOrNull()
                if (playlist != null) {
                    // Track trong /albums/tracks KHÔNG có artist_name/image
                    // → điền fallback từ thông tin ALBUM (artist_name, image) nếu thiếu
                    val songs = playlist.songs.map { song ->
                        song.copy(
                            artistsNames = if (song.artistsNames.isNullOrBlank())
                                playlist.artistsNames ?: "" else song.artistsNames,
                            thumbnail = song.thumbnail ?: playlist.thumbnail,
                            thumbnailM = song.thumbnailM ?: playlist.thumbnail
                        )
                    }
                    Result.success(playlist.copy(songs = songs))
                } else {
                    Result.failure(Exception("Không tìm thấy playlist"))
                }
            } else {
                Result.failure(Exception(response.headers.error_message ?: "Không tìm thấy playlist"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Lỗi mạng: ${e.localizedMessage}"))
        }
    }

    override suspend fun getFeaturedPlaylists(): Result<List<PlaylistItem>> {
        return try {
            val response = apiService.getFeaturedPlaylists()
            if (response.isSuccess) {
                // Lọc bỏ playlist tên trống (dữ liệu Jamendo đôi khi có playlist
                // không đặt tên → UI Home hiện thẻ trống xấu xí).
                Result.success(response.results.filter { it.title.isNotBlank() })
            } else {
                Result.failure(Exception(response.headers.error_message ?: "Không tải được playlist"))
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
