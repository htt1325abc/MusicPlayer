# 📘 TÀI LIỆU HỌC TẬP — App Music Player Android (Kotlin)

> Tài liệu được viết **theo code thật của project này** để bạn học đúng chuẩn cấu trúc:
> **PHẦN 1** = Tổng quan & cách build · **PHẦN 2** = Luồng dữ liệu · **PHẦN 3** = Kiến thức nền (chuyên sâu Service + Retrofit/OkHttp) · **PHẦN 4** = Checklist tự kiểm tra (có đáp án).

---

# PHẦN 1 — TỔNG QUAN & CÁCH BUILD

## 1.1. Project gồm 2 phần

| Phần | Công nghệ | Vai trò |
|------|-----------|---------|
| **A. Server** | Node.js + Express + `zingmp3-api-full` | Proxy gọi ZingMP3, trả JSON gọn cho Android |
| **B. App** | Kotlin + MVVM + Retrofit/OkHttp + MediaPlayer | Tìm kiếm & phát nhạc |

## 1.2. Server — 4 REST endpoints (đã hoàn thiện)

| Method | Endpoint | Mô tả |
|--------|----------|-------|
| GET | `/api/search?q=keyword` | Tìm bài hát theo tên |
| GET | `/api/song/:id/stream` | Lấy link mp3 để phát |
| GET | `/api/chart` | Top nhạc thịnh hành |
| GET | `/api/playlist/:id` | Chi tiết playlist/album |

## 1.3. Cách chạy (đã kiểm chứng trên máy)

### a) Server
```bash
cd server
npm install
npm run dev        # chạy tại http://localhost:3000
```

### b) Build & cài app lên emulator
```powershell
# Từ thư mục gốc project
.\gradlew.bat :app:installDebug --console=plain

# Mở app
& "C:\Users\LENOVO\AppData\Local\Android\Sdk\platform-tools\adb.exe" shell am start -n com.example.musicplayer/.MainActivity
```

> Kết quả thực tế: `BUILD SUCCESSFUL` (~1 phút lần đầu, ~4–7s các lần sau).

### c) Chạy emulator không lỗi (GPU + âm thanh)

```powershell
$sdk = "C:\Users\LENOVO\AppData\Local\Android\Sdk"
Start-Process -FilePath "$sdk\emulator\emulator.exe" `
  -ArgumentList @("-avd","Pixel_7_Pro","-no-snapshot-load","-netdelay","none","-netspeed","full")

# SAU MỖI LẦN BOOT — bắt buộc chạy lại để kết nối server:
& "$sdk\platform-tools\adb.exe" reverse tcp:3000 tcp:3000
```

| Flag | Vì sao? |
|------|---------|
| `-no-snapshot-load` | Boot sạch → **audio khởi tạo đúng** (fix lỗi không có tiếng do snapshot cũ) |
| `Start-Process` | Emulator chạy độc lập, không bị chết khi terminal bị đóng |
| `adb reverse` | Chuyển tiếp `127.0.0.1:3000` của emulator → server trên máy |

**Muốn chạy bằng GPU thật (nhanh):** máy có RTX 3050 nhưng driver cũ (< 553.35) nên emulator tự rơi về software rendering. Cập nhật driver NVIDIA lên **≥ 553.35** là triệt để; hoặc ép bằng flag:
```powershell
Start-Process "$sdk\emulator\emulator.exe" -ArgumentList @("-avd","Pixel_7_Pro","-gpu","host","-no-snapshot-load")
```

---

# PHẦN 2 — LUỒNG HOẠT ĐỘNG (DATA FLOW)

## 2.1. Sơ đồ tổng quan

```
┌─────────────┐     HTTP GET      ┌──────────────────┐     HTTP GET      ┌───────────┐
│   Android   │ ────────────────▶ │  Server Node.js   │ ───────────────▶ │  ZingMP3   │
│  (Retrofit) │ ◀──────────────── │  (Express proxy)   │ ◀─────────────── │   .vn      │
└─────────────┘    JSON response  └──────────────────┘     JSON/HTML     └───────────┘
       │
       │ dùng URL mp3 nhận được
       ▼
┌─────────────┐
│ MusicService │  → MediaPlayer.setDataSource(url) → phát nhạc
└─────────────┘
```

## 2.2. Luồng chi tiết 15 bước

| Bước | Thành phần | Việc xảy ra |
|------|------------|-------------|
| 1 | UI (EditText) | User gõ từ khóa "Sơn Tùng" |
| 2 | Activity | `TextWatcher` → gọi `viewModel.search(keyword)` |
| 3 | ViewModel | `viewModelScope.launch { }` → `delay(500)` (debounce) → `repository.searchSongs(keyword)` |
| 4 | Repository | Gọi `api.searchSongs(keyword)` — **suspend fun**, không chặn UI |
| 5 | Retrofit + OkHttp | Build GET request → gửi tới server |
| 6 | Server Node.js | Route `/api/search` → `ZingMp3.search(keyword)` |
| 7 | ZingMP3.vn | Trả JSON (đã giải mã/ký số phía server) |
| 8 | Server | Trả JSON gọn lại cho Android |
| 9 | Retrofit + Gson | Parse JSON → `List<SongItem>` |
| 10 | ViewModel | Cập nhật `StateFlow` danh sách bài |
| 11 | UI | `RecyclerView.submitList()` render lại (Observer pattern) |
| 12 | User bấm 1 bài | Activity → `viewModel.playSong(song)` → lấy URL stream |
| 13 | Activity | Observe `streamUrl` → `musicService.playFromUrl(url, ...)` qua Binder |
| 14 | MusicService | `mediaPlayer.setDataSource(url)` → `prepareAsync()` → phát khi sẵn sàng |
| 15 | Foreground Service | Hiện Notification, giữ Service không bị kill |

## 2.3. Vì sao phải gọi 2 API riêng (search → stream)?

- **Search** chỉ cần thông tin cơ bản (tên, ca sĩ, ảnh, ID) → nhẹ, nhanh, hiển thị ngay.
- **Stream URL** của ZingMP3 có **thời hạn ngắn** (vài giờ) và tốn thời gian tạo chữ ký `sig` → lấy sẵn cho cả danh sách là chậm + lãng phí.
- → Chỉ gọi `/stream` **đúng lúc user bấm phát bài đó**.

### Vì sao link stream KHÔNG nên lưu database lâu dài?
1. Link kèm `sig`/`token`/`ctime` → **hết hạn sau vài giờ** → trả 404/403 hoặc file rỗng.
2. ZingMP3 có thể **đổi CDN / thu hồi** link bất kỳ lúc nào.
3. Đúng cách: DB chỉ lưu `encodeId` (bền). Khi phát: `encodeId` → gọi `/stream` → link mới.

---

# PHẦN 3 — KIẾN THỨC NỀN (CHUYÊN SÂU)

## 3.1. Kotlin Coroutines — `suspend fun` là gì, vì sao cần?

Gọi mạng là tác vụ **chậm** (vài trăm ms → vài giây). Nếu gọi trên main thread → app đơ (ANR).

```kotlin
// BÌNH THƯỜNG — chặn main thread, app đơ:
fun search(query: String): List<SongItem> {
    return api.searchSongs(query)   // ❌ chặn UI
}

// SUSPEND — tạm dừng mà KHÔNG chặn thread:
suspend fun searchSongs(@Query("q") query: String): ApiResponse<List<SongItem>>
// ✅ Retrofit hiểu suspend fun và tự chạy trên IO thread, main thread tự do
```

**Cách hoạt động:** `suspend fun` có thể "tạm dừng" (suspend) rồi "tiếp tục" (resume) sau khi có kết quả — không cần callback lồng nhau (tránh *callback hell*).

**`viewModelScope.launch { }`** khởi chạy coroutine và **tự hủy khi ViewModel bị destroy** → tránh rò rỉ bộ nhớ.

**Debounce** trong code thật:
```kotlin
fun search(query: String) {
    searchJob?.cancel()          // hủy request cũ nếu user còn gõ
    if (query.isBlank()) return
    searchJob = viewModelScope.launch {
        delay(500)               // chờ 500ms — bị cancel thì dừng luôn
        _isLoading.value = true
        val result = repository.searchSongs(query)
        result.onSuccess { _songs.value = it }
             .onFailure { _errorMessage.value = it.message }
        _isLoading.value = false
    }
}
```

## 3.2. MVVM (Model - View - ViewModel)

- **Model**: data class thô — `SongItem`, `ApiResponse<T>`, `StreamData` (trong `model/`)
- **View**: `MainActivity` — chỉ hiển thị UI + bắt sự kiện, **không chứa logic**
- **ViewModel**: `MusicViewModel` — giữ state, sống sót qua xoay màn hình
- **Repository**: `MusicRepository` — tách nguồn dữ liệu (network) khỏi ViewModel

**Tại sao cần Repository?** Nếu ViewModel gọi thẳng API:
- ViewModel dính logic network → khó test (phải mock mạng)
- Khó đổi nguồn dữ liệu (thêm cache, đổi server) → phải sửa ViewModel
- → Repository đóng gói, trả `Result<T>` gọn gàng

## 3.3. Retrofit + OkHttp — CÁCH HOẠT ĐỘNG (CHUYÊN SÂU)

### 3.3.1. Hai tầng khác nhau
- **OkHttp** = tầng **thực thi HTTP** (mở kết nối, gửi/nhận byte, connection pool, retry).
- **Retrofit** = tầng **trừu tượng hóa**: biến interface Kotlin thành HTTP request + tự parse JSON (nhờ converter).

### 3.3.2. Phân tích `RetrofitClient.kt` (code thật)

```kotlin
object RetrofitClient {   // Singleton — 1 instance dùng chung toàn app

    private const val BASE_URL = "http://127.0.0.1:3000/"  // đổi sau khi deploy Render

    private val okHttpClient: OkHttpClient by lazy {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY   // log cả request+response → dễ debug JSON
        }
        OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)   // timeout dài vì Render free tier cold start chậm
            .build()
    }

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())  // JSON ↔ Kotlin data class
            .build()
    }

    val apiService: MusicApiService by lazy {
        retrofit.create(MusicApiService::class.java)  // Retrofit tự sinh code implement interface
    }
}
```

### 3.3.3. `Interceptor` — "chen ngang" mỗi request
`HttpLoggingInterceptor` implement `Interceptor`. Mỗi request đi qua chuỗi interceptor:
```
Request → [Interceptor 1: log] → [Interceptor 2: ...] → OkHttp thực thi → Response
```
→ Dùng để log, gắn header (token), retry, cache...

### 3.3.4. Các annotation Retrofit trong `MusicApiService.kt`

```kotlin
interface MusicApiService {
    @GET("api/search")
    suspend fun searchSongs(@Query("q") query: String): ApiResponse<List<SongItem>>

    @GET("api/song/{id}/stream")
    suspend fun getStreamUrl(@Path("id") songId: String): ApiResponse<StreamData>

    @GET("api/chart")
    suspend fun getChart(): ApiResponse<List<ChartData>>

    @GET("api/playlist/{id}")
    suspend fun getPlaylistDetail(@Path("id") playlistId: String): ApiResponse<PlaylistData>
}
```

| Annotation | Ý nghĩa |
|------------|---------|
| `@GET("path")` | HTTP method + path (nối vào `baseUrl`) |
| `@Query("q")` | Thêm query param → `?q=...` |
| `@Path("id")` | Thay `{id}` trong path bằng giá trị |

### 3.3.5. Repository xử lý lỗi bằng `Result<T>` (code thật)

```kotlin
class MusicRepository {
    private val apiService = RetrofitClient.apiService

    suspend fun searchSongs(query: String): Result<List<SongItem>> {
        return try {
            val response = apiService.searchSongs(query)
            if (response.success && response.data != null) {
                Result.success(response.data)
            } else {
                Result.failure(Exception(response.error ?: "Không tìm thấy kết quả"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Lỗi mạng: ${e.localizedMessage}"))  // timeout, mất mạng, server down
        }
    }
}
```

> `Result<T>` (Kotlin built-in) bọc **giá trị** hoặc **exception** → ViewModel chỉ cần `onSuccess`/`onFailure`, lỗi tập trung ở Repository.

## 3.4. Service Android — LÝ THUYẾT & CODE THẬT (CHUYÊN SÂU)

### 3.4.1. 3 loại Service (nhớ kỹ)

| Loại | Bắt đầu bởi | Dùng khi | Sống đến khi |
|------|-------------|----------|--------------|
| **Started** | `startService()` / `startForegroundService()` | Xử lý 1 việc nền (tải file) | `stopSelf()` / `stopService()` |
| **Bound** | `bindService()` | Giao tiếp 2 chiều với Activity | Không còn client nào bind |
| **Foreground** | `startForegroundService()` + `startForeground()` | Việc cần chạy lâu, hiện notification | User/kill rõ ràng |

> App này dùng **Bound + Foreground kết hợp**: Bound để Activity điều khiển, Foreground để chạy nền không bị kill.

### 3.4.2. Vòng đời & thứ tự gọi (quan trọng khi debug)

**Khi Activity `bindService()`:**
```
Activity.onCreate()
  └─ bindService(intent, serviceConnection, BIND_AUTO_CREATE)
       ├─ Service.onCreate()               ← breakpoint 1
       └─ Service.onBind() → trả IBinder   ← breakpoint 2
            └─ Activity.onServiceConnected() ← breakpoint 3 (có binder, gọi play được)
```

**Lưu ý thực tế:** `onServiceConnected()` có thể xảy ra **sau** user đã bấm bài hát → phải dùng *pending playback* (xem 3.5).

### 3.4.3. Phân tích `MusicService.kt` (code thật)

```kotlin
class MusicService : Service() {

    private val binder = MusicBinder()          // Binder để Activity lấy reference

    inner class MusicBinder : Binder() {
        fun getService(): MusicService = this@MusicService
    }

    override fun onBind(intent: Intent?): IBinder = binder   // trả Binder cho Activity

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()             // channel bắt buộc từ Android 8+
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY_PAUSE -> togglePlayPause()   // nút trên notification
            ACTION_STOP -> { stopPlayback(); stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() }
        }
        return START_NOT_STICKY  // không tự restart khi bị kill (đồ án học tập)
    }

    fun playFromUrl(url: String, title: String, artist: String) {
        mediaPlayer?.release()
        mediaPlayer = null
        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(url)                       // ⚠️ có thể ném exception → bọc try-catch
                setOnPreparedListener {
                    start()
                    startForeground(NOTIFICATION_ID, buildNotification(true))  // chuyển Foreground
                }
                setOnCompletionListener { onPlaybackStateChanged?.invoke(false); updateNotification(false) }
                setOnErrorListener { _, what, extra ->
                    onError?.invoke("Lỗi phát nhạc (code: $what/$extra)")
                    true
                }
                prepareAsync()   // buffer nền, không block main thread
            }
        } catch (e: Exception) {
            onError?.invoke("Không thể phát: ${e.message}")
        }
    }

    fun togglePlayPause() {
        mediaPlayer?.let { p ->
            if (p.isPlaying) { p.pause(); onPlaybackStateChanged?.invoke(false) }
            else { p.start(); onPlaybackStateChanged?.invoke(true) }
        }
    }
}
```

### 3.4.4. Vì sao `prepareAsync()` thay vì `prepare()`?
- `prepare()` **block main thread** → app đơ khi buffer nhạc từ internet.
- `prepareAsync()` chạy nền, gọi `setOnPreparedListener` khi sẵn sàng → mượt.

## 3.5. Binder — giao tiếp Activity ↔ Service

Bound Service trả `IBinder` qua `onBind()`. Activity `bindService()` → nhận Binder → **gọi trực tiếp hàm như gọi hàm bình thường** (không qua Intent, không qua mạng).

```kotlin
private val serviceConnection = object : ServiceConnection {
    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
        val binder = service as MusicService.MusicBinder
        musicService = binder.getService()        // có reference → gọi play/pause được
        isBound = true

        musicService?.onPlaybackStateChanged = { isPlaying -> /* cập nhật nút */ }
        musicService?.onError = { message -> /* Toast */ }

        // Service đã sẵn sàng → phát bài đang chờ (nếu user bấm sớm)
        pendingUrl?.let { url ->
            playOnService(url, pendingSong)
            pendingUrl = null; pendingSong = null
        }
    }
    override fun onServiceDisconnected(name: ComponentName?) {
        musicService = null; isBound = false
    }
}
```

**Vì sao cần pending playback?** User có thể bấm bài hát **trước khi** `onServiceConnected()` chạy (musicService = null). Nếu không lưu lại, URL stream sẽ mất → không phát được. Code thật trong `observeViewModel()`:

```kotlin
viewModel.streamUrl.collect { url ->
    if (url != null) {
        if (musicService != null) playOnService(url, song)   // đã có service → phát luôn
        else { pendingUrl = url; pendingSong = song }         // chưa có → lưu chờ
        viewModel.onStreamUrlConsumed()                       // đánh dấu đã xử lý
    }
}
```

## 3.6. Reactive UI với StateFlow (thay LiveData)

- ViewModel **chủ động thông báo** khi có thay đổi (Observer pattern) — Activity không cần hỏi.
- `RecyclerView.Adapter` chỉ cần `submitList()` — không tự viết logic so sánh.

```kotlin
private val _songs = MutableStateFlow<List<SongItem>>(emptyList())
val songs: StateFlow<List<SongItem>> = _songs.asStateFlow()
```

**Tại sao StateFlow hơn LiveData?**
- Kotlin-native, hoạt động tốt với Coroutines (kết hợp `flow`, `map`, `combine`).
- Luôn có giá trị khởi tạo (không null) → an toàn hơn.

**Tại sao `repeatOnLifecycle(STARTED)`?**
- Chỉ collect khi Activity ở STARTED trở lên; tự dừng khi về background → tiết kiệm tài nguyên.

```kotlin
lifecycleScope.launch {
    repeatOnLifecycle(Lifecycle.State.STARTED) {
        viewModel.songs.collect { songs -> songAdapter.submitList(songs) }
    }
}
```

## 3.7. Vì sao cần Server Node.js trung gian (thay vì gọi thẳng ZingMP3 từ Android)?

- Thư viện `zingmp3-api-full` **chỉ chạy trên Node.js/JS**, không có bản Kotlin.
- Cơ chế tạo chữ ký `sig` phức tạp **đã viết sẵn** trong thư viện → viết lại bằng Kotlin tốn công vô ích.
- → Giữ phần "khó" ở server, Android chỉ gọi REST JSON đơn giản.

---

# PHẦN 4 — CHECKLIST TỰ KIỂM TRA (CÓ ĐÁP ÁN)

Dùng để tự đánh giá bạn **hiểu** hay chỉ **copy chạy được**:

1. **Vì sao `search()` là `suspend fun` mà không phải hàm thường?**
   → Gọi mạng chậm; nếu chạy trên main thread → ANR. `suspend fun` cho phép "tạm dừng" không chặn thread, Retrofit tự chạy trên IO thread.

2. **Vì sao cần 2 lần gọi API (search → stream) thay vì gộp 1 lần?**
   → Stream URL có hạn + tốn thời gian tạo `sig`; chỉ cần gọi khi user thực sự bấm phát → nhanh, tiết kiệm bandwidth.

3. **Vẽ lại được sơ đồ data flow từ trí nhớ?**
   → Android → Server Node.js → ZingMP3 → JSON → RecyclerView → bấm bài → stream URL → MediaPlayer.

4. **Vai trò của Repository — bỏ Repository, gọi thẳng API trong ViewModel thì sao?**
   → ViewModel dính logic network → khó test, khó đổi nguồn dữ liệu (thêm cache, đổi server), lỗi rải rác. Repository đóng gói gọi API + `Result<T>`.

5. **Thứ tự gọi khi debug `onBind()`, `onStartCommand()`, `onServiceConnected()`?**
   → `bindService()` → `Service.onCreate()` → `onBind()` → (Activity) `onServiceConnected()`. `onStartCommand()` chỉ chạy khi `startService()`/`startForegroundService()` (không phải khi bind).

6. **Vì sao link stream không lưu DB lâu dài?**
   → Link có thời hạn (sig/ctime) + có thể bị thu hồi/đổi CDN → chỉ lưu `encodeId`, gọi `/stream` khi phát.

7. **Chạy emulator bằng GPU & không lỗi?**
   → `Start-Process emulator -avd Pixel_7_Pro -no-snapshot-load` (+ cập nhật driver NVIDIA ≥ 553.35 để dùng GPU thật; `adb reverse tcp:3000` sau mỗi boot).

8. **Lệnh build?**
   → `.\gradlew.bat :app:installDebug --console=plain` rồi `adb shell am start -n com.example.musicplayer/.MainActivity`.

---

## 🔗 Liên kết file code thật (để đối chiếu)
- `app/.../network/RetrofitClient.kt` — cấu hình Retrofit/OkHttp
- `app/.../network/MusicApiService.kt` — khai báo API
- `app/.../repository/MusicRepository.kt` — Repository + Result
- `app/.../viewmodel/MusicViewModel.kt` — StateFlow + debounce
- `app/.../service/MusicService.kt` — Foreground + Bound + MediaPlayer
- `app/.../MainActivity.kt` — bindService, observe, mini player
- `server/index.js` — 4 endpoints proxy ZingMP3
