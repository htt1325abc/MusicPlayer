package com.example.musicplayer.repository

import com.example.musicplayer.data.local.dao.FavoriteSongDao
import com.example.musicplayer.data.local.entity.toFavoriteSongEntity
import com.example.musicplayer.data.local.entity.toSongItem
import com.example.musicplayer.model.ChartData
import com.example.musicplayer.model.PlaylistData
import com.example.musicplayer.model.PlaylistItem
import com.example.musicplayer.model.SongItem
import com.example.musicplayer.network.MusicApiService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Repository Pattern — lớp trung gian giữa ViewModel và Network.
 *
 * TẠI SAO cần Repository?
 * → ViewModel không nên biết data đến từ đâu (API, cache, database...)
 * → Repository đóng gói logic gọi API + xử lý lỗi
 * → Dễ thay đổi data source sau này (ví dụ: thêm Room cache) mà không sửa ViewModel
 * → Trả Result<T> để ViewModel xử lý success/failure một cách rõ ràng
 *
 * TẠI SAO dùng Result<T>?
 * → Kotlin built-in, bọc success value HOẶC exception
 * → Thay vì try-catch rải khắp ViewModel, tập trung xử lý lỗi tại đây
 * → ViewModel chỉ cần: result.getOrNull() hoặc result.exceptionOrNull()
 *
 * ⚠️ THAY ĐỔI KHI ÁP DỤNG KOIN (PHẦN B):
 * → Trước: `private val apiService = RetrofitClient.apiService` (tự lấy singleton cũ)
 * → Sau : Constructor nhận dependency từ bên ngoài (Koin truyền vào qua `get()`).
 *   Repository KHÔNG CÒN tự quyết định lấy dependency ở đâu — bên gọi quyết định.
 *   Đây chính là "Inversion of Control" (Đảo ngược điều khiển) của DI.
 *
 * ⚠️ PHẦN 2 (Room):
 * → Thêm `favoriteSongDao` → Repository vừa gọi API (network) vừa đọc/ghi Room (local).
 * → Yêu thích là dữ liệu local → lưu thẳng vào Room, không cần mạng.
 */
class MusicRepository(
    private val apiService: MusicApiService,
    private val favoriteSongDao: FavoriteSongDao
) {

    /**
     * Tìm kiếm bài hát theo keyword.
     * Trả Result<List<SongItem>> — success = danh sách bài, failure = exception
     */
    suspend fun searchSongs(query: String): Result<List<SongItem>> {
        return try {
            val response = apiService.searchSongs(query)
            if (response.success && response.data != null) {
                Result.success(response.data)
            } else {
                Result.failure(Exception(response.error ?: "Không tìm thấy kết quả"))
            }
        } catch (e: Exception) {
            // Bắt mọi lỗi mạng: timeout, no internet, server down...
            Result.failure(Exception("Lỗi mạng: ${e.localizedMessage}"))
        }
    }

    /**
     * Lấy link stream mp3 của bài hát.
     * Trả Result<String> — success = URL mp3, failure = exception
     */
    suspend fun getStreamUrl(songId: String): Result<String> {
        return try {
            val response = apiService.getStreamUrl(songId)
            if (response.success && response.data != null) {
                val url = response.data.getBestStreamUrl()
                if (url != null) {
                    Result.success(url)
                } else {
                    // Bài VIP không có link mp3 miễn phí
                    Result.failure(Exception("Bài hát VIP, không phát được miễn phí"))
                }
            } else {
                Result.failure(Exception(response.error ?: "Không lấy được link nhạc"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Lỗi mạng: ${e.localizedMessage}"))
        }
    }

    /**
     * Lấy bảng xếp hạng nhạc thịnh hành.
     */
    suspend fun getChart(): Result<List<ChartData>> {
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

    /**
     * Lấy chi tiết playlist/album.
     */
    suspend fun getPlaylist(playlistId: String): Result<PlaylistData> {
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

    /**
     * Lấy danh sách playlist nổi bật cho màn hình Home (mới - PHẦN A).
     */
    suspend fun getFeaturedPlaylists(): Result<List<PlaylistItem>> {
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

    // ============================================================
    // PHẦN 2 — ĐỌC/GHI ROOM: DANH SÁCH YÊU THÍCH
    // ============================================================

    /**
     * Quan sát danh sách bài yêu thích (mới thêm trước).
     * Room trả [Flow] → ViewModel collect 1 lần, UI tự cập nhật khi bảng đổi.
     */
    fun observeFavorites(): Flow<List<SongItem>> =
        favoriteSongDao.observeAll()
            .map { entities -> entities.map { it.toSongItem() } }

    /**
     * Kiểm tra 1 bài đã yêu thích chưa (để hiển thị icon trái tim đúng trạng thái).
     */
    suspend fun isFavorite(songId: String): Boolean =
        favoriteSongDao.getById(songId) != null

    /**
     * Bật/tắt yêu thích 1 bài:
     * - Chưa thích → thêm vào bảng favorite_songs.
     * - Đã thích → xóa khỏi bảng.
     */
    suspend fun toggleFavorite(song: SongItem) {
        if (favoriteSongDao.getById(song.encodeId) != null) {
            favoriteSongDao.deleteById(song.encodeId)
        } else {
            favoriteSongDao.insert(song.toFavoriteSongEntity(addedAt = System.currentTimeMillis()))
        }
    }
}
