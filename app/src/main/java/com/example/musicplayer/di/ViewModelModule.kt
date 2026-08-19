package com.example.musicplayer.di

import com.example.musicplayer.presenter.home.HomeViewModel
import com.example.musicplayer.presenter.search.MusicViewModel
import com.example.musicplayer.presenter.songlist.PlaylistViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

/**
 * viewModelModule — Koin cung cấp ViewModel.
 *
 * ⚠️ LƯU Ý về `viewModelOf` (project MẪU dùng `viewModelOf(::HomeViewModel)`):
 * → Mẫu chạy Koin 4.0.0 — có DSL `viewModelOf`.
 * → Project này pin Koin 3.5.6 — chưa có `viewModelOf`, dùng DSL tương đương
 *   `viewModel { HomeViewModel(get(), get(), get(), get()) }` (Koin tự đọc
 *   constructor và `get()` từng dependency). Cùng ý nghĩa, khác cú pháp.
 *
 * TẠI SAO dùng Koin cho ViewModel?
 * → ViewModel phải có vòng đời gắn với Activity/Fragment (tồn tại qua xoay màn hình,
 *   tự hủy khi screen bị destroy) → Koin đăng ký đúng cơ chế ViewModelStore của Android.
 * → `Application` (cho IViewModel extends AndroidViewModel) được Koin cung cấp tự động
 *   từ `androidContext(this)` trong App.kt → `get()` đầu tiên resolve ra Application.
 */
val viewModelModule = module {
    // HomeViewModel cần: Application + MusicRepository + RecentPlayedStore + PlaybackController
    viewModel { HomeViewModel(get(), get(), get(), get()) }
    // PlaylistViewModel cần: Application + MusicRepository + PlaybackController
    viewModel { PlaylistViewModel(get(), get(), get()) }
    // MusicViewModel cần: Application + MusicRepository + PlaybackController
    viewModel { MusicViewModel(get(), get(), get()) }
}
