package com.example.musicplayer

import android.app.Application
import com.example.musicplayer.di.networkModule
import com.example.musicplayer.di.repositoryModule
import com.example.musicplayer.di.viewModelModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

/**
 * App — Application class, nơi "khởi động" Koin.
 *
 * TẠI SAO khởi tạo Koin ngay tại Application.onCreate()?
 * → Application.onCreate() chạy TRƯỚC mọi Activity/Service.
 * → Cần Koin sẵn sàng trước khi bất kỳ màn hình nào `by viewModel()` / `by inject()`.
 * → Nếu không, app sẽ crash vì "KoinApplication has not been started".
 *
 * TẠI SAO để `android:name=".App"` trong AndroidManifest?
 * → Android cần biết class Application tùy chỉnh của app → khai báo trong manifest.
 * → Đây là bước BẮT BUỘC để Koin chạy.
 */
class App : Application() {

    override fun onCreate() {
        super.onCreate()

        startKoin {
            // Ghi log hoạt động của Koin (định nghĩa module, lỗi inject) ra Logcat
            androidLogger(Level.INFO)
            // Cung cấp Application context cho các dependency cần context
            androidContext(this@App)
            // Đăng ký các module: Network → Repository → ViewModel
            modules(
                networkModule,
                repositoryModule,
                viewModelModule
            )
        }
    }
}
