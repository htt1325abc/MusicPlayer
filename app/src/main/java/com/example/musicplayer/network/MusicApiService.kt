package com.example.musicplayer.network

import com.example.musicplayer.model.ApiResponse
import com.example.musicplayer.model.ChartData
import com.example.musicplayer.model.PlaylistData
import com.example.musicplayer.model.SongItem
import com.example.musicplayer.model.StreamData
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Interface định nghĩa các REST API endpoint.
 *
 * TẠI SAO dùng interface?
 * → Retrofit tự sinh code implement interface này tại runtime (dùng Proxy pattern)
 * → Chỉ cần khai báo method signature, Retrofit lo phần HTTP request/response
 *
 * TẠI SAO dùng suspend fun?
 * → suspend fun cho phép gọi API bất đồng bộ mà code trông như đồng bộ
 * → Không block Main Thread → UI không bị đơ (ANR)
 * → Kết hợp với ViewModel.viewModelScope tự cancel khi ViewModel bị destroy
 */
interface MusicApiService {

    /**
     * Tìm kiếm bài hát theo keyword
     * Server trả về danh sách SongItem (metadata, không có link mp3)
     */
    @GET("api/search")
    suspend fun searchSongs(@Query("q") query: String): ApiResponse<List<SongItem>>

    /**
     * Lấy link stream mp3 của 1 bài hát cụ thể
     *
     * TẠI SAO tách riêng khỏi search?
     * → Link mp3 có thời hạn (expire), lấy sớm sẽ hết hạn trước khi user nghe
     * → Chỉ gọi khi user bấm vào bài → tiết kiệm bandwidth
     */
    @GET("api/song/{id}/stream")
    suspend fun getStreamUrl(@Path("id") songId: String): ApiResponse<StreamData>

    /**
     * Lấy bảng xếp hạng nhạc thịnh hành
     */
    @GET("api/chart")
    suspend fun getChart(): ApiResponse<List<ChartData>>

    /**
     * Lấy chi tiết playlist/album
     */
    @GET("api/playlist/{id}")
    suspend fun getPlaylistDetail(@Path("id") playlistId: String): ApiResponse<PlaylistData>
}
