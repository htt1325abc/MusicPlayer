package com.example.musicplayer.model

import android.graphics.Color

/**
 * Model cho 1 thể loại nhạc trên màn hình Home.
 *
 * TẠI SAO thể loại là DỮ LIỆU TĨNH (hardcode) thay vì gọi API?
 * → ZingMP3 không có API liệt kê "thể loại" dạng card đẹp cho app.
 * → Thể loại nhạc (Pop, Ballad, Rap...) là tập cố định, ít thay đổi.
 * → Hardcode trong app → Home load nhanh, không cần request, dễ demo.
 * → Khi bấm vào 1 thể loại, app sẽ SEARCH theo tên thể loại để lấy bài hát.
 *
 * @property name      Tên thể loại (cũng là keyword để search bài hát)
 * @property colorStart Màu gradient bắt đầu (đỉnh card)
 * @property colorEnd   Màu gradient kết thúc (đáy card)
 */
data class GenreItem(
    val name: String,
    val colorStart: Int,
    val colorEnd: Int
) {
    companion object {
        /**
         * Danh sách thể loại mặc định. Mỗi thể loại có 1 cặp màu gradient riêng
         * để card trông sinh động, dễ phân biệt.
         */
        val all: List<GenreItem> = listOf(
            GenreItem("Nhạc trẻ", Color.rgb(231, 76, 60), Color.rgb(192, 57, 43)),
            GenreItem("Pop", Color.rgb(52, 152, 219), Color.rgb(41, 128, 185)),
            GenreItem("Ballad", Color.rgb(155, 89, 182), Color.rgb(142, 68, 173)),
            GenreItem("Rap Việt", Color.rgb(230, 126, 34), Color.rgb(211, 84, 0)),
            GenreItem("EDM", Color.rgb(0, 172, 193), Color.rgb(0, 131, 176)),
            GenreItem("R&B", Color.rgb(46, 204, 113), Color.rgb(39, 174, 96)),
            GenreItem("Rock", Color.rgb(90, 92, 105), Color.rgb(52, 73, 94)),
            GenreItem("Acoustic", Color.rgb(241, 196, 15), Color.rgb(192, 139, 0)),
            GenreItem("Bolero", Color.rgb(243, 156, 18), Color.rgb(169, 86, 24)),
            GenreItem("K-Pop", Color.rgb(255, 82, 134), Color.rgb(233, 30, 99))
        )
    }
}
