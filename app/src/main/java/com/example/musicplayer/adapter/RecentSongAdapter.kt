package com.example.musicplayer.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.bumptech.glide.request.RequestOptions
import com.example.musicplayer.R
import com.example.musicplayer.databinding.ItemRecentSongBinding
import com.example.musicplayer.model.SongItem

/**
 * RecentSongAdapter — list dọc cho section "Nghe gần đây".
 *
 * Khác với SongAdapter (dùng ở màn hình search):
 * → Thumbnail TRÒN (CircleCrop) — theo yêu cầu UI của section gần đây
 * → Có nút play nhỏ bên phải (bấm phát ngay không cần vào màn hình khác)
 */
class RecentSongAdapter(
    private val onSongClick: (SongItem, Int) -> Unit,
    private val onPlayClick: (SongItem, Int) -> Unit
) : ListAdapter<SongItem, RecentSongAdapter.RecentViewHolder>(RecentSongDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecentViewHolder {
        val binding = ItemRecentSongBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return RecentViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RecentViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class RecentViewHolder(
        private val binding: ItemRecentSongBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            // Bấm cả dòng → phát bài
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onSongClick(getItem(position), position)
                }
            }
            // Bấm nút play nhỏ → phát bài
            binding.btnRecentPlay.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onPlayClick(getItem(position), position)
                }
            }
        }

        fun bind(song: SongItem) {
            binding.tvRecentTitle.text = song.title
            binding.tvRecentArtist.text = song.artistsNames

            // CircleCrop → ảnh tròn đúng thiết kế
            Glide.with(binding.root.context)
                .load(song.thumbnailM ?: song.thumbnail)
                .apply(
                    RequestOptions()
                        .placeholder(R.drawable.ic_music_note)
                        .error(R.drawable.ic_music_note)
                        .transform(CircleCrop())
                )
                .into(binding.ivRecentThumb)
        }
    }

    class RecentSongDiffCallback : DiffUtil.ItemCallback<SongItem>() {
        override fun areItemsTheSame(oldItem: SongItem, newItem: SongItem): Boolean {
            return oldItem.encodeId == newItem.encodeId
        }

        override fun areContentsTheSame(oldItem: SongItem, newItem: SongItem): Boolean {
            return oldItem == newItem
        }
    }
}
