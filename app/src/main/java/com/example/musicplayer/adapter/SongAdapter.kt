package com.example.musicplayer.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions
import com.example.musicplayer.R
import com.example.musicplayer.databinding.ItemSongBinding
import com.example.musicplayer.model.SongItem

/**
 * Adapter cho RecyclerView hiển thị danh sách bài hát.
 *
 * TẠI SAO dùng ListAdapter thay vì RecyclerView.Adapter thường?
 * → ListAdapter tích hợp sẵn DiffUtil → tự tính toán item nào thay đổi
 * → Chỉ cập nhật item thay đổi, không notifyDataSetChanged() toàn bộ
 * → Animation mượt hơn, hiệu suất tốt hơn (quan trọng khi search realtime)
 *
 * @param onItemClick callback khi user bấm vào 1 bài hát
 *        (trả cả vị trí trong danh sách để phát theo queue auto-advance)
 * @param onFavoriteClick callback khi user bấm nút trái tim (PHẦN 2 - Room)
 */
class SongAdapter(
    private val onItemClick: (SongItem, Int) -> Unit,
    private val onFavoriteClick: (SongItem, Int) -> Unit
) : ListAdapter<SongItem, SongAdapter.SongViewHolder>(SongDiffCallback()) {

    // Tập id các bài đang yêu thích — Activity cập nhật khi StateFlow đổi (PHẦN 2)
    private val favoriteIds = mutableSetOf<String>()

    /**
     * Cập nhật tập bài yêu thích từ ViewModel (Room Flow).
     * Chỉ re-bind các dòng có trạng thái yêu thích THAY ĐỔI → RecyclerView không nhấp nháy.
     */
    fun updateFavorites(ids: Set<String>) {
        if (favoriteIds == ids) return
        val oldIds = favoriteIds.toSet()
        favoriteIds.clear()
        favoriteIds.addAll(ids)

        val changedIds = oldIds union ids
        for (i in 0 until itemCount) {
            if (getItem(i).encodeId in changedIds) {
                notifyItemChanged(i)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongViewHolder {
        val binding = ItemSongBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return SongViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SongViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    /**
     * ViewHolder — giữ reference đến các view trong item layout.
     * Dùng ViewBinding thay vì findViewById → type-safe, null-safe
     */
    inner class SongViewHolder(
        private val binding: ItemSongBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            // Set click listener 1 lần trong init, không phải mỗi lần bind
            // → Tối ưu hơn đặt trong onBindViewHolder
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClick(getItem(position), position)
                }
            }
            // Nút trái tim yêu thích (PHẦN 2) — bấm → toggleFavorite qua callback
            binding.btnFavorite.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onFavoriteClick(getItem(position), position)
                }
            }
        }

        fun bind(song: SongItem) {
            binding.tvSongTitle.text = song.title
            binding.tvArtistName.text = song.artistsNames
            binding.tvDuration.text = song.formatDuration()

            // Icon trái tim: đầy (đã thích) / rỗng (chưa thích) theo tập favoriteIds
            val isFavorite = song.encodeId in favoriteIds
            binding.btnFavorite.setImageResource(
                if (isFavorite) R.drawable.ic_favorite else R.drawable.ic_favorite_border
            )

            // Glide load ảnh thumbnail — tự cache, xử lý placeholder khi loading
            Glide.with(binding.root.context)
                .load(song.thumbnailM ?: song.thumbnail)
                .apply(
                    RequestOptions()
                        .placeholder(R.drawable.ic_music_note) // Ảnh tạm khi đang load
                        .error(R.drawable.ic_music_note)       // Ảnh khi load lỗi
                        .transform(RoundedCorners(16))         // Bo góc 16dp
                )
                .into(binding.ivThumbnail)
        }
    }

    /**
     * DiffUtil callback — so sánh 2 item để biết item nào thay đổi.
     * TẠI SAO cần DiffUtil?
     * → Khi user search lại, list mới có thể trùng 1 phần với list cũ
     * → DiffUtil chỉ cập nhật item khác biệt → RecyclerView không nhấp nháy
     */
    class SongDiffCallback : DiffUtil.ItemCallback<SongItem>() {
        // areItemsTheSame: cùng ID = cùng 1 bài hát (có thể data thay đổi)
        override fun areItemsTheSame(oldItem: SongItem, newItem: SongItem): Boolean {
            return oldItem.encodeId == newItem.encodeId
        }

        // areContentsTheSame: toàn bộ data giống nhau → không cần re-bind
        override fun areContentsTheSame(oldItem: SongItem, newItem: SongItem): Boolean {
            return oldItem == newItem
        }
    }
}
