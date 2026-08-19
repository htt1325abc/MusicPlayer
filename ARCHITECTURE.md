# ARCHITECTURE.md — Kiến trúc đồ án MusicPlayer (Kotlin Android)

> File này giải thích **code thật trong project** (không phải lý thuyết chung chung) để bạn ôn trước khi bảo vệ đồ án.
> Đọc kèm các file được trích dẫn để nắm chắc.
> ⚠️ **Đã cập nhật 2026-08-19** sau khi refactor **Phần 3** (áp dụng convention project MẪU `DIYWallpaper_Kotlin`):
> thêm base class `common/`, pattern `onState()`, Repository interface/impl, Room cho "Nghe gần đây" + "Yêu thích" + "Playlist đã lưu".

---

## 0. Tóm tắt 1 câu

**MusicPlayer** là app Android phát nhạc trực tuyến theo kiến trúc **MVVM + Repository Pattern + Clean Architecture rút gọn** (có `common/` base class, `domain/` interface, `data/` impl), dùng **Koin** quản lý dependency, **Retrofit/OkHttp** gọi một **server Node.js proxy** (`zingmp3-api-full`) để lấy bài hát & link mp3, phát nhạc bằng **MediaPlayer** + **MediaSessionCompat** bên trong một **Foreground + Bound Service**, và dùng **Room** để lưu "Nghe gần đây", "Yêu thích" và "Playlist đã lưu".

---

## 1. Tổng quan kiến trúc

### 1.1 Công nghệ sử dụng (`gradle/libs.versions.toml` + `app/build.gradle.kts`)

| Thành phần | Thư viện / version | Dùng để làm gì |
|---|---|---|
| UI | Material 3 (`com.google.android.material` 1.14.0) | Theme dark, Toolbar, TextInputLayout |
| Danh sách | RecyclerView 1.4.0 | List + DiffUtil (`ListAdapter`) |
| ViewBinding | `buildFeatures { viewBinding = true }` | Truy cập view type-safe, không `findViewById` |
| Network | Retrofit 2.11.0 + OkHttp 4.12.0 + Gson 2.11.0 | Gọi REST API, log request/response |
| Ảnh | Glide 4.16.0 | Load & cache thumbnail, album art |
| Bất đồng bộ | Coroutines 1.10.2 | `suspend`, `viewModelScope`, `StateFlow` |
| Lifecycle | `lifecycle-viewmodel-ktx` 2.9.1 | ViewModel, `repeatOnLifecycle` |
| DI | Koin 3.5.6 (`io.insert-koin:koin-android`) | Quản lý dependency tập trung |
| Local DB | **Room 2.7.1** (runtime + ktx + compiler) | Lưu recent / favorite / saved playlist |
| Code-gen | **KSP 2.3.9** | Chạy Room compiler (`@Dao`/`@Database`) lúc build |
| Media | **androidx.media 1.7.0** (`NotificationCompat.MediaStyle`) | MediaSession + notification media controls |
| Audio | `MediaPlayer` (Android SDK) + `MediaSessionCompat` | Phát nhạc + điều khiển từ lock screen/tai nghe |
| Server | Node.js + Express + `zingmp3-api-full` v1.0.14 | Proxy trung gian tới ZingMP3 (port 3000) |

> ⚠️ Khác project MẪU: mẫu dùng **DataBinding + Koin 4.0.0** (`viewModelOf`); project này dùng **ViewBinding + Koin 3.5.6** (`viewModel { X(get(), ...) }`). Cùng tinh thần, khác cú pháp — xem mục 2.4.

### 1.2 Cấu trúc package / layer (SAU refactor Phần 3)

Package gốc: `com.example.musicplayer` (`app/src/main/java/com/example/musicplayer/`)

```
com.example.musicplayer/
├── App.kt                  ── Application — startKoin (đăng ký 4 module)
├── common/                 ⭐ BASE CLASS dùng chung: IActivity, IViewModel, ResultFlow
├── data/
│   ├── local/              ⭐ ROOM: MusicDatabase v2, Migrations, dao/, entities/, mapper/, repository/
│   └── repository/         ── MusicRepositoryImpl (implementation)
├── domain/
│   └── repository/         ⭐ MusicRepository (INTERFACE)
├── di/                     ── 4 Koin module: network / repository / service / viewModel
├── model/                  ── data class DTO map với JSON (`SongItem`, `PlaylistItem`, `StreamData`...)
├── network/                ── MusicApiService (endpoint), RetrofitClient (⚠️ dead code — xem 4.2)
├── presenter/              ⭐ UI theo FEATURE (giống mẫu): base/, home/, search/, songlist/
│   ├── base/BasePlayerActivity.kt
│   ├── home/HomeActivity.kt + HomeViewModel.kt
│   ├── search/MainActivity.kt + MusicViewModel.kt
│   └── songlist/SongListActivity.kt + PlaylistViewModel.kt
├── adapter/                ── SongAdapter, RecentSongAdapter, GenreAdapter, PlaylistAdapter
├── service/                ── MusicService, MediaSessionManager, PlaybackController, MusicPlaybackController
└── res/                    ── Layout XML, drawable, menu, theme
```

| Layer | Package | Vai trò |
|---|---|---|
| **Base / Cross-cutting** | `common/` | Base class + wrapper state — dùng xuyên suốt mọi màn hình |
| **Domain** | `domain/repository/` | Interface repository (hợp đồng, không biết data từ đâu) |
| **Data** | `data/local/` (Room), `data/repository/` (impl) | Đọc/ghi DB, gọi API — **nơi DUY NHẤT biết chi tiết** |
| **Presentation** | `presenter/<feature>/` | Activity + ViewModel đi chung 1 feature package |
| **Service** | `service/` | Phát nhạc nền, media session, notification |
| **DI** | `di/` | 4 Koin module nối tất cả lại |

> ✅ So với bản cũ (trước Phần 3): `viewmodel/`+`ui/` → gộp thành `presenter/<feature>/`; `repository/` → tách `domain/repository/` (interface) + `data/repository/` (impl) + `data/local/repository/` (local repo Room); **không còn SharedPreferences** — "Nghe gần đây" đã chuyển sang Room.

### 1.3 Sơ đồ phụ thuộc (ai gọi ai — chiều mũi tên đi XUỐNG)

```
┌────────────────────────  VIEW LAYER (presenter/)  ──────────────────────────┐
│  HomeActivity (launcher) ──mở──► SongListActivity ──mở──► MainActivity     │
│        │  extends              │  extends              │  extends          │
│        ▼                       ▼                       ▼                  │
│  BasePlayerActivity (extends IActivity: bind service + mini player + playQueue)│
└───────────┬──────────────────────┬──────────────────────────────────────────┘
            │ onState(State.X)     │ observe StateFlow (Room Flow)
            ▼                      ▼
┌────────────────  VIEWMODEL LAYER (IViewModel<State> + onState)  ────────────┐
│  HomeViewModel<HomeState>  PlaylistViewModel<PlaylistState>  MusicViewModel<MusicState>│
└───────────┬─────────────────────────────────────────────────────────────────┘
            │ suspend fun
            ▼
┌────────────────  DOMAIN (interface)  ───────────────────────────────────────┐
│  MusicRepository (interface — chỉ khai báo, không có code)                 │
└───────────┬─────────────────────────────────────────────────────────────────┘
            ▼
┌────────────────  DATA (implementation)  ────────────────────────────────────┐
│  MusicRepositoryImpl ─► MusicApiService (Retrofit) ─► server:3000          │
│                       ─► FavoriteSongDao / PlaylistDao (Room)              │
│  RecentPlayedStore   ─► RecentSongDao (Room)                               │
└───────────┬─────────────────────────────────────────────────────────────────┘
            ▼
┌────────────────  SERVICE LAYER  ────────────────────────────────────────────┐
│  MusicService (Foreground + Bound)                                         │
│    ├─ MediaPlayer (phát nhạc)  +  MediaSessionManager (notification/Media) │
│    └─ by inject() MusicRepository (tự lấy URL bài kế khi auto-advance)     │
│  PlaybackController (interface) ← MusicPlaybackController (Koin single)    │
└─────────────────────────────────────────────────────────────────────────────┘
```

**Quy tắc phụ thuộc:**
```
Activity → ViewModel.onState() → MusicRepository(interface) → MusicRepositoryImpl → ApiService/server + Room
Activity → BasePlayerActivity → bind MusicService (Binder)
ViewModel → PlaybackController(interface) → MusicPlaybackController → MusicService.next()/previous()
Service  → MusicRepository (Koin inject — để tự auto-advance khi chạy nền)
Dữ liệu trả về đi NGƯỢC lên: server/Room → Repository → ViewModel (StateFlow) → UI
```

**Nguyên tắc quan trọng nhất:** View không gọi thẳng API; ViewModel **không giữ reference Service** (chỉ giữ `PlaybackController` interface); **Service là nơi DUY NHẤT giữ MediaPlayer**; Repository chia **interface (domain)** và **impl (data)**.

---

## 2. Đi qua từng thành phần quan trọng

> Format: **nhiệm vụ → caller → callee → xóa thì hỏng gì**.

### 2.1 `App.kt` — khởi động Koin

- **Nhiệm vụ:** `class App : Application()`, `onCreate()` gọi:
  ```kotlin
  startKoin {
      androidLogger(Level.INFO)
      androidContext(this@App)          // cung cấp Application/Context cho Koin
      modules(networkModule, repositoryModule, serviceModule, viewModelModule)
  }
  ```
- **Caller:** Hệ điều hành Android (khai báo `android:name=".App"` trong manifest).
- **Callee:** `startKoin` đăng ký **4 module** DI.
- **Xóa thì hỏng:** mọi thứ dùng Koin crash ngay — *"KoinApplication has not been started"*.
- **Trả lời khi bị hỏi:** *"Koin phải start ở Application.onCreate() vì nó chạy trước mọi Activity/Service, nên khi bất kỳ màn hình nào inject thì Koin đã sẵn sàng."*

### 2.2 `common/` — Base class (⭐ học từ project MẪU)

| File | Vai trò |
|---|---|
| `common/IViewModel.kt` | Base ViewModel. |
| `common/IActivity.kt` | Base Activity (template method). |
| `common/ResultFlow.kt` | Sealed class bọc trạng thái tải dữ liệu. |

**`IViewModel.kt`** — `abstract class IViewModel<State : IViewModel.IState>(application: Application) : AndroidViewModel(application)`:
- **Nhiệm vụ:**
  - `abstract fun onState(state: State)` — **pattern COMMAND**: UI gửi sealed-class State, ViewModel `when(state)` xử lý. (Không còn Activity gọi hàm public trực tiếp.)
  - `protected fun launchBlock(dispatcher = Main.immediate)`: chạy coroutine trong `viewModelScope` kèm `CoroutineExceptionHandler` (log lỗi, không crash).
  - `protected suspend fun withIO()/withMain()`: đổi thread.
  - `private _isLoading : MutableStateFlow<Boolean>` + `internal val isLoading` + `fun setLoading()` — mọi màn hình tự show/hide loading.
  - `protected fun toast()/string()`.
  - `interface IState` — marker cho sealed class State của từng màn hình.
- **Caller:** `HomeViewModel`/`PlaylistViewModel`/`MusicViewModel` kế thừa và implement `onState`.
- **Callee:** Activity gọi `viewModel.onState(...)`.
- **Xóa thì hỏng:** 3 ViewModel không compile được; mất pattern thống nhất.
- ⚠️ **Khác mẫu:** mẫu cho `IViewModel` kế thừa thêm `KoinComponent` (để tự `by inject()` trong VM). Project này bỏ vì mọi dependency đều qua **constructor** (Koin bơm) — không cần.

**`IActivity.kt`** — `abstract class IActivity<VB : ViewBinding, VM : IViewModel<State>, State : IViewModel.IState> : AppCompatActivity()`:
- **Nhiệm vụ:** ép mọi màn hình theo template:
  ```
  onCreate → setupInit() → setContentView(viewBinding.root) → initViews() → initObservers() → initListeners()
  ```
  - `abstract getLazyViewModel(): Lazy<VM>` — thường `viewModel<HomeViewModel>()`.
  - `abstract getLazyViewBinding(): Lazy<VB>` — thường `lazy { ActivityXBinding.inflate(layoutInflater) }`.
  - `protected fun observerLoadingState(onLoading, onLoaded)` — collect `viewModel.isLoading` theo lifecycle STARTED.
- **Caller:** `BasePlayerActivity` kế thừa (→ HomeActivity/SongListActivity/MainActivity).
- **Xóa thì hỏng:** các Activity mất template + loading chung.

**`ResultFlow.kt`** — sealed class `Initial / Loading(info) / Success(data) / Error(msg)` + extension `doOnSuccess/doOnLoading/doOnError`. Dự phòng cho màn hình cần phân biệt rõ trạng thái (hiện các màn hình dùng StateFlow như mẫu).

### 2.3 `network/` — Retrofit + OkHttp

**`MusicApiService.kt`** (interface) — 5 endpoint `suspend fun`:
- `@GET("api/search") searchSongs(@Query("q") query): ApiResponse<List<SongItem>>`
- `@GET("api/song/{id}/stream") getStreamUrl(@Path("id") songId): ApiResponse<StreamData>`
- `@GET("api/chart") getChart(): ApiResponse<List<ChartData>>`
- `@GET("api/playlist/{id}") getPlaylistDetail(@Path("id") playlistId): ApiResponse<PlaylistData>`
- `@GET("api/playlists") getFeaturedPlaylists(): ApiResponse<List<PlaylistItem>>`
- **Caller:** `MusicRepositoryImpl`. **Callee:** server `http://127.0.0.1:3000/`.

**`RetrofitClient.kt`** (object singleton) — ⚠️ **HIỆN LÀ DEAD CODE**: sau refactor, mọi nơi dùng Koin (`NetworkModule`), không file nào gọi `RetrofitClient.apiService` nữa. Giữ lại như "bản cũ để so sánh", nhưng nên xóa khi có dịp (xem 4.2).

### 2.4 `di/` — 4 Koin module + cách khởi tạo THẬT

**`NetworkModule.kt`** (`networkModule`):
```kotlin
single { OkHttpClient.Builder()...build() }                          // (1) tự tạo
single { Retrofit.Builder().baseUrl("...").client(get())...build() } // (2) get() = (1)
single { get<Retrofit>().create(MusicApiService::class.java) }       // (3) get() = (2)
```

**`RepositoryModule.kt`** (`repositoryModule`) — **đăng ký cả Room + Repository**:
```kotlin
single { Room.databaseBuilder(androidContext(), MusicDatabase::class.java, DATABASE_NAME)
             .addMigrations(*Migrations.ALL_MIGRATIONS).build() }     // (1) Room DB
single { get<MusicDatabase>().recentSongDao() }                       // (2) 3 DAO
single { get<MusicDatabase>().favoriteSongDao() }
single { get<MusicDatabase>().playlistDao() }
single { RecentSongMapper() } ; single { FavoriteSongMapper() } ; single { PlaylistMapper() } // (3) mapper
single { RecentPlayedStore(get(), get()) }                            // (4) local repo (DAO + Mapper)
singleOf(::MusicRepositoryImpl) bind MusicRepository::class           // (5) ⭐ interface ← impl
```
- ⭐ `singleOf(::MusicRepositoryImpl) bind MusicRepository::class` — giống mẫu `singleOf(...) bind Interface::class`. Mọi nơi inject `MusicRepository` đều nhận đúng 1 impl duy nhất. Koin tự `get()` các tham số constructor của `MusicRepositoryImpl` (ApiService, DAO, Mapper).

**`ServiceModule.kt`** (`serviceModule`):
```kotlin
single<PlaybackController> { MusicPlaybackController() }
```
- ⚠️ Khai báo theo **INTERFACE** `PlaybackController`. Nếu khai báo class cụ thể, inject interface sẽ `NoBeanDefFoundException` (đã từng crash).

**`ViewModelModule.kt`** (`viewModelModule`):
```kotlin
viewModel { HomeViewModel(get(), get(), get(), get()) }     // Application + MusicRepository + RecentPlayedStore + PlaybackController
viewModel { PlaylistViewModel(get(), get(), get()) }        // Application + MusicRepository + PlaybackController
viewModel { MusicViewModel(get(), get(), get()) }           // Application + MusicRepository + PlaybackController
```
- `get()` đầu tiên resolve **Application** (để `IViewModel` extends `AndroidViewModel`) — do `androidContext()` đăng ký sẵn.
- ⚠️ Mẫu dùng `viewModelOf(::HomeViewModel)` (Koin 4); project pin Koin 3.5.6 → dùng DSL tương đương `viewModel { X(get(), ...) }`.

**Cách Koin hoạt động (quan trọng để trả lời bảo vệ):**
- `startKoin()` chỉ **đăng ký công thức** (definition). Dependency tạo **lười (lazy)** — lần đầu được yêu cầu.
- Khi `HomeActivity` gọi `by viewModel<HomeViewModel>()`, Koin: tìm định nghĩa → thấy cần 4 tham số → lần lượt tạo `Application` (có sẵn), `MusicRepository` (→ `MusicRepositoryImpl` → `MusicApiService` → `Retrofit` → `OkHttpClient`), `RecentPlayedStore` (→ `RecentSongDao` → `MusicDatabase`), `PlaybackController` (→ `MusicPlaybackController`) → gắn VM vào `ViewModelStore` của Activity.
- `single {}` = 1 instance toàn app; `viewModel {}` = gắn vòng đời Activity.

### 2.5 `data/local/` — Room (⭐ thay SharedPreferences)

**`MusicDatabase.kt`** — `@Database(entities = [RecentSongEntity, FavoriteSongEntity, PlaylistEntity], version = 2, exportSchema = false)`. `DATABASE_NAME = "music_player.db"`.

| Entity | Bảng | Field đặc trưng | Mục đích |
|---|---|---|---|
| `RecentSongEntity` | `recent_songs` | `encodeId` (PK), `playedAt` | "Nghe gần đây" |
| `FavoriteSongEntity` | `favorite_songs` | `encodeId` (PK), `addedAt` | "Yêu thích" |
| `PlaylistEntity` | `saved_playlists` | `encodeId` (PK), `songCount`, `savedAt` | "Playlist đã lưu" (Phần 3) |

**`Migrations.kt`** — `MIGRATION_1_2`: `CREATE TABLE IF NOT EXISTS saved_playlists (...)`. Version 1 → 2 thêm bảng playlist mà **không mất dữ liệu cũ** (recent/favorite giữ nguyên).

**`dao/`** — `RecentSongDao`, `FavoriteSongDao`, `PlaylistDao`:
- `@Insert(onConflict = REPLACE)` — ghi đè theo `encodeId` (không trùng).
- `@Query("... ORDER BY x DESC") fun observeAll(): Flow<List<Entity>>` — **trả Flow** → UI tự cập nhật khi bảng đổi (single source of truth).
- `getById`, `deleteById`, `clear`.

**`mapper/`** — `interface Mapper<Entity, Model> { toModel(entity); toEntity(model) }` + `RecentSongMapper`, `FavoriteSongMapper`, `PlaylistMapper`. Chuyển Entity ↔ Model (trước đây là extension function trong file entity).

**`repository/RecentPlayedStore.kt`** — local repository bọc `RecentSongDao` + `RecentSongMapper`:
- `observeRecent(): Flow<List<SongItem>>` (giới hạn 20), `getRecentSnapshot()`, `add(song)`.
- **Caller:** `HomeViewModel` (observe), `BasePlayerActivity.playQueue()` (ghi khi phát bài).

### 2.6 `domain/repository/` + `data/repository/` — Repository pattern (interface/impl)

**`domain/repository/MusicRepository.kt`** (INTERFACE):
- Network: `searchSongs`, `getStreamUrl`, `getChart`, `getPlaylist`, `getFeaturedPlaylists` → đều trả `Result<T>`.
- Room: `observeFavorites(): Flow`, `isFavorite`, `toggleFavorite`, `observeSavedPlaylists(): Flow`, `isSavedPlaylist`, `toggleSavePlaylist`.

**`data/repository/MusicRepositoryImpl.kt`** (IMPL):
- Constructor nhận `MusicApiService` + `FavoriteSongDao` + `PlaylistDao` + `FavoriteSongMapper` + `PlaylistMapper` (Koin bơm).
- Network method: gọi API, kiểm tra `response.success && data != null` → `Result.success/failure`.
- `getStreamUrl`: gọi `StreamData.getBestStreamUrl()` (ưu tiên 320, fallback 128, **lọc chuỗi "VIP"**).
- Room method: `dao.observeAll().map { mapper.toModel }` (Flow) / `toggleX` = getById → insert hoặc delete.

**Tại sao tách interface/impl?** ViewModel/Service chỉ phụ thuộc interface → dễ test (fake repo), dễ thay data source, đúng "Inversion of Control".

### 2.7 `presenter/` — các màn hình & pattern `onState()`

**`base/BasePlayerActivity.kt`** — lớp nền dùng chung cho 3 màn hình:
- extends `IActivity<VB, VM, State>()` (→ template vòng đời).
- `recentStore: RecentPlayedStore by inject()` (Koin).
- `playbackController: PlaybackController by inject()` + cast `as MusicPlaybackController` để gán `.service`.
- `bindPlayerService()/unbindPlayerService()` — bind/unbind MusicService.
- `serviceConnection` — gán `onPlaybackStateChanged/onError/onSongChanged`; flush `pendingPlay` khi service sẵn sàng; gán `playbackImpl.service = musicService`.
- `playQueue(songs, index)` — show mini player + `recentStore.add(song)` (Room) + `musicService?.playQueue(...)` hoặc `pendingPlay`.
- `setupMiniPlayer()` — nút **Prev/PlayPause/Next**: play/pause → `playbackController.togglePlayPause()`; Next/Prev → hook `onNext()/onPrevious()` (Activity con override → `viewModel.onState(State.Next/Previous)`).

**`home/HomeActivity.kt` + `HomeViewModel.kt`** (LAUNCHER):
- `HomeViewModel : IViewModel<HomeState>` — constructor `(Application, MusicRepository, RecentPlayedStore, PlaybackController)`.
- `init {}` gọi `observeRecentSongs()`, `observeFavorites()`, `observeSavedPlaylists()` (Room Flow) + `onState(HomeState.FetchFeaturedPlaylists)`.
- `onState(HomeState)` xử lý: `FetchFeaturedPlaylists` → loadHome; `ToggleFavorite`; `ToggleSavePlaylist`; `Next`/`Previous`/`TogglePlayPause` → `playbackController`.
- Activity: 5 section — Thể loại, Playlist nổi bật, **Playlist đã lưu**, Yêu thích, Nghe gần đây. Bấm bài → `playQueue(...)`. Nút bookmark → `onState(HomeState.ToggleSavePlaylist(playlist))`.

**`songlist/SongListActivity.kt` + `PlaylistViewModel.kt`**:
- `PlaylistViewModel : IViewModel<PlaylistState>` — xử lý `LoadPlaylist(id)` (mode playlist) và `SearchSongs(title, keyword)` (mode thể loại).
- Activity: đọc `EXTRA_MODE/EXTRA_TITLE/EXTRA_ID` → `viewModel.onState(PlaylistState.LoadPlaylist/SearchSongs)`. Bấm bài → `playQueue(viewModel.songs.value, position)`.

**`search/MainActivity.kt` + `MusicViewModel.kt`**:
- ✅ Sau refactor: MainActivity **đã migrate sang Koin + BasePlayerActivity** (trước đây DI thủ công). Giờ giống Home/SongList: `playQueue(cả danh sách, vị trí)` — Service tự fetch URL & auto-advance.
- `MusicViewModel : IViewModel<MusicState>` — `Search(query)` có **debounce 500ms** (`searchJob?.cancel()` + `delay(500)`), `ToggleFavorite`.
- Đã bỏ luồng cũ `playSong()/streamUrl` (Service tự lo URL).

**Sealed class State — khai báo CUỐI file ViewModel** (đúng convention mẫu):
```kotlin
sealed class HomeState : IViewModel.IState {
    data object FetchFeaturedPlaylists : HomeState()
    data class ToggleFavorite(val song: SongItem) : HomeState()
    data class ToggleSavePlaylist(val playlist: PlaylistItem) : HomeState()
    data object Next : HomeState()
    data object Previous : HomeState()
    data object TogglePlayPause : HomeState()
}
```

### 2.8 `service/` — trái tim phát nhạc

**`MusicService.kt`** — `class MusicService : Service(), KoinComponent, MediaStateProvider`:
- **Vòng đời:** Bound (`onBind` → `MusicBinder`) + Foreground (`startForeground` khi phát, `foregroundServiceType="mediaPlayback"`); `onCreate` tạo NotificationChannel + `MediaSessionManager`; `onDestroy` hủy scope + release player.
- **Hàng đợi:** `queue: List<SongItem>` + `currentIndex` → auto-advance.
- **Các cờ chống lỗi MediaPlayer (quan trọng):**
  - `isPrepared` — chỉ cho `start()` sau `onPrepared` (tránh lỗi `-38` "start called in state 4").
  - `playbackGeneration` — tăng mỗi lần `playInternal`; callback player cũ (đã release) bị bỏ qua.
  - `isSwitchingSong` — chặn bấm Next/Prev liên tục khi đang fetch URL (tránh lệch index).
- **`onStartCommand`:** xử lý 6 action `ACTION_PLAY/PAUSE/PLAY_PAUSE/NEXT/PREVIOUS/STOP` + `Intent.ACTION_MEDIA_BUTTON` → `MediaButtonReceiver.handleIntent(mediaSessionManager.session, intent)`.
- **Hàm chính:** `playQueue(songs, startIndex, preFetchedUrl?)`, `playCurrent()` (lấy URL qua `repository.getStreamUrl` trên `playbackScope`; **khi fail: log + onError + GIỮ nguyên bài cũ, không đệ quy nhảy bài**), `playInternal()` (tạo MediaPlayer + `prepareAsync` + listeners), `next()/previous()/play()/pause()/togglePlayPause()`, `stopPlayback()`.
- **Auto-advance** trong `setOnCompletionListener`: `currentIndex++` → `playCurrent()` (kể cả app ở nền).
- **`MediaStateProvider`** (implement): `isPlaying`, `getCurrentPositionMs`, `getDurationMs`, `getTitle`, `getArtist`, `getArtUrl`, `refreshNotification` — để `MediaSessionManager` đọc state mà không phụ thuộc class cụ thể.

**`MediaSessionManager.kt`** — đóng gói `MediaSessionCompat` + build notification **MediaStyle**:
- `PlaybackStateCompat` (STATE_PLAYING/PAUSED) + actions `PLAY/PAUSE/PLAY_PAUSE/SKIP_TO_NEXT/SKIP_TO_PREVIOUS/STOP`.
- Notification 3 nút **Prev · Play/Pause · Next**; load ảnh album bằng Glide (bitmap → large icon); vòng lặp 1s cập nhật position (progress lock screen).
- Bấm thân notification → mở `MainActivity`. Bấm nút → route về `MusicService` qua MediaSession callback.
- **Caller:** `MusicService.onCreate()`. **Callee:** `MediaStateProvider` (chính là MusicService).

**`PlaybackController.kt`** (interface): `play()/pause()/togglePlayPause()/next()/previous()/isPlaying()`.
**`MusicPlaybackController.kt`** (Koin `single`, implement): giữ `@Volatile var service: MusicService?` (Activity gán khi bind), mọi method ủy quyền xuống Service; service null → no-op an toàn. **Tại sao cần interface?** ViewModel không phụ thuộc trực tiếp Android Service → dễ test, dễ đổi engine (ExoPlayer).

### 2.9 `adapter/` — 4 RecyclerView Adapter

| Adapter | List | Đặc điểm |
|---|---|---|
| `SongAdapter` | Bài hát (search/playlist) | `onItemClick(song, position)` + **`onFavoriteClick`** + `updateFavorites(ids)` (icon tim, re-bind dòng thay đổi) |
| `RecentSongAdapter` | Nghe gần đây / Yêu thích | Thumbnail tròn (`CircleCrop`), nút play nhỏ riêng |
| `GenreAdapter` | Thể loại | Gradient nền tạo động, `ScaleAnimation` khi bấm |
| `PlaylistAdapter` | Playlist nổi bật / Đã lưu | `onPlaylistClick` + **`onSaveClick`** (bookmark) + `updateSaved(ids)` (icon đầy/rỗng) |

- Tất cả là `ListAdapter + DiffUtil.ItemCallback` (`areItemsTheSame` = encodeId, `areContentsTheSame` = `==`). Click listener đặt trong `init {}` (chỉ gán 1 lần).

### 2.10 `model/` — data class (DTO)

| File | Nội dung |
|---|---|
| `SearchResponse.kt` | `ApiResponse<T>` (generic: `success`, `data`, `error`) + `SongItem` (encodeId, title, artistsNames, thumbnail, thumbnailM, duration + `formatDuration()`) |
| `StreamResponse.kt` | `StreamData` (`@SerializedName("128"/"320")` + `getBestStreamUrl()` — ưu tiên 320, **lọc "VIP"**) |
| `PlaylistResponse.kt` | `PlaylistData` (encodeId, title, thumbnail, thumbnailM, artistsNames, `songs`) |
| `PlaylistItem.kt` | Card Home (encodeId, title, thumbnail, songCount + `formatSongCount()`) |
| `GenreItem.kt` | 10 thể loại tĩnh + màu gradient |
| `ChartResponse.kt` | `typealias ChartData = SongItem` |

### 2.11 `server/index.js` — proxy Node.js

- Gói `zingmp3-api-full` ký request tới ZingMP3 (không gọi trực tiếp từ Android được).
- 5 endpoint khớp 1-1 `MusicApiService`; `GET /api/song/:id/stream` trả `{128, 320}` và **lọc "VIP" → null** (cả server lẫn app).
- App gọi qua `http://127.0.0.1:3000/` + **`adb reverse tcp:3000 tcp:3000`** (10.0.2.2 không hoạt động trên emulator này).

---

## 3. Truy vết 1 luồng thực tế từ đầu đến cuối

> **Luồng:** mở app → bấm playlist → chọn bài → phát → **Next** qua mini player → hết bài **auto-advance** → bookmark playlist.

### Bước 0 — Khởi động
1. Android khởi tạo `App` → `startKoin` đăng ký 4 module (lazy). *(App.kt)*

### Bước 1 — Home tải dữ liệu
2. Launcher mở `HomeActivity` → template `initViews()` (toolbar, adapters, `bindPlayerService()`, `setupMiniPlayer()`) + `initObservers()` (collect StateFlow). *(presenter/home/HomeActivity.kt)*
3. `HomeViewModel.init {}`:
   - `observeRecentSongs()/observeFavorites()/observeSavedPlaylists()` → collect **Room Flow** (tự cập nhật khi bảng đổi).
   - `onState(HomeState.FetchFeaturedPlaylists)` → `loadHome()` → `repository.getFeaturedPlaylists()` → Retrofit → server → `_featuredPlaylists`. *(presenter/home/HomeViewModel.kt)*
4. `initObservers()` collect `featuredPlaylists/recentSongs/favorites/savedPlaylists/savedPlaylistIds` → `adapter.submitList()` → UI vẽ. *(presenter/home/HomeActivity.kt)*

### Bước 2 — Bấm playlist → SongListActivity
5. Bấm card → `PlaylistAdapter.onPlaylistClick` → `openSongList(title, MODE_PLAYLIST, encodeId)` → `startActivity(SongListActivity)`. *(adapter/PlaylistAdapter.kt, presenter/home/HomeActivity.kt)*
6. `SongListActivity.initViews()` đọc `EXTRA_MODE = "playlist"` → `viewModel.onState(PlaylistState.LoadPlaylist(id))`. *(presenter/songlist/SongListActivity.kt)*
7. `PlaylistViewModel.loadPlaylist(id)` → `repository.getPlaylist(id)` → `GET /api/playlist/:id` → `_songs.value = playlist.songs` → adapter vẽ danh sách. *(presenter/songlist/PlaylistViewModel.kt, data/repository/MusicRepositoryImpl.kt)*

### Bước 3 — Bấm bài → playQueue
8. Bấm bài → `SongAdapter.onItemClick` → `playQueue(viewModel.songs.value, position)`:
   - `showMiniPlayer(song)` — mini player hiện ngay.
   - `recentStore.add(song)` — ghi Room (`recent_songs`) → Home tự cập nhật "Nghe gần đây".
   - `musicService?.playQueue(songs, index)` (hoặc `pendingPlay` nếu chưa bind). *(presenter/base/BasePlayerActivity.kt)*

### Bước 4 — MusicService phát
9. `MusicService.playQueue(songs, startIndex)` → set queue/index, `onSongChanged` → `playCurrent()`. *(service/MusicService.kt)*
10. `playCurrent()`: `playbackScope.launch { repository.getStreamUrl(encodeId) }` → `StreamData.getBestStreamUrl()` → URL mp3. *(service/MusicService.kt, data/repository/MusicRepositoryImpl.kt)*
11. `playInternal(url, title, artist)`:
    - `playbackGeneration++`; release player cũ; `isPrepared = false`.
    - `MediaPlayer().apply { setDataSource; setOnPreparedListener; setOnCompletionListener; setOnErrorListener; prepareAsync() }`.
    - `onPrepared` → `isPrepared = true`; `start()`; `startForeground`; `mediaSessionManager.updateMetadata()/updatePlaybackState()`. *(service/MusicService.kt)*

### Bước 5 — Next/Prev + auto-advance
12. Bấm **Next** mini player → `onNext()` → `viewModel.onState(HomeState.Next)` → `playbackController.next()` → `MusicService.next()` → `currentIndex++` → `playCurrent()`. *(presenter/base/BasePlayerActivity.kt → presenter/home/HomeViewModel.kt → service/MusicPlaybackController.kt → service/MusicService.kt)*
13. Bấm **Next** trên notification → MediaSession callback `onSkipToNext` → `service.next()` (đường đi khác nhưng cùng đích).
14. Bài hết → `setOnCompletionListener` → `currentIndex++` → `playCurrent()` (auto-advance, chạy cả khi app nền) → `onSongChanged` → mini player + notification đổi bài.

### Bước 6 — Bookmark playlist (Phần 3)
15. Bấm bookmark trên card → `PlaylistAdapter.onSaveClick` → `viewModel.onState(HomeState.ToggleSavePlaylist(playlist))` → `repository.toggleSavePlaylist()` → `PlaylistDao` (Room) → Room Flow emit → section "Playlist đã lưu" + icon đầy. *(adapter/PlaylistAdapter.kt → presenter/home/HomeViewModel.kt → data/repository/MusicRepositoryImpl.kt → data/local/dao/PlaylistDao.kt)*

**Biến đổi dữ liệu:** `PlaylistItem → PlaylistData.songs: List<SongItem> → queue + index → encodeId → GET stream → StreamData{128,320} → url mp3 → MediaPlayer → âm thanh`. Song song: `SongItem → RecentSongEntity (Room) → Flow → UI`.

---

## 4. Điểm cần lưu ý khi bảo vệ đồ án

### 4.1 Những đoạn code "dễ bị hỏi" + trả lời ngắn gọn

| Nếu giám khảo hỏi | Trả lời gọn |
|---|---|
| **Tại sao có base class `common/`?** | Gộp phần lặp lại (template vòng đời, loading, launch coroutine, onState) về 1 chỗ → mọi màn hình nhất quán, ít code trùng. Học từ project mẫu. |
| **Pattern `onState()` là gì?** | UI không gọi hàm ViewModel trực tiếp mà gửi 1 sealed-class State (`viewModel.onState(HomeState.Next)`); ViewModel dùng `when(state)` quyết định xử lý. Tách biệt "ý định" và "logic", dễ mở rộng (thêm State là thêm case). |
| **Vì sao `IViewModel` extends `AndroidViewModel`?** | Cần `Application` cho các tiện ích `toast()/string()` và để Koin bơm `get()` đầu tiên. |
| **Tại sao Repository tách interface (domain) + impl (data)?** | ViewModel/Service chỉ phụ thuộc interface → dễ test (fake repo), dễ đổi data source, Koin `singleOf(::Impl) bind Interface` đúng DI. |
| **Vì sao có `isPrepared`?** | MediaPlayer lỗi `-38` nếu `start()` khi đang buffer. `isPrepared` chỉ cho start sau `onPrepared`; bấm sớm thì bỏ qua, bài tự phát khi xong. |
| **Tại sao có `playbackGeneration`?** | Bấm liên tục 2 bài → player cũ vừa release vẫn gửi callback muộn → gọi start trên instance hỏng. Mỗi lần phát bài mới tăng biến; callback generation cũ bị bỏ qua. |
| **Tại sao có `isSwitchingSong`?** | Chặn bấm Next/Prev liên tục khi đang fetch URL → tránh 2 coroutine fetch song song làm lệch `currentIndex`. |
| **Vì sao Service tự gọi `MusicRepository`?** | Auto-advance chạy khi app ở nền — Activity có thể đã chết. Service tự fetch URL bài kế nên nhạc chạy liên tục. Koin `KoinComponent` + `by inject()`. |
| **Vì sao ViewModel không giữ Service mà giữ `PlaybackController`?** | ViewModel chỉ cần "biết cách next/prev/play/pause", không cần Service cụ thể. Interface → dễ test, dễ đổi engine; tránh phụ thuộc Android Service. |
| **Vì sao Foreground + Bound?** | Bound (Binder) để Activity gọi method trực tiếp; Foreground để Android 8+ không giết service khi ở background. |
| **Tại sao `prepareAsync()`?** | `prepare()` chặn main thread khi tải file lớn → ANR. `prepareAsync()` chạy nền, xong gọi `onPrepared`. |
| **Vì sao dùng Room thay SharedPreferences?** | Room trả `Flow<>` (UI tự cập nhật khi bảng đổi — single source of truth), query SQL, chống trùng bằng PK, scale tốt hơn. |
| **Vì sao có `Migration` 1→2?** | Khi thêm bảng mới phải tăng `version` + viết Migration để **giữ dữ liệu cũ**. Nếu chỉ tăng version không Migration → Room crash (hoặc mất data nếu fallback destructive). |
| **Vì sao có `Mapper<Entity, Model>`?** | Entity là bảng SQLite (có field riêng như `playedAt/savedAt`), Model là DTO API. Mapper tách việc chuyển đổi ra khỏi entity → entity sạch, dễ test. |
| **Tại sao dùng `adb reverse` + 127.0.0.1 thay vì 10.0.2.2?** | Trên emulator này 10.0.2.2 không kết nối được từ app process. `adb reverse tcp:3000 tcp:3000` chuyển port device → host. |
| **Tại sao lọc "VIP" ở cả 2 nơi?** | ZingMP3 trả chữ `"VIP"` (không null) cho bài trả phí → nếu không lọc, MediaPlayer mở file tên "VIP" → crash. Lọc server + app cho chắc. |
| **Tại sao dùng `Result<T>` trong Repository?** | Bọc exception, ViewModel dùng `onSuccess/onFailure` rõ ràng, không try-catch rải rác. |
| **Tại sao StateFlow thay LiveData?** | Kotlin-native, luôn có giá trị khởi tạo, dùng chung hệ coroutine, dễ kết hợp operator. |
| **Tại sao `repeatOnLifecycle(STARTED)`?** | Chỉ collect khi Activity STARTED+, tự dừng khi background → tiết kiệm tài nguyên, tránh cập nhật UI khi không nhìn thấy. |
| **Tại sao `pendingPlay`?** | User có thể bấm bài trước khi Service bind xong → lưu lại, phát ngay khi `onServiceConnected`. |

### 4.2 Điểm yếu THẬT của code hiện tại (nói thật — tạo điểm cộng)

1. **`network/RetrofitClient.kt` là dead code** — sau refactor không file nào gọi `RetrofitClient.apiService` nữa (mọi thứ qua Koin). Nên xóa; hiện giữ lại như "bản so sánh". BASE_URL chỉ còn 1 chỗ trong `NetworkModule.kt`.
2. **BASE_URL hardcode `http://127.0.0.1:3000/`** — chỉ chạy được trên emulator có `adb reverse`; máy thật phải đổi URL.
3. **Chưa xử lý AudioFocus** — cuộc gọi đến / app khác phát nhạc thì app không tự pause.
4. **Chưa dùng ExoPlayer** — MediaPlayer đủ cho mp3 đơn giản nhưng không adaptive streaming, buffering control, gapless.
5. **Thể loại nhạc hardcode** trong `GenreItem.all`; bấm thể loại = search theo tên → kết quả phụ thuộc chất lượng search.
6. **Chưa có shuffle / repeat** — chỉ có next/prev theo thứ tự + auto-advance hết list là dừng (không lặp).
7. **Chưa có unit test / UI test** — nói thẳng "chưa viết test, đây là việc cải thiện tiếp".
8. **`playFromUrl()` trong `MusicService` không còn nơi nào gọi** (mọi màn hình đã dùng `playQueue`) — hàm chết còn để lại.
9. **Room version tăng tiếp phải viết Migration mới** — nếu quên sẽ crash khi nâng cấp trên máy có dữ liệu cũ.
10. **`onState` dùng một chiều** — chưa có xử lý "one-shot event" (như `Channel` trong mẫu) cho các thông báo 1 lần; hiện dùng StateFlow + `errorMessage`.

> Mẹo: nếu bị hỏi điểm yếu, đừng giấu — nói thẳng 2-3 điểm và đề xuất hướng sửa (xóa RetrofitClient, thêm AudioFocus, shuffle/repeat, test). Điều đó tạo điểm cộng "biết đánh giá code của mình".

---

## 5. Từ khóa cần nhớ khi bảo vệ (glossary nhanh)

- **MVVM**: Model – View – ViewModel. View hiển thị, ViewModel giữ state + logic, Model là data.
- **Clean Architecture rút gọn**: `common/` (base) · `domain/` (interface) · `data/` (impl) · `presenter/` (UI).
- **Base class / template method**: `IActivity`/`IViewModel` — lớp cha định sẵn khung, lớp con chỉ điền phần riêng.
- **Pattern `onState()` (Command)**: UI gửi sealed-class State; ViewModel `when(state)` xử lý.
- **Sealed class `*State`**: khai báo cuối file ViewModel, implement `IViewModel.IState`.
- **`singleOf(::Impl) bind Interface::class`**: Koin — đăng ký impl nhưng inject theo interface.
- **StateFlow**: luồng state luôn có giá trị hiện tại; `asStateFlow()` không cho bên ngoài ghi.
- **viewModelScope**: CoroutineScope tự hủy khi ViewModel bị destroy.
- **repeatOnLifecycle(STARTED)**: collect an toàn theo vòng đời.
- **ListAdapter + DiffUtil**: so list cũ/mới → chỉ render phần thay đổi.
- **ViewBinding**: `ActivityXBinding.inflate(...)` — type-safe, thay `findViewById`.
- **Retrofit**: interface Kotlin → HTTP client (Proxy pattern); `suspend fun` chạy nền.
- **Repository Pattern**: interface (domain) + impl (data); ViewModel không biết data từ đâu.
- **Room**: ORM trên SQLite; `@Entity` (bảng), `@Dao` (truy vấn), `@Database` (khai báo), `Flow` query (tự cập nhật), `Migration` (nâng cấp giữ data).
- **Mapper<Entity, Model>**: chuyển Entity ↔ Model.
- **Result<T>**: bọc giá trị hoặc exception.
- **Koin**: DI container; `module{}` khai báo công thức; `single{}` (1 instance), `viewModel{}` (gắn vòng đời Activity), `get()` (lấy dependency), `by inject()`/`by viewModel()` (bơm vào), `KoinComponent` (cho class không phải Activity như Service).
- **DI / Inversion of Control**: class khai báo "tôi cần gì", không tự tạo; Koin đưa vào.
- **Bound Service + Binder**: Activity lấy reference service để gọi method.
- **Foreground Service + startForeground**: chạy nền lâu dài với notification, không bị giết.
- **START_NOT_STICKY**: service bị kill không tự restart.
- **MediaPlayer**: `prepareAsync`/`onPrepared`, `setOnCompletionListener`, `setOnErrorListener`, `release()`.
- **MediaSessionCompat**: cầu nối giữa app và hệ thống (lock screen, tai nghe, notification).
- **MediaStyle notification**: notification media có nút Prev/PlayPause/Next.
- **PlaybackController (interface)**: ViewModel điều khiển nhạc mà không phụ thuộc Service.
- **PendingIntent**: intent "đóng gói" gửi sau (notification button → service).
- **KSP**: chạy Room compiler lúc build (sinh code `@Dao`/`@Database`).
- **adb reverse**: chuyển port thiết bị → host.
- **Cleartext HTTP**: Android chặn HTTP từ API 28; bật `usesCleartextTraffic`.

---

*File này được viết lại từ việc đọc toàn bộ code thật trong project (2026-08-19, sau refactor Phần 3 theo project MẪU DIYWallpaper_Kotlin). Nếu sửa code, hãy cập nhật lại file này cho khớp.*
