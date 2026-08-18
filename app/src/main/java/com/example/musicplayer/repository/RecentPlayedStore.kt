package com.example.musicplayer.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.musicplayer.model.SongItem
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * RecentPlayedStore — nơi lưu danh sách "Nghe gần đây".
 *
 * TẠI SAO cần class này?
 * → Màn hình Home có section "Nghe gần đây" — cần biết user đã nghe bài nào.
 * → Dữ liệu phải SỐNG SÓT khi đóng app → lưu vào SharedPreferences (dạng JSON).
 * → ViewModel (HomeViewModel) đọc để hiển thị; Activity (khi phát bài) ghi vào.
 *
 * TẠI SAO là Koin singleton (`single {}`) thay vì static object?
 * → Cần `Context` để tạo SharedPreferences → Koin truyền `androidContext()`.
 * → `single` đảm bảo MỌI nơi trong app dùng chung 1 instance (cùng 1 file prefs)
 *   → HomeViewModel và Activity ghi/đọc trên cùng 1 dữ liệu, không lệch nhau.
 */
class RecentPlayedStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("recent_played", Context.MODE_PRIVATE)

    private val gson = Gson()
    private val key = "songs"
    private val maxItems = 20 // Giới hạn số bài lưu để tránh file prefs phình to

    /**
     * Lấy danh sách bài đã nghe gần đây (mới nhất trước).
     * Trả emptyList nếu chưa có hoặc JSON lỗi.
     */
    fun getRecent(): List<SongItem> {
        val json = prefs.getString(key, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<SongItem>>() {}.type
            gson.fromJson<List<SongItem>>(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList() // JSON hỏng → coi như chưa có lịch sử
        }
    }

    /**
     * Thêm 1 bài vào đầu danh sách gần đây.
     * Nếu bài đã tồn tại thì xóa bản cũ (tránh trùng lặp), rồi chèn lên đầu.
     */
    fun add(song: SongItem) {
        val current = getRecent().toMutableList()
        current.removeAll { it.encodeId == song.encodeId }
        current.add(0, song)
        if (current.size > maxItems) {
            // Giữ maxItems bài mới nhất
            while (current.size > maxItems) current.removeAt(current.size - 1)
        }
        prefs.edit().putString(key, gson.toJson(current)).apply()
    }
}
