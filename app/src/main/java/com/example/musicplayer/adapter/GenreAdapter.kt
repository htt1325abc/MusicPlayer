package com.example.musicplayer.adapter

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.ScaleAnimation
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.musicplayer.databinding.ItemGenreBinding
import com.example.musicplayer.model.GenreItem

/**
 * GenreAdapter — RecyclerView cuộn ngang cho section "Thể loại nhạc".
 *
 * TẠI SAO dùng ListAdapter + DiffUtil?
 * → Danh sách thể loại là cố định, nhưng dùng ListAdapter để nhất quán
 *   với các adapter khác + hỗ trợ animation khi list thay đổi sau này.
 */
class GenreAdapter(
    private val onGenreClick: (GenreItem) -> Unit
) : ListAdapter<GenreItem, GenreAdapter.GenreViewHolder>(GenreDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GenreViewHolder {
        val binding = ItemGenreBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return GenreViewHolder(binding)
    }

    override fun onBindViewHolder(holder: GenreViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class GenreViewHolder(
        private val binding: ItemGenreBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener { view ->
                // Hiệu ứng scale nhẹ khi bấm → card phóng to rồi thu lại, tạo cảm giác "bấm vào"
                // TẠI SAO scale? → Phản hồi trực quan, app trông "sống động" hơn ripple thường
                val anim = ScaleAnimation(
                    1f, 0.94f, 1f, 0.94f,
                    ScaleAnimation.RELATIVE_TO_SELF, 0.5f,
                    ScaleAnimation.RELATIVE_TO_SELF, 0.5f
                ).apply {
                    duration = 120
                    fillAfter = false
                }
                view.startAnimation(anim)

                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onGenreClick(getItem(position))
                }
            }
        }

        fun bind(genre: GenreItem) {
            binding.tvGenreName.text = genre.name

            // Tạo gradient nền cho card từ 2 màu của thể loại (TL → BR)
            // TẠI SAO set programmatically thay vì tạo sẵn drawable?
            // → Mỗi thể loại 1 cặp màu riêng → không thể tạo 10 file drawable thủ công.
            // → GradientDrawable tạo động vừa gọn, vừa dễ thêm thể loại mới.
            val gradient = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(genre.colorStart, genre.colorEnd)
            )
            binding.ivGenreBg.background = gradient
        }
    }

    class GenreDiffCallback : DiffUtil.ItemCallback<GenreItem>() {
        override fun areItemsTheSame(oldItem: GenreItem, newItem: GenreItem): Boolean {
            return oldItem.name == newItem.name
        }

        override fun areContentsTheSame(oldItem: GenreItem, newItem: GenreItem): Boolean {
            return oldItem == newItem
        }
    }
}
