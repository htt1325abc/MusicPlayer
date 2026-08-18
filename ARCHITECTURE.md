# ARCHITECTURE.md — Kiến trúc đồ án MusicPlayer (Kotlin Android)

> File này giải thích **code thật trong project** (không phải lý thuyết chung chung) để bạn ôn trước khi bảo vệ đồ án.
> Đọc kèm các file được trích dẫn để nắm chắc.

---

## 0. Tóm tắt 1 câu

**MusicPlayer** là app Android phát nhạc trực tuyến theo kiến trúc **MVVM + Repository Pattern**, dùng **Retrofit/OkHttp** gọi một **server Node.js proxy** (gói `zingmp3-api-full`) để lấy bài hát & link mp3, phát nhạc bằng **MediaPlayer** bên trong một **Foreground + Bound Service**, và dùng **Koin** để quản lý dependency.

---

## 1. Tổng quan kiến trúc

### 1.1 Công nghệ sử dụng (đã khai báo trong `gradle/libs.versions.toml` + `app/build.gradle.kts`)

| Thành phần | Thư viện / version | Dùng để làm gì |
|---|---|---|
| UI | Material 3 (`com.google.android.material` 1.14.0) | Theme dark, Toolbar, TextInputLayout |
| Danh sách | RecyclerView 1.4.0 | List + DiffUtil |
| Network | Retrofit 2.11.0 + OkHttp 4.12.0 + Gson 2.11.0 | Gọi REST API, log request/response |
| Ảnh | Glide 4.16.0 | Load & cache thumbnail từ URL |
| Bất đồng bộ | Coroutines 1.10.2 | `suspend`, `viewModelScope`, `StateFlow` |
| Lifecycle | `lifecycle-viewmodel-ktx` 2.9.1 | ViewModel, `repeatOnLifecycle` |
| DI | Koin 3.5.6 (`io.insert-koin:koin-android`) | Quản lý dependency tập trung |
| Audio | `MediaPlayer` (Android SDK) | Phát nhạc — không cần thư viện ngoài |
| Server | Node.js + Express + `zingmp3-api-full` v1.0.14 | Proxy trung gian tới ZingMP3 |

### 1.2 Các package / layer trong project

Package gốc: `com.example.musicplayer` (`app/src/main/java/com/example/musicplayer/`)

| Package | Layer | Vai trò |
|---|---|---|
| `App.kt` | — | `Application` — khởi động Koin ngay khi app chạy |
| `network/` | Data layer | `MusicApiService` (khai báo endpoint), `RetrofitClient` (tạo Retrofit) |
| `repository/` | Data layer | `MusicRepository` (gọi API, bọc `Result<T>`), `RecentPlayedStore` (lưu "nghe gần đây") |
| `model/` | Model | Các `data class` map với JSON server trả về (`SongItem`, `PlaylistItem`, `StreamData`...) |
| `di/` | — | 3 Koin module: `networkModule`, `repositoryModule`, `viewModelModule` |
| `viewmodel/` | ViewModel layer | `MusicViewModel` (cũ), `HomeViewModel`, `PlaylistViewModel` (mới, dùng Koin) |
| `ui/` | View layer | `HomeActivity`, `SongListActivity`, `BasePlayerActivity` |
| `adapter/` | View layer | `SongAdapter`, `RecentSongAdapter`, `GenreAdapter`, `PlaylistAdapter` |
| `service/` | — | `MusicService` — Foreground + Bound service phát nhạc + auto-advance |
| `MainActivity.kt` | View layer | Màn hình tìm kiếm **cũ** (DI thủ công, giữ lại để so sánh) |
| `res/` | — | Layout XML, drawable, menu, theme |
| `server/` (ngoài app) | — | Server Node.js proxy ZingMP3, chạy port 3000 |

> ⚠️ Project **chưa có package `domain/` riêng** (không có UseCase/Interactor). Đây là MVVM rút gọn: `View → ViewModel → Repository → Network`. Khi được hỏi, bạn nói rõ "đồ án dùng MVVM đơn giản, không tách thêm tầng domain cho gọn".

### 1.3 Sơ đồ phụ thuộc (ai gọi ai)

```
┌──────────────────────────  VIEW LAYER (Activity)  ──────────────────────────┐
│                                                                             │
│  HomeActivity (launcher) ──mở──► SongListActivity ──mở──► MainActivity (cũ) │
│        │  extends                │  extends              │                  │
│        ▼                         ▼                       ▼                  │
│  BasePlayerActivity (bind MusicService + mini player + playQueue)           │
└───────────┬──────────────────────┬──────────────────────────────────────────┘
            │ 1. bấm bài → playQueue()     │ 2. observe StateFlow
            ▼                              ▼
┌──────────────────  VIEWMODEL LAYER  ────────────────────────────────────────┐
│  HomeViewModel (Koin: by viewModel)     PlaylistViewModel (Koin)            │
│  MusicViewModel (CŨ: ViewModelProvider, tự new repository)                  │
└───────────┬─────────────────────────────────────────────────────────────────┘
            │ 3. gọi suspend fun
            ▼
┌──────────────────  DATA LAYER (Repository)  ────────────────────────────────┐
│  MusicRepository ──► MusicApiService (Retrofit) ──► OkHttp ──► server:3000  │
│  RecentPlayedStore (SharedPreferences — lưu lịch sử nghe)                   │
└───────────┬─────────────────────────────────────────────────────────────────┘
            │ 4. link mp3
            ▼
┌──────────────────  SERVICE LAYER  ──────────────────────────────────────────┐
│  MusicService (Foreground + Bound)                                          │
│    ├─ MediaPlayer (phát nhạc)                                               │
│    ├─ by inject() MusicRepository (tự lấy URL bài kế tiếp khi auto-advance) │
│    └─ Notification (nút Play/Pause/Stop)                                    │
└─────────────────────────────────────────────────────────────────────────────┘

Quy tắc phụ thuộc (theo chiều mũi tên đi XUỐNG):
  Activity → ViewModel → Repository → ApiService → server
  Activity → Service (bind qua Binder)
  Service → Repository (Koin inject — để tự auto-advance khi chạy nền)
  ViewModel/Activity ← Repository ← ApiService  (dữ liệu trả về ngược lên)
```

**Nguyên tắc quan trọng nhất:** View không gọi thẳng API, ViewModel không tự `new` repository lung tung (bản cũ MainActivity làm vậy — đó là "kiểu cũ" để so sánh), và **Service là nơi DUY NHẤT giữ MediaPlayer**.

---

## 2. Đi qua từng thành phần quan trọng

Mỗi mục gồm: **nhiệm vụ → caller (ai gọi) → callee (gọi tới ai) → xóa thì hỏng gì**.

### 2.1 `App.kt` — khởi động Koin

- **Nhiệm vụ:** `class App : Application()`, trong `onCreate()` gọi `startKoin { androidLogger(Level.INFO); androidContext(this@App); modules(networkModule, repositoryModule, viewModelModule) }`.
- **Caller:** Hệ điều hành Android gọi `onCreate()` trước mọi Activity/Service. App được khai báo trong manifest: `android:name=".App"`.
- **Callee:** `startKoin(...)` đăng ký 3 module DI.
- **Xóa thì hỏng gì:** Mọi thứ dùng Koin sẽ crash ngay lập tức với lỗi *"KoinApplication has not been started"* — `HomeViewModel`, `PlaylistViewModel`, `RecentPlayedStore`, `MusicRepository` trong `MusicService` đều không inject được.
- **Trả lời khi bị hỏi:** *"Koin phải được start ở Application.onCreate() vì nó chạy trước tất cả Activity/Service, nên khi bất kỳ màn hình nào cần inject thì Koin đã sẵn sàng."*

### 2.2 `network/` — Retrofit + OkHttp

**`MusicApiService.kt`** (interface):
- **Nhiệm vụ:** Khai báo 5 endpoint bằng annotation Retrofit:
  - `@GET("api/search") searchSongs(@Query("q") query): ApiResponse<List<SongItem>>`
  - `@GET("api/song/{id}/stream") getStreamUrl(@Path("id") songId): ApiResponse<StreamData>`
  - `@GET("api/chart") getChart(): ApiResponse<List<ChartData>>`
  - `@GET("api/playlist/{id}") getPlaylistDetail(@Path("id") playlistId): ApiResponse<PlaylistData>`
  - `@GET("api/playlists") getFeaturedPlaylists(): ApiResponse<List<PlaylistItem>>`
- Tất cả là `suspend fun` → chạy bất đồng bộ không block main thread.
- **Caller:** `MusicRepository`.
- **Callee:** server Node.js ở `http://127.0.0.1:3000/`.
- **Xóa thì hỏng:** toàn bộ app không lấy được dữ liệu (Repository compile lỗi).

**`RetrofitClient.kt`** (object singleton):
- **Nhiệm vụ:** Tạo 1 instance `Retrofit` + `OkHttpClient` (có `HttpLoggingInterceptor` level BODY, timeout 30s), expose `apiService`.
- **Caller:** `MusicRepository` dùng làm **default parameter**: `class MusicRepository(private val apiService: MusicApiService = RetrofitClient.apiService)`. Tức là chỉ còn màn hình cũ (`MusicViewModel`) dùng default này; màn hình mới dùng Koin.
- **Xóa thì hỏng:** `MusicRepository` không có default → `MusicViewModel` (cũ) không compile được (nếu không sửa).
- ⚠️ **Điểm yếu cần biết:** Có **2 chỗ tạo Retrofit**: `RetrofitClient.kt` và `di/NetworkModule.kt` — kèm theo đó **BASE_URL bị lặp 2 lần**. Nếu đổi URL phải đổi cả 2 chỗ. Đây là code "đang chuyển tiếp" từ style cũ sang Koin.

### 2.3 `repository/` — Data layer

**`MusicRepository.kt`**:
- **Nhiệm vụ:** Trung gian giữa ViewModel/Service và Network. Mỗi hàm gọi API, kiểm tra `response.success && response.data != null`, rồi bọc vào `Result<T>` (success chứa dữ liệu, failure chứa `Exception`).
- Các hàm: `searchSongs(query)`, `getStreamUrl(songId)`, `getChart()`, `getPlaylist(playlistId)`, `getFeaturedPlaylists()`.
- Trong `getStreamUrl`: gọi `response.data.getBestStreamUrl()` (hàm này trong `StreamData` — ưu tiên 320, fallback 128, **lọc chuỗi "VIP"**) → nếu `null` thì trả `Result.failure(Exception("Bài hát VIP..."))`.
- **Caller:**
  - `MusicViewModel` (tự `MusicRepository()` — kiểu cũ)
  - `HomeViewModel`, `PlaylistViewModel` (Koin `get()` bơm vào constructor)
  - `MusicService` (`by inject()` — để tự lấy URL bài kế tiếp khi auto-advance)
- **Callee:** `MusicApiService`.
- **Xóa thì hỏng:** không gọi được bất kỳ API nào — mọi màn hình đều hiện lỗi/trống.

**`RecentPlayedStore.kt`**:
- **Nhiệm vụ:** Lưu/đọc danh sách "Nghe gần đây" bằng **SharedPreferences** (file `recent_played`), lưu dạng JSON qua Gson, giới hạn 20 bài, bài mới chèn lên đầu, xóa trùng `encodeId`.
- **Caller:** `HomeViewModel.loadRecent()` (đọc), `BasePlayerActivity.playQueue()` (ghi khi phát bài).
- **Callee:** SharedPreferences + Gson.
- **Xóa thì hỏng:** section "Nghe gần đây" trên Home trống và không lưu được lịch sử. (App vẫn chạy được — đây là tính năng phụ, không phải lõi.)

### 2.4 `di/` — 3 Koin module + thứ tự khởi tạo THẬT

**`NetworkModule.kt`** (`networkModule`):
```kotlin
single { OkHttpClient.Builder()...build() }          // (1) tự tạo
single { Retrofit.Builder().baseUrl("...").client(get())...build() }  // (2) get() lấy (1)
single { get<Retrofit>().create(MusicApiService::class.java) }        // (3) get() lấy (2)
```

**`RepositoryModule.kt`** (`repositoryModule`):
```kotlin
single { RecentPlayedStore(androidContext()) }   // dùng Application context (an toàn, không leak)
single { MusicRepository(get()) }                // get() = MusicApiService từ networkModule
```

**`ViewModelModule.kt`** (`viewModelModule`):
```kotlin
viewModel { HomeViewModel(get(), get()) }        // get() = MusicRepository + RecentPlayedStore
viewModel { PlaylistViewModel(get()) }           // get() = MusicRepository
```

**Thứ tự khởi tạo THỰC TẾ (quan trọng để trả lời bảo vệ):**
- Koin **không** "chạy lần lượt từng module" tại `startKoin()`. `startKoin` chỉ **đăng ký công thức** (definition) vào container.
- Dependency được tạo **lười (lazy)** — lần ĐẦU TIÊN được yêu cầu. Ví dụ khi `HomeActivity` gọi `by viewModel<HomeViewModel>()`:
  1. Koin tìm `HomeViewModel` trong `viewModelModule`.
  2. Nó cần 2 tham số → Koin tìm `MusicRepository` và `RecentPlayedStore` trong `repositoryModule`.
  3. `MusicRepository` cần `MusicApiService` → Koin tìm trong `networkModule`.
  4. `MusicApiService` cần `Retrofit` → tạo Retrofit; Retrofit cần `OkHttpClient` → tạo OkHttpClient.
  5. Xong hết các dependency lá → Koin "đi ngược lên" tạo từng cái, cuối cùng tạo `HomeViewModel` và gắn vào `ViewModelStore` của Activity.
- Kết quả: **mỗi `single {}` chỉ tồn tại 1 instance** dùng chung toàn app; **mỗi `viewModel {}` gắn với vòng đời của Activity** (không bị mất khi xoay màn hình).

> 🧠 **Cách giải thích gọn khi bị hỏi "Koin hoạt động thế nào?":**
> "Koin là dependency container. Tôi khai báo 'cách tạo' từng object trong module. Khi Activity cần ViewModel, Koin tự nhìn vào constructor của nó, tự tạo hết các dependency phía sau (Repository → ApiService → Retrofit → OkHttpClient), rồi bơm vào. Tôi không phải tự `new` gì cả — đó gọi là Inversion of Control."

### 2.5 `viewmodel/` — 3 ViewModel

**`MusicViewModel.kt`** (CŨ — màn hình MainActivity):
- Tự `private val repository = MusicRepository()` → **kiểu cũ, không Koin** (giữ để so sánh).
- State: `songs`, `isLoading`, `errorMessage`, `currentSong`, `streamUrl` (đều là `MutableStateFlow` + expose `asStateFlow()`).
- `search(query)`: **debounce 500ms** bằng `searchJob?.cancel()` + `delay(500)` → gọi `repository.searchSongs`.
- `playSong(song)`: lưu `currentQueue = _songs.value` (danh sách đang hiển thị — để Service tự phát bài tiếp), fetch `getStreamUrl`, đẩy vào `_streamUrl`.
- `getCurrentQueue()`: trả danh sách đang hiển thị cho MainActivity đưa vào Service.
- `onStreamUrlConsumed()`: reset `_streamUrl = null` (tránh phát lại bài cũ khi observe lại).

**`HomeViewModel.kt`** (MỚI — Koin):
- Constructor `(repository: MusicRepository, recentStore: RecentPlayedStore)` → Koin bơm.
- `genres` = `GenreItem.all` (tĩnh, không cần API).
- `loadHome()`: `repository.getFeaturedPlaylists()` → `_featuredPlaylists`.
- `loadRecent()`: `recentStore.getRecent()` → `_recentSongs`.
- `init {}` gọi luôn 2 hàm trên khi ViewModel được tạo.

**`PlaylistViewModel.kt`** (MỚI — Koin):
- Constructor `(repository: MusicRepository)` → Koin bơm.
- `loadPlaylist(id)`: `repository.getPlaylist(id)` → `_title` + `_songs` (dùng cho mode PLAYLIST).
- `searchSongs(keyword)`: `repository.searchSongs(keyword)` → `_songs` (dùng cho mode SEARCH theo thể loại).
- `setTitle(title)`: set tiêu đề toolbar.

**Nếu xóa từng cái:**
- `MusicViewModel` → MainActivity hỏng (không search, không phát được từ màn hình cũ).
- `HomeViewModel` → HomeActivity hỏng (không load playlist/recent).
- `PlaylistViewModel` → SongListActivity hỏng (không load danh sách bài).

### 2.6 `service/MusicService.kt` — trái tim của app

**Vòng đời:**
- Là **Bound Service** (`onBind` trả về `MusicBinder`) + **Foreground Service** (khi phát, gọi `startForeground(NOTIFICATION_ID, notification)` với `foregroundServiceType="mediaPlayback"`).
- `onCreate()`: tạo NotificationChannel (Android 8+).
- `onStartCommand()`: chỉ xử lý action từ notification (PLAY_PAUSE / STOP), trả `START_NOT_STICKY` (không tự restart khi bị kill).
- `onDestroy()`: `playbackScope.cancel()` + `mediaPlayer?.release()`.

**Tại sao Bound + Foreground?**
- **Bound (Binder):** Activity cần gọi trực tiếp method của service (`playQueue`, `togglePlayPause`, `stopPlayback`, `isPlaying`).
- **Foreground:** Android 8+ giết background service sau vài phút; foreground service hiện notification → không bị giết → nhạc chạy khi app ở background/khóa màn hình.

**Các thành phần chính:**
- `queue: List<SongItem>` + `currentIndex` → **hàng đợi phát** (tính năng auto-advance).
- `currentSong` (private set) + `onSongChanged` callback → báo Activity cập nhật mini player khi **tự chuyển bài**.
- `isPrepared` flag → chặn lỗi "start called in state 4" (gọi `start()` khi đang buffer).
- `playbackGeneration` (tăng mỗi lần `playInternal`) → bỏ qua callback của player cũ đã release.
- `repository: MusicRepository by inject()` → Koin bơm, vì Service là `KoinComponent`.

**Các hàm chính (ai gọi gì):**
- `playQueue(songs, startIndex, preFetchedUrl?)` — **được Activity gọi** khi user bấm bài. Đặt queue + index, fire `onSongChanged`, rồi phát thẳng (`preFetchedUrl` có sẵn) hoặc `playCurrent()`.
- `playCurrent()` — tự lấy URL qua `repository.getStreamUrl(song.encodeId)` trên `playbackScope`, thành công → `playInternal`, thất bại → **tự nhảy sang bài kế tiếp** (bỏ qua bài VIP/lỗi).
- `playInternal(url, title, artist)` — tạo `MediaPlayer`, `setDataSource`, `prepareAsync`, `setOnPreparedListener` → `start()` + `startForeground`, `setOnCompletionListener` → **auto-advance** (nếu còn bài) hoặc dừng, `setOnErrorListener`.
- `togglePlayPause()` / `isPlaying()` / `stopPlayback()` — Activity gọi.
- `playFromUrl(url, title, artist)` — phát 1 bài đơn lẻ (xóa queue); **hiện tại không còn nơi nào gọi** (MainActivity mới đã chuyển sang `playQueue`) — đây là hàm thừa còn để lại.
- `buildNotification(isPlaying)` — notification với nút Play/Pause + Stop (PendingIntent gửi `ACTION_*` về `onStartCommand`), bấm notification mở `MainActivity`.

**Auto-advance hoạt động ở đâu?** Trong `setOnCompletionListener`:
```kotlin
setOnCompletionListener {
    if (generation != playbackGeneration) return@setOnCompletionListener
    onPlaybackStateChanged?.invoke(false)
    if (currentIndex + 1 < queue.size) {
        currentIndex++
        playCurrent()      // ← tự phát bài kế tiếp, kể cả khi app ở background
    } else {
        updateNotification(false)
    }
}
```

**Xóa MusicService thì hỏng gì:** không phát được bất kỳ bài nào — cả 3 Activity đều `bindPlayerService()`/`bindService()`; mini player, notification, auto-advance đều chết.

### 2.7 `ui/` — các màn hình & luồng chuyển

**`HomeActivity.kt`** (LAUNCHER — màn hình chính mới):
- extends `BasePlayerActivity`; `homeViewModel: HomeViewModel by viewModel()` (Koin).
- 3 section: **Thể loại** (`GenreAdapter` — cuộn ngang), **Playlist nổi bật** (`PlaylistAdapter` — cuộn ngang), **Nghe gần đây** (`RecentSongAdapter` — list dọc).
- Bấm thể loại → `openSongList(title=genre.name, mode=MODE_SEARCH, id=genre.name)`.
- Bấm playlist → `openSongList(title=playlist.title, mode=MODE_PLAYLIST, id=playlist.encodeId)`.
- Bấm bài gần đây → `playQueue(homeViewModel.recentSongs.value, position)`.
- Menu `R.id.action_search` → mở `MainActivity` (màn hình tìm kiếm cũ).
- `onResume()` gọi `homeViewModel.loadRecent()` để cập nhật "Nghe gần đây" khi quay lại.

**`SongListActivity.kt`** (danh sách bài):
- extends `BasePlayerActivity`; `playlistViewModel: PlaylistViewModel by viewModel()` (Koin).
- Đọc `EXTRA_MODE/EXTRA_TITLE/EXTRA_ID` từ Intent.
- `MODE_PLAYLIST` → `loadPlaylist(id)`; còn lại (SEARCH) → `setTitle(title)` + `searchSongs(id)`.
- Bấm bài → `playQueue(playlistViewModel.songs.value, position)`.

**`MainActivity.kt`** (màn hình tìm kiếm CŨ, DI thủ công):
- `viewModel = ViewModelProvider(this)[MusicViewModel::class.java]` (không Koin).
- Search bằng `TextWatcher` trên `etSearch` → `viewModel.search(...)`, có nút search trên bàn phím.
- Observe 5 StateFlow; khi `streamUrl` có giá trị → tính `queue = viewModel.getCurrentQueue()` + `startIndex` → `musicService?.playQueue(queue, startIndex, preFetchedUrl = url)`; nếu service chưa bind xong → lưu `pendingUrl/pendingSong`, phát trong `onServiceConnected`.
- Yêu cầu `POST_NOTIFICATIONS` (Android 13+).

**`BasePlayerActivity.kt`** (lớp nền dùng chung cho màn hình mới):
- `recentStore: RecentPlayedStore by inject()` (Koin).
- `bindPlayerService()/unbindPlayerService()` — bind/unbind MusicService.
- `serviceConnection` — gán `onPlaybackStateChanged`, `onError`, `onSongChanged` (cập nhật mini player khi auto-advance), flush `pendingPlay` khi service sẵn sàng.
- `playQueue(songs, index)` — show mini player + `recentStore.add(song)` + gọi `musicService?.playQueue(...)` (hoặc `pendingPlay` nếu chưa bind).
- `setupMiniPlayer()/showMiniPlayer(song)/updatePlayPauseIcon(isPlaying)`.

**Luồng chuyển màn hình:**
```
HomeActivity ──(bấm thể loại/playlist)──► SongListActivity
HomeActivity ──(bấm nút search trên toolbar)──► MainActivity (tìm kiếm)
```
- Home và SongList extends BasePlayerActivity → dùng chung service + mini player.
- MainActivity độc lập (cũ).

### 2.8 `adapter/` — 4 RecyclerView Adapter

| Adapter | List | Khác biệt so với SongAdapter |
|---|---|---|
| `SongAdapter` | Bài hát (search/playlist) | `onItemClick(song, position)` |
| `RecentSongAdapter` | Nghe gần đây | Thumbnail **tròn** (`CircleCrop`), có nút play nhỏ riêng |
| `GenreAdapter` | Thể loại | Gradient nền tạo động (`GradientDrawable`), hiệu ứng `ScaleAnimation` khi bấm |
| `PlaylistAdapter` | Playlist | Thumbnail bo góc, hiện `formatSongCount()` |

- Tất cả đều là **`ListAdapter` + `DiffUtil.ItemCallback`**:
  - `areItemsTheSame` so theo `encodeId` (cùng 1 bài).
  - `areContentsTheSame` so `==` (data class).
  - → `submitList()` chỉ cập nhật item thay đổi, có animation, không nhấp nháy toàn bộ.
- Click listener đặt trong `init {}` của ViewHolder (chỉ gán 1 lần, không gán lại mỗi lần bind — tối ưu).

### 2.9 `model/` — các data class

| File | Nội dung |
|---|---|
| `SearchResponse.kt` | `ApiResponse<T>` (generic wrapper: `success`, `data`, `error`) + `SongItem` (encodeId, title, artistsNames, thumbnail, thumbnailM, duration + `formatDuration()`) |
| `StreamResponse.kt` | `StreamData` (`@SerializedName("128")` quality128, `"320"` quality320 + `getBestStreamUrl()` — ưu tiên 320, lọc "VIP") |
| `PlaylistResponse.kt` | `PlaylistData` (encodeId, title, thumbnail, thumbnailM, artistsNames, `songs`) |
| `PlaylistItem.kt` | `PlaylistItem` cho card Home (encodeId, title, thumbnail, songCount + `formatSongCount()`) |
| `GenreItem.kt` | Thể loại tĩnh + màu gradient (10 loại hardcode) |
| `ChartResponse.kt` | `typealias ChartData = SongItem` (bảng xếp hạng tái dùng SongItem) |

### 2.10 `server/index.js` — proxy Node.js

- Gói `zingmp3-api-full` xử lý việc ký request tới ZingMP3 (không thể gọi trực tiếp từ Android).
- 5 endpoint (khớp 1-1 với `MusicApiService`):
  - `GET /api/search?q=` → trả danh sách bài (metadata, **không** link mp3).
  - `GET /api/song/:id/stream` → trả `{"128": url, "320": url}` — **lọc "VIP" → null** ở cả server lẫn app.
  - `GET /api/chart` → bảng xếp hạng.
  - `GET /api/playlist/:id` → chi tiết playlist + `songs`.
  - `GET /api/playlists` → lấy từ `getTop100()`, gom tất cả section thành 1 list, giới hạn 20.
- App gọi server qua `http://127.0.0.1:3000/` + **`adb reverse tcp:3000 tcp:3000`** (vì `10.0.2.2` không hoạt động với app process trên emulator này).

---

## 3. Truy vết 1 luồng thực tế từ đầu đến cuối

> **Luồng được chọn:** User mở app → bấm 1 **playlist** trên Home → xem danh sách bài → bấm 1 bài → nhạc phát → bài hết **tự động chuyển bài kế tiếp**.
> Đây là luồng "full-stack" nhất, chạm vào mọi tầng + tính năng auto-advance.

### Bước 0 — App khởi động (1 lần)
1. Android khởi tạo `App` (khai báo `android:name=".App"` trong manifest) → `App.onCreate()` → `startKoin(...)` đăng ký 3 module. *(file: `App.kt`)*

### Bước 1 — HomeActivity tải playlist nổi bật
2. Launcher mở `HomeActivity.onCreate()`: `setSupportActionBar`, `setupAdapters()`, `bindPlayerService()`, `setupMiniPlayer()`, `observeViewModel()`, `homeViewModel.loadHome()` + `loadRecent()`. *(file: `ui/HomeActivity.kt`)*
3. `HomeViewModel.loadHome()` (trong `viewModelScope.launch`) → `repository.getFeaturedPlaylists()`. *(file: `viewmodel/HomeViewModel.kt`)*
4. `MusicRepository.getFeaturedPlaylists()` → `apiService.getFeaturedPlaylists()` → Retrofit gửi `GET /api/playlists` → server `ZingMp3.getTop100()` gom 20 playlist → JSON. *(files: `repository/MusicRepository.kt`, `network/MusicApiService.kt`, `server/index.js`)*
5. Retrofit/Gson convert JSON → `List<PlaylistItem>` → `Result.success(...)` → `HomeViewModel._featuredPlaylists.value = list`. *(file: `viewmodel/HomeViewModel.kt`)*
6. `observeViewModel()` collect `featuredPlaylists` → `playlistAdapter.submitList(list)` → RecyclerView vẽ card playlist. *(file: `ui/HomeActivity.kt`)*

### Bước 2 — Bấm playlist → mở SongListActivity
7. User bấm card → `PlaylistAdapter.onPlaylistClick(playlist)` → `HomeActivity.openSongList(title, MODE_PLAYLIST, playlist.encodeId)` → `startActivity(Intent(... SongListActivity))`. *(files: `adapter/PlaylistAdapter.kt`, `ui/HomeActivity.kt`)*
8. `SongListActivity.onCreate()` đọc `EXTRA_MODE = "playlist"`, `EXTRA_ID = encodeId` → `playlistViewModel.loadPlaylist(id)`. *(file: `ui/SongListActivity.kt`)*
9. `PlaylistViewModel.loadPlaylist(id)` → `repository.getPlaylist(id)` → `GET /api/playlist/:id` → server trả `PlaylistData` → `_songs.value = playlist.songs` → adapter `submitList` → danh sách `SongItem` hiện lên. *(files: `viewmodel/PlaylistViewModel.kt`, `repository/MusicRepository.kt`, `server/index.js`)*

### Bước 3 — Bấm bài → playQueue
10. User bấm 1 bài → `SongAdapter` `onItemClick(song, position)` → `SongListActivity` lambda `{ _, position -> playQueue(playlistViewModel.songs.value, position) }`. *(files: `adapter/SongAdapter.kt`, `ui/SongListActivity.kt`)*
11. `BasePlayerActivity.playQueue(songs, index)`:
    - `showMiniPlayer(song)` — mini player hiện title/artist/thumbnail ngay.
    - `recentStore.add(song)` — ghi vào SharedPreferences (lịch sử nghe).
    - Service đã bind? → `musicService?.playQueue(songs, index)`; chưa → `pendingPlay = songs to index`. *(file: `ui/BasePlayerActivity.kt`)*

### Bước 4 — MusicService nhận queue, phát bài
12. `MusicService.playQueue(songs, startIndex)`:
    - `queue = songs`, `currentIndex = startIndex`.
    - `currentSong = song`, `onSongChanged?.invoke(song)` → BasePlayerActivity cập nhật mini player.
    - `preFetchedUrl = null` (luồng này không có URL sẵn) → `playCurrent()`. *(file: `service/MusicService.kt`)*
13. `playCurrent()`: `playbackScope.launch { repository.getStreamUrl(song.encodeId) }` → `GET /api/song/:id/stream` → `StreamData.getBestStreamUrl()` → URL mp3 (hoặc failure → bỏ qua bài, phát bài kế). *(files: `service/MusicService.kt`, `repository/MusicRepository.kt`)*
14. `playInternal(url, title, artist)`:
    - `playbackGeneration++`; release player cũ; `isPrepared = false`.
    - `MediaPlayer().apply { setDataSource(url); setOnPreparedListener {...}; setOnCompletionListener {...}; setOnErrorListener {...}; prepareAsync() }`.
    - `onPrepared` (đã buffer xong): `isPrepared = true`; `start()`; `onPlaybackStateChanged(true)` (mini player icon → pause); `startForeground(NOTIFICATION_ID, buildNotification(true))`. *(file: `service/MusicService.kt`)*

### Bước 5 — Auto-advance khi bài hết
15. Bài phát xong → `setOnCompletionListener`:
    - `onPlaybackStateChanged(false)`.
    - `currentIndex + 1 < queue.size` → `currentIndex++` → `playCurrent()` → lại `getStreamUrl` bài kế → `playInternal` → **bài mới tự phát** (nhạc không dừng giữa chừng).
    - `onSongChanged(songMoi)` → BasePlayerActivity `showMiniPlayer` → mini player + notification đổi sang bài mới.
    - Nếu hết danh sách → `updateNotification(false)`. *(file: `service/MusicService.kt`)*
16. User bấm play/pause trên mini player → `BasePlayerActivity.setupMiniPlayer()` → `musicService?.togglePlayPause()` → kiểm tra `isPrepared` rồi `player.pause()/start()`. *(files: `ui/BasePlayerActivity.kt`, `service/MusicService.kt`)*

**Tóm tắt dữ liệu biến đổi qua từng bước:**
```
PlaylistItem (JSON card) → PlaylistData.songs: List<SongItem> → queue: List<SongItem> + index
→ encodeId → GET stream → StreamData{128,320} → String url mp3 → MediaPlayer (setDataSource) → âm thanh
```

**Số file tham gia:** `HomeActivity` → `HomeViewModel` → `MusicRepository` → `MusicApiService` → server → (ngược) → `SongListActivity` → `PlaylistViewModel` → `SongAdapter` → `BasePlayerActivity` → `MusicService` → `MusicRepository` → `MediaPlayer`. (≈ 10 file + server.)

> Nếu bị hỏi luồng **tìm kiếm**: MainActivity `etSearch` → `MusicViewModel.search(q)` (debounce 500ms) → `repository.searchSongs(q)` → `GET /api/search` → `_songs` → adapter hiện kết quả → bấm bài → `viewModel.playSong(song)` → `_streamUrl` → MainActivity observe → `musicService?.playQueue(viewModel.getCurrentQueue(), startIndex, preFetchedUrl=url)`. Khác bản mới ở chỗ: màn hình cũ tự fetch URL trước rồi truyền cho service; màn hình mới để service tự fetch.

---

## 4. Điểm cần lưu ý khi bảo vệ đồ án

### 4.1 Những đoạn code "dễ bị hỏi" + cách trả lời ngắn gọn

| Nếu giám khảo hỏi | Trả lời gọn |
|---|---|
| **Tại sao có `isPrepared`?** | MediaPlayer báo lỗi `-38 (INVALID_OPERATION)` nếu gọi `start()` khi đang buffer (state PREPARING). `isPrepared` chỉ cho phép start sau `onPrepared`. Nếu bấm play/pause sớm thì bỏ qua lượt bấm — bài vẫn tự phát khi prepare xong. |
| **Tại sao có `playbackGeneration`?** | Khi bấm liên tục 2 bài, player cũ vừa release vẫn có thể gửi callback (onPrepared/onCompletion) muộn → sẽ gọi `start()` trên instance đã hỏng. Mỗi lần phát bài mới tăng biến này; callback nào có generation cũ thì bị bỏ qua. |
| **Vì sao Service lại tự gọi `MusicRepository`?** | Vì auto-advance chạy khi app ở background — Activity có thể đã bị hủy, không còn ai lấy URL bài kế. Service tự fetch nên nhạc chạy liên tục kể cả khóa màn hình. Koin `KoinComponent` + `by inject()` để service có repository. |
| **Vì sao là Foreground + Bound Service?** | Bound (Binder) để Activity gọi method trực tiếp; Foreground để Android 8+ không giết service, phát nhạc được ở background. Kèm `FOREGROUND_SERVICE_MEDIA_PLAYBACK` trong manifest. |
| **Tại sao `prepareAsync()` thay vì `prepare()`?** | `prepare()` chặn main thread khi tải/giải mã file lớn → ANR. `prepareAsync()` chạy nền, xong mới gọi `onPrepared`. |
| **Tại sao dùng `adb reverse` + `127.0.0.1` thay vì `10.0.2.2`?** | Trên emulator này `10.0.2.2` không kết nối được từ app process (dù shell vẫn ping được). `adb reverse tcp:3000 tcp:3000` chuyển port 3000 của device về host, nên gọi `127.0.0.1:3000` là tới server. |
| **Tại sao bật `usesCleartextTraffic="true"`?** | Android 9+ chặn HTTP (cleartext) mặc định. Server dev chỉ có HTTP, nên bật cờ này. Khi deploy HTTPS (Render) thì không ảnh hưởng. |
| **Tại sao phải lọc chuỗi "VIP" ở cả 2 nơi?** | ZingMP3 trả chữ `"VIP"` (không phải null) cho bài trả phí. Nếu để nguyên, MediaPlayer sẽ cố mở file tên "VIP" → `FileNotFoundException` crash. Lọc ở server (biến thành null) + ở app (`getBestStreamUrl()` kiểm tra `!= "VIP"`) cho chắc. |
| **Tại sao dùng `Result<T>` trong Repository?** | Bọc exception lại, ViewModel xử lý bằng `onSuccess/onFailure` rõ ràng, không phải try-catch rải rác. |
| **Tại sao dùng `StateFlow` thay `LiveData`?** | Kotlin-native, luôn có giá trị khởi tạo, dùng chung hệ coroutine, dễ kết hợp operator. |
| **Tại sao `repeatOnLifecycle(STARTED)`?** | Chỉ collect StateFlow khi Activity ở STARTED trở lên, tự dừng khi ở background — tiết kiệm tài nguyên, tránh cập nhật UI khi không nhìn thấy. |
| **Tại sao `pendingPlay`/`pendingUrl`?** | User có thể bấm bài trước khi Service bind xong (`musicService == null`). Lưu lại để phát ngay khi `onServiceConnected` — nếu không, thao tác bị nuốt mất. |
| **Tại sao ViewModel không giữ Context?** | ViewModel sống lâu hơn Activity; giữ Activity/Context gây memory leak. `RecentPlayedStore` lấy `androidContext()` (Application context) từ Koin — an toàn. |

### 4.2 Điểm yếu THẬT của code hiện tại (nói thật để bạn chủ động)

1. **2 cách tạo Retrofit song song:** `network/RetrofitClient.kt` (object) và `di/NetworkModule.kt` (Koin) — **BASE_URL bị khai báo 2 lần**. Nếu sửa URL phải sửa cả 2. Đây là hệ quả của việc "giữ bản cũ để so sánh trước/sau Koin".
2. **BASE_URL hardcode `http://127.0.0.1:3000/`:** chỉ chạy trên emulator có `adb reverse`. Cài lên máy thật sẽ không kết nối được; chưa có cơ chế đổi server.
3. **`MainActivity` + `MusicViewModel` vẫn dùng DI thủ công:** `ViewModelProvider` + tự `new MusicRepository()`. Code chạy tốt nhưng **không nhất quán** với Koin, và khó test hơn.
4. **`playFromUrl()` trong MusicService hiện không còn nơi nào gọi** — hàm "chết" còn để lại (MainActivity mới đã chuyển sang `playQueue`). Giám khảo tinh ý có thể hỏi; trả lời: "hàm giữ cho màn hình cũ, nhưng hiện mọi nơi đã dùng playQueue".
5. **Không xử lý `AudioFocus`:** nếu có app khác phát nhạc/cuộc gọi đến, app không tự pause. (MediaPlayer thuần, chưa request audio focus.)
6. **Notification không dùng `MediaStyle`** (chủ ý tránh thêm dependency `androidx.media`): nên **không có** ảnh nghệ sĩ trên màn hình khóa, không có thanh seek, không xử lý media button (tai nghe). Các nút Play/Pause/Stop vẫn hoạt động bình thường.
7. **Không dùng ExoPlayer:** MediaPlayer đủ dùng cho mp3 đơn giản, nhưng không có adaptive streaming, buffering control, gapless, tiết kiệm pin kém hơn.
8. **"Nghe gần đây" lưu JSON toàn bộ vào SharedPreferences:** OK với ≤20 bài, nhưng không scale được như Room/DataStore. Chưa có database.
9. **Thể loại nhạc hardcode** trong `GenreItem.all` và **bấm thể loại = search theo tên** → kết quả phụ thuộc chất lượng tìm kiếm (ví dụ "R&B" có thể ít bài hơn). Đây là workaround vì ZingMP3 không có API "liệt kê thể loại" kiểu card.
10. **Chưa có shuffle / repeat / next-prev thủ công** trên mini player — chỉ có auto-advance theo thứ tự list.
11. **`currentQueue` trong `MusicViewModel` chỉ chụp danh sách tại thời điểm bấm phát:** nếu user search từ khóa khác trong lúc đang phát, queue không tự cập nhật (chỉ phát tiếp theo danh sách cũ). Đây là hành vi có chủ đích nhưng có thể bị hỏi.
12. **Chưa có unit test / UI test thật** (chỉ test mặc định của template). Khi được hỏi, nói thẳng: "chưa viết test, đây là việc cải thiện tiếp theo".
13. **Khi auto-advance fetch URL thất bại**, service chỉ "bỏ qua bài" mà không thông báo cho user (chỉ gọi `onError`). Có thể thêm retry/countdown.

> Mẹo: nếu bị hỏi điểm yếu, **đừng giấu** — nói thẳng 2-3 điểm trên và đề xuất hướng sửa (Room cho recents, MediaSession, ExoPlayer, AudioFocus, thêm test). Điều đó tạo điểm cộng "biết đánh giá code của mình".

---

## 5. Từ khóa cần nhớ khi bảo vệ (glossary nhanh)

- **MVVM**: Model – View – ViewModel. View (Activity) hiển thị, ViewModel giữ state, Model là data.
- **StateFlow**: luồng state luôn có giá trị hiện tại; dùng `asStateFlow()` để không cho bên ngoài ghi.
- **viewModelScope**: CoroutineScope tự hủy khi ViewModel bị destroy → không leak coroutine.
- **repeatOnLifecycle(STARTED)**: collect an toàn theo vòng đời.
- **ListAdapter + DiffUtil**: tự so sánh list cũ/mới (`areItemsTheSame`, `areContentsTheSame`) → chỉ render phần thay đổi.
- **ViewBinding**: `ActivityXBinding.inflate(...)` thay `findViewById` — type-safe.
- **Retrofit**: thư viện biến interface Kotlin thành HTTP client (Proxy pattern); `suspend fun` → chạy nền.
- **OkHttp interceptor**: chèn vào chuỗi request/response (dùng để log).
- **Repository Pattern**: ViewModel không biết data từ đâu; đổi data source không đụng ViewModel.
- **Result<T>**: bọc giá trị hoặc exception.
- **Koin**: DI container; `module{}` khai báo công thức; `single{}` (1 instance), `viewModel{}` (gắn vòng đời Activity), `get()` (lấy dependency), `by inject()`/`by viewModel()` (bơm vào), `KoinComponent` (cho class không phải Activity như Service).
- **DI / Inversion of Control**: class khai báo "tôi cần gì", không tự tạo; ai đó (Koin) đưa vào.
- **Bound Service + Binder**: Activity lấy reference service để gọi method.
- **Foreground Service + startForeground**: chạy nền lâu dài với notification, không bị hệ thống giết.
- **START_NOT_STICKY**: service bị kill không tự restart.
- **MediaPlayer**: `prepareAsync`/`onPrepared` (buffer nền), `setOnCompletionListener` (hết bài), `setOnErrorListener`, `release()` (giải phóng tài nguyên).
- **PendingIntent**: intent "đóng gói" gửi sau (notification button → service).
- **SharedPreferences**: lưu cặp key-value nhỏ, persist qua các lần mở app.
- **adb reverse**: chuyển port thiết bị → host (giúp app gọi server local).
- **Cleartext HTTP**: Android chặn HTTP mặc định từ API 28; bật `usesCleartextTraffic`.

---

*File này được tạo từ việc đọc toàn bộ code thật trong project (2026-08-18). Nếu sửa code, hãy cập nhật lại file này cho khớp.*
