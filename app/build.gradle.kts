plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.musicplayer"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.musicplayer"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    // Bật ViewBinding — truy cập view an toàn hơn findViewById, không cần cast type
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    // AndroidX core
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)

    // Retrofit + OkHttp — gọi REST API từ Android
    // TẠI SAO Retrofit? → Type-safe, tự convert JSON ↔ data class, hỗ trợ coroutines
    implementation(libs.retrofit.core)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp.logging)

    // Gson — parse JSON response từ server
    implementation(libs.gson)

    // Glide — load & cache ảnh thumbnail từ URL
    // TẠI SAO Glide? → Tự quản lý memory cache + disk cache, xử lý lifecycle tự động
    implementation(libs.glide)

    // Lifecycle (ViewModel + LiveData) — quản lý state theo MVVM
    // TẠI SAO ViewModel? → Survive configuration change (xoay màn hình không mất data)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.livedata)
    implementation(libs.lifecycle.runtime)

    // Coroutines — viết async code dạng tuần tự, dễ đọc hơn callback
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)

    // RecyclerView — khai báo tường minh (trước đây dựa transitive từ Material)
    implementation(libs.recyclerview)

    // Koin — Dependency Injection (PHẦN B)
    // TẠI SAO thêm Koin? → Quản lý Retrofit/Repository/ViewModel tập trung 1 nơi,
    //   ViewModel không phải tự new() dependency nữa → dễ test & thay thế.
    //   Từ Koin 3.2+, koin-android đã gộp sẵn ViewModel DSL (`viewModel { }`, `by viewModel()`)
    //   → chỉ cần 1 dependency này (koin-androidx-viewmodel tách riêng đã không còn tồn tại).
    implementation(libs.koin.android)

    // Test
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}