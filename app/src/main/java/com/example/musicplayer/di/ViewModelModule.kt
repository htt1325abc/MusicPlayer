package com.example.musicplayer.di

import com.example.musicplayer.viewmodel.HomeViewModel
import com.example.musicplayer.viewmodel.MusicViewModel
import com.example.musicplayer.viewmodel.PlaylistViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

/**
 * viewModelModule — Koin cung cấp ViewModel.
 *
 * TẠI SAO dùng `viewModel { }` thay vì `single { }`?
 * → ViewModel phải có vòng đời gắn với Activity/Fragment (tồn tại qua xoay màn hình,
 *   tự hủy khi screen bị destroy).
 * → Koin `viewModel { }` đăng ký ViewModel theo đúng cơ chế của
 *   `ViewModelProvider`/`ViewModelStore` của Android → không bị mất data khi xoay màn hình.
 * → Khi Activity gọi `by viewModel<HomeViewModel>()`, Koin:
 *     1. Tìm class HomeViewModel trong module
 *     2. `get()` các dependency trong constructor (MusicRepository, RecentPlayedStore)
 *     3. Tạo ViewModel và gắn vào ViewModelStore của Activity hiện tại
 *
 * ⚠️ KHÁC BIỆT với MusicViewModel (MainActivity):
 * → Cũ : `ViewModelProvider(this)[MusicViewModel::class.java]` — Android phải dùng
 *        factory mặc định KHÔNG có tham số → MusicViewModel tự new() Repository bên trong.
 * → Sau: MainActivity dùng `by viewModel<MusicViewModel>()` — Koin tự lo factory + bơm
 *        dependency (MusicRepository + PlaybackController) như các ViewModel khác.
 */
val viewModelModule = module {
    // HomeViewModel cần: MusicRepository (network + favorites), RecentPlayedStore (Room), PlaybackController
    viewModel { HomeViewModel(get(), get(), get()) }
    // PlaylistViewModel cần: MusicRepository, PlaybackController
    viewModel { PlaylistViewModel(get(), get()) }
    // MusicViewModel cần: MusicRepository, PlaybackController
    viewModel { MusicViewModel(get(), get()) }
}
