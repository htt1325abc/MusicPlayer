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
import com.example.musicplayer.databinding.ItemPlaylistBinding
import com.example.musicplayer.model.PlaylistItem

/**
 * PlaylistAdapter — RecyclerView cuộn ngang cho section "Playlist nổi bật".
 */
class PlaylistAdapter(
    private val onPlaylistClick: (PlaylistItem) -> Unit
) : ListAdapter<PlaylistItem, PlaylistAdapter.PlaylistViewHolder>(PlaylistDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlaylistViewHolder {
        val binding = ItemPlaylistBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return PlaylistViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PlaylistViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class PlaylistViewHolder(
        private val binding: ItemPlaylistBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onPlaylistClick(getItem(position))
                }
            }
        }

        fun bind(playlist: PlaylistItem) {
            binding.tvPlaylistTitle.text = playlist.title
            binding.tvPlaylistCount.text = playlist.formatSongCount()

            // Glide load ảnh bìa playlist — bo góc 16dp cho khớp card
            Glide.with(binding.root.context)
                .load(playlist.thumbnail)
                .apply(
                    RequestOptions()
                        .placeholder(R.drawable.ic_music_note)
                        .error(R.drawable.ic_music_note)
                        .transform(RoundedCorners(16))
                )
                .into(binding.ivPlaylistThumb)
        }
    }

    class PlaylistDiffCallback : DiffUtil.ItemCallback<PlaylistItem>() {
        override fun areItemsTheSame(oldItem: PlaylistItem, newItem: PlaylistItem): Boolean {
            return oldItem.encodeId == newItem.encodeId
        }

        override fun areContentsTheSame(oldItem: PlaylistItem, newItem: PlaylistItem): Boolean {
            return oldItem == newItem
        }
    }
}
