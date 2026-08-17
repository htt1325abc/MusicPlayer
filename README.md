# 🎵 Music Player — Đồ án Android (Kotlin + Node.js)

Ứng dụng nghe nhạc trực tuyến: **Android app (Kotlin, MVVM)** + **Server Node.js (Express)** proxy dữ liệu từ ZingMP3.

> Chi tiết server: xem [`server/README.md`](server/README.md)

---

## 📦 Cấu trúc project

```
MusicPlayer/
├── app/                        # Android app (Kotlin, MVVM)
│   └── src/main/java/com/example/musicplayer/
│       ├── MainActivity.kt     # Màn hình tìm kiếm + mini player
│       ├── adapter/SongAdapter.kt       # RecyclerView adapter
│       ├── model/              # SongItem, SearchResponse, StreamResponse, ChartResponse, PlaylistResponse
│       ├── network/            # RetrofitClient, MusicApiService
│       ├── repository/MusicRepository.kt
│       ├── service/MusicService.kt      # Foreground + Bound Service (MediaPlayer)
│       └── viewmodel/MusicViewModel.kt
└── server/                     # Server Node.js proxy ZingMP3
    ├── index.js                # 4 REST endpoints + CORS
    └── README.md               # Hướng dẫn chạy + deploy Render.com
```

---

## ⚙️ Cách chạy

### 1. Server (phải bật trước khi test app)

```bash
cd server
npm install
npm run dev        # chạy tại http://localhost:3000
```

### 2. Build & cài app lên emulator

```powershell
# Từ thư mục gốc project
.\gradlew.bat :app:installDebug --console=plain

# Mở app
& "C:\Users\LENOVO\AppData\Local\Android\Sdk\platform-tools\adb.exe" shell am start -n com.example.musicplayer/.MainActivity
```

> Kết quả đã xác nhận: `BUILD SUCCESSFUL` (~1 phút lần đầu, ~4–7s các lần sau), chạy ổn định trên AVD `Pixel_7_Pro` (API 37).

---

## 🖥️ Hướng dẫn chạy emulator (GPU & tránh lỗi)

### Lệnh khởi động chuẩn (đã kiểm chứng)

```powershell
$sdk = "C:\Users\LENOVO\AppData\Local\Android\Sdk"
Start-Process -FilePath "$sdk\emulator\emulator.exe" `
  -ArgumentList @("-avd","Pixel_7_Pro","-no-snapshot-load","-netdelay","none","-netspeed","full")
```

| Flag | Tác dụng |
|------|----------|
| `-no-snapshot-load` | Boot sạch, bỏ qua snapshot cũ → **audio được khởi tạo đúng** (fix lỗi không có tiếng) |
| `Start-Process` | Chạy emulator độc lập, không bị chết khi terminal bị đóng |
| `adb reverse` | Chuyển tiếp `127.0.0.1:3000` của emulator → server trên máy |

### Sau mỗi lần boot lại emulator — BẮT BUỘC chạy lại:

```powershell
& "$sdk\platform-tools\adb.exe" reverse tcp:3000 tcp:3000
```

### Chạy bằng GPU thật (nhanh hơn)

Máy có GPU **NVIDIA RTX 3050** nhưng **driver cũ (529.4 < 553.35)** → emulator tự rơi về software rendering (chậm). Cách xử lý:

- **Khuyên dùng:** cập nhật driver NVIDIA lên **≥ 553.35** → emulator tự nhận GPU, không cần flag.
- **Không đổi driver:** ép chế độ GPU bằng flag:

```powershell
Start-Process "$sdk\emulator\emulator.exe" `
  -ArgumentList @("-avd","Pixel_7_Pro","-gpu","host","-no-snapshot-load")
# hoặc
Start-Process "$sdk\emulator\emulator.exe" `
  -ArgumentList @("-avd","Pixel_7_Pro","-gpu","angle_indirect","-no-snapshot-load")
```

---

## 🔄 Luồng hoạt động (Data Flow)

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

| Bước | Thành phần | Việc xảy ra |
|------|------------|-------------|
| 1 | UI (EditText) | User gõ từ khóa, ví dụ "Sơn Tùng MTP" |
| 2 | Activity | Gọi `viewModel.search(keyword)` |
| 3 | ViewModel | Mở `viewModelScope.launch { }`, gọi `repository.searchSongs(keyword)` |
| 4 | Repository | Gọi `api.search(keyword)` — **suspend fun** nên không chặn UI thread |
| 5 | Retrofit + OkHttp | Build request, gửi GET tới server |
| 6 | Server Node.js | Nhận request tại `/api/search`, gọi `ZingMp3.search(keyword)` |
| 7 | ZingMP3.vn | Trả dữ liệu JSON (đã giải mã/ký số phía server) |
| 8 | Server | Trả JSON đã gọn lại cho Android |
| 9 | Retrofit + Gson | Parse JSON → `List<SongItem>` |
| 10 | ViewModel | Cập nhật StateFlow danh sách bài hát |
| 11 | UI | `RecyclerView` render lại (nhờ Observer pattern) |
| 12 | User bấm 1 bài | Lặp lại bước 3–9 nhưng gọi endpoint `/stream` để lấy URL mp3 thật |
| 13 | Activity | Gọi `musicService.playFromUrl(url, ...)` qua Binder |
| 14 | MusicService | `mediaPlayer.setDataSource(url)` → `prepareAsync()` → phát khi sẵn sàng |
| 15 | Foreground Service | Hiển thị Notification, giữ Service không bị hệ thống kill |

### Vì sao phải gọi 2 API riêng (search → stream)?

- Khi tìm kiếm chỉ cần **thông tin cơ bản** (tên bài, ca sĩ, ảnh, ID) → nhẹ, nhanh, hiển thị ngay.
- Link mp3 thật của ZingMP3 có **thời hạn ngắn** (vài giờ) và tốn thời gian tạo chữ ký `sig` — lấy sẵn cho cả danh sách sẽ rất chậm và lãng phí.
- → Chỉ gọi API lấy stream URL **đúng lúc user thực sự bấm phát**.

---

## 🧠 Vì sao link stream KHÔNG nên lưu database lâu dài?

1. **Link có thời hạn:** mỗi link mp3 kèm chữ ký `sig`/`token`/`ctime` — sau vài giờ hết hạn → trả 404/403 hoặc file rỗng.
2. **CDN có thể bị đổi/thu hồi:** ZingMP3 có thể đổi host hoặc chặn nếu phát hiện bị lạm dụng → link chết bất kỳ lúc nào.
3. **Lưu link = lưu thứ "chết yểu":** đúng cách là chỉ lưu `encodeId` (bền vĩnh viễn). Khi phát: lấy `encodeId` → gọi lại `/stream` → nhận link mới còn hạn.

```
DB/trạng thái app → lưu encodeId, title, artistsNames, thumbnail (dữ liệu bền)
Khi bấm phát      → gọi GET /api/song/:id/stream → URL mp3 mới, dùng ngay
Phát xong         → bỏ URL, chỉ giữ encodeId
```

---

## 📚 Kiến thức nền (tóm tắt)

### Kotlin Coroutines — `suspend fun`
- Gọi mạng là tác vụ tốn thời gian; gọi trực tiếp trên main thread → app đơ (ANR).
- `suspend fun` "tạm dừng" hàm mà **không chặn thread**, chờ kết quả rồi chạy tiếp — tránh callback lồng nhau.
- `viewModelScope.launch { }` khởi chạy coroutine, tự hủy khi ViewModel bị clear → tránh rò rỉ bộ nhớ.

### MVVM
- **Model**: dữ liệu thô (SongItem, ...) — **View**: Activity hiển thị — **ViewModel**: giữ trạng thái, sống sót qua xoay màn hình.
- **Repository**: tách nguồn dữ liệu khỏi ViewModel → dễ thay nguồn (thêm cache local) mà không sửa ViewModel.

### Retrofit + OkHttp
- OkHttp = tầng thực thi HTTP (mở kết nối, gửi/nhận byte).
- Retrofit = tầng trừu tượng: biến interface Kotlin thành HTTP request + tự parse JSON bằng Gson.
- Interceptor (như `HttpLoggingInterceptor`) chen vào trước/sau mỗi request → log, gắn header, retry...

### Vì sao cần server Node.js trung gian?
- Thư viện `zingmp3-api-full` chỉ chạy trên Node.js/JS, không có bản Kotlin.
- Cơ chế tạo chữ ký `sig` phức tạp đã được viết sẵn trong thư viện → không cần viết lại bằng Kotlin.
- → Giữ phần "khó" ở server, Android chỉ gọi REST JSON đơn giản.

### Binder (Activity ↔ Service)
- `MusicService` là Bound Service → trả `IBinder` qua `onBind()`.
- Activity `bindService()` → nhận Binder → gọi trực tiếp `playSong()` như hàm bình thường.
- → Phù hợp giao tiếp 2 chiều, tần suất cao (play, pause, seek...).

### Reactive UI (LiveData/StateFlow)
- ViewModel **chủ động thông báo** thay đổi (Observer pattern) thay vì Activity hỏi.
- Adapter chỉ cần `submitList()` khi dữ liệu đổi — không tự viết logic so sánh cũ/mới.

---

## ✅ Checklist tự kiểm tra (hiểu chứ không chỉ copy)

- [ ] Giải thích được vì sao `search()` là `suspend fun` → *vì gọi mạng chậm, cần tạm dừng không chặn main thread, tránh ANR.*
- [ ] Giải thích được vì sao cần 2 lần gọi API (search → stream) → *stream URL có hạn + tốn sig; chỉ lấy khi bấm phát.*
- [ ] Vẽ lại được sơ đồ data flow từ trí nhớ.
- [ ] Giải thích vai trò Repository → *tách nguồn dữ liệu; bỏ đi thì ViewModel dính logic network, khó test & khó đổi nguồn.*
- [ ] Đặt breakpoint tại `onBind()`, `onStartCommand()`, `onServiceConnected()` và giải thích thứ tự gọi khi debug.
- [ ] Giải thích được vì sao link stream không lưu DB lâu dài.
- [ ] Chạy được emulator bằng GPU và lệnh build (xem mục "Cách chạy" ở trên).
