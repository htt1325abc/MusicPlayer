package com.example.musicplayer.network

import com.example.musicplayer.model.ChartData
import com.example.musicplayer.model.JamendoResponse
import com.example.musicplayer.model.PlaylistData
import com.example.musicplayer.model.PlaylistItem
import com.example.musicplayer.model.SongItem
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Interface định nghĩa các REST API endpoint của JAMENDO.
 *
 * TẠI SAO dùng interface?
 * → Retrofit tự sinh code implement interface này tại runtime (dùng Proxy pattern)
 * → Chỉ cần khai báo method signature, Retrofit lo phần HTTP request/response
 *
 * TẠI SAO dùng suspend fun?
 * → suspend fun cho phép gọi API bất đồng bộ mà code trông như đồng bộ
 * → Không block Main Thread → UI không bị đơ (ANR)
 * → Kết hợp với ViewModel.viewModelScope tự cancel khi ViewModel bị destroy
 *
 * ⚠️ client_id KHÔNG khai báo ở đây — một OkHttp Interceptor trong
 * NetworkModule tự thêm vào query param cho mọi request.
 */
interface MusicApiService {

    /**
     * Tìm kiếm bài hát theo từ khóa (tên bài, nghệ sĩ, tag...).
     * GET https://api.jamendo.com/v3.0/tracks/?search=<q>&limit=30&audioformat=mp32&imagesize=300
     *
     * @param query       Từ khóa tìm kiếm (search free-text của Jamendo)
     * @param limit       Số kết quả tối đa (max 200)
     * @param audioFormat mp31 (96kbps) / mp32 (VBR — chất lượng tốt)
     * @param imageSize   Kích thước ảnh cover (px)
     */
    @GET("tracks/")
    suspend fun searchSongs(
        @Query("search") query: String,
        @Query("limit") limit: Int = 30,
        @Query("audioformat") audioFormat: String = "mp32",
        @Query("imagesize") imageSize: Int = 300
    ): JamendoResponse<SongItem>

    /**
     * Lấy URL stream mp3 của 1 bài hát cụ thể.
     *
     * Jamendo trả URL stream ngay trong field `audio` của track (KHÔNG cần
     * endpoint riêng như local server). Vì vậy ta gọi lại tracks với filter
     * `id` rồi lấy results[0].audio ở tầng Repository.
     */
    @GET("tracks/")
    suspend fun getStreamUrl(
        @Query("id") songId: String,
        @Query("audioformat") audioFormat: String = "mp32"
    ): JamendoResponse<SongItem>

    /**
     * Bảng xếp hạng: các bài hát "featured" do đội ngũ Jamendo chọn,
     * sắp theo độ phổ biến (popularity_total).
     */
    @GET("tracks/")
    suspend fun getChart(
        @Query("featured") featured: String = "1",
        @Query("order") order: String = "popularity_total",
        @Query("limit") limit: Int = 30,
        @Query("audioformat") audioFormat: String = "mp32"
    ): JamendoResponse<ChartData>

    /**
     * Lấy chi tiết 1 playlist (danh sách bài hát bên trong).
     * GET https://api.jamendo.com/v3.0/albums/tracks/?id=<albumId>&audioformat=mp32&imagesize=300
     * → results[0].tracks là danh sách bài hát.
     */
    @GET("albums/tracks/")
    suspend fun getPlaylistDetail(
        @Query("id") playlistId: String,
        @Query("audioformat") audioFormat: String = "mp32",
        @Query("imagesize") imageSize: Int = 300
    ): JamendoResponse<PlaylistData>

    /**
     * Danh sách playlist cho màn hình Home.
     * GET https://api.jamendo.com/v3.0/albums/?order=popularity_total&limit=20&imagesize=300
     *
     * ⚠️ Dùng endpoint /albums (KHÔNG phải /playlists):
     * → Album có ảnh bìa (image) + tên thật, map chuẩn vào PlaylistItem.
     * → /playlists của Jamendo là playlist USER tự tạo: tên thường trống, không ảnh.
     */
    @GET("albums/")
    suspend fun getFeaturedPlaylists(
        @Query("order") order: String = "popularity_total",
        @Query("limit") limit: Int = 20,
        @Query("imagesize") imageSize: Int = 300
    ): JamendoResponse<PlaylistItem>
}
