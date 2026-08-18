# 🎵 Hướng dẫn mở emulator, build & chạy MusicPlayer

> Hướng dẫn từng bước để chạy app **MusicPlayer** trên Android Emulator.
> Áp dụng cho máy: Windows 10/11, Android SDK tại `C:\Users\LENOVO\AppData\Local\Android\Sdk`.

---

## 1. Chuẩn bị (một lần duy nhất)

### 1.1. Kiểm tra môi trường
- **Android SDK**: đã cài tại `C:\Users\LENOVO\AppData\Local\Android\Sdk`
- **Emulator (AVD)**: `Pixel_7_Pro` (API 37) — app dùng AVD này
- **Node.js**: để chạy server nhạc (kiểm tra bằng lệnh `node -v`)
- **JDK**: cài kèm Android Studio

> ⚠️ Lưu ý: Lệnh `adb` **không nằm trong PATH**, phải gọi bằng đường dẫn đầy đủ hoặc khai báo biến:
> ```powershell
> $sdk = "C:\Users\LENOVO\AppData\Local\Android\Sdk"
> ```

### 1.2. Cấu hình âm thanh emulator (chỉ cần làm 1 lần)
Nếu **không nghe được tiếng nhạc** trên emulator, mở file:
```
C:\Users\LENOVO\.android\avd\Pixel_7_Pro.avd\config.ini
```
Thêm dòng sau (nếu chưa có):
```
hw.audioOutput=yes
```
Lưu file rồi **đóng hoàn toàn** emulator trước khi chạy lại.

---

## 2. Mở emulator

Chạy trong **PowerShell**:

```powershell
$sdk = "C:\Users\LENOVO\AppData\Local\Android\Sdk"
Start-Process -FilePath "$sdk\emulator\emulator.exe" `
  -ArgumentList '-avd','Pixel_7_Pro','-no-snapshot-load','-netdelay','none','-netspeed','full' `
  -WindowStyle Minimized
```

> Giải thích các flag:
> - `-no-snapshot-load`: boot sạch (tránh lỗi mất âm thanh khi restore snapshot)
> - `-netdelay none` / `-netspeed full`: mạng nhanh & ổn định
> - `Start-Process`: tách khỏi terminal → **không bị VS Code kill** khi đóng terminal
>
> ⚠️ Dùng software rendering (driver NVIDIA cũ) nên emulator chạy hơi chậm — bình thường.

**Chờ emulator boot xong:**

```powershell
$sdk = "C:\Users\LENOVO\AppData\Local\Android\Sdk"
& "$sdk\platform-tools\adb.exe" wait-for-device
& "$sdk\platform-tools\adb.exe" shell 'while [[ -z $(getprop sys.boot_completed) ]]; do sleep 1; done; echo BOOT_COMPLETED'
```

Khi thấy `BOOT_COMPLETED` là emulator đã sẵn sàng.

---

## 3. Build app

Mở terminal tại thư mục gốc dự án:
```powershell
cd C:\Users\LENOVO\AndroidStudioProjects\MusicPlayer
```

Build APK debug:
```powershell
.\gradlew.bat :app:assembleDebug --console=plain
```

Nếu muốn **build + cài luôn lên emulator**:
```powershell
.\gradlew.bat :app:installDebug --console=plain
```

> ✅ Kết quả mong đợi: `BUILD SUCCESSFUL`

---

## 4. Chạy server nhạc (BẮT BUỘC)

App lấy dữ liệu & stream nhạc từ server local qua port **3000**.
Mở terminal riêng rồi chạy:

```powershell
cd C:\Users\LENOVO\AndroidStudioProjects\MusicPlayer\server
npm run dev
```

Khi thấy dòng:
```
🎵 MusicPlayer Server đang chạy tại http://localhost:3000
```
là server đã sẵn sàng.

> ⚠️ Server cần chạy **trong suốt quá trình dùng app**. Đừng đóng terminal này.

---

## 5. Kết nối adb reverse (BẮT BUỘC sau mỗi lần restart emulator)

App gọi server qua `127.0.0.1:3000` nhờ `adb reverse`:

```powershell
$sdk = "C:\Users\LENOVO\AppData\Local\Android\Sdk"
& "$sdk\platform-tools\adb.exe" reverse tcp:3000 tcp:3000
& "$sdk\platform-tools\adb.exe" reverse --list   # kiểm tra
```

Kết quả mong đợi: thấy dòng `host-15 tcp:3000 tcp:3000`.

> ⚠️ Mỗi lần **khởi động lại emulator**, phải chạy lại lệnh này.

---

## 6. Chạy app

```powershell
$sdk = "C:\Users\LENOVO\AppData\Local\Android\Sdk"
& "$sdk\platform-tools\adb.exe" shell am start -n com.example.musicplayer/.MainActivity
```

App sẽ mở trên emulator. Kiểm tra hoạt động:
```powershell
& "$sdk\platform-tools\adb.exe" shell dumpsys window | Select-String "mCurrentFocus"
```

Kết quả mong đợi (app đang ở foreground):
```
mCurrentFocus=Window{... com.example.musicplayer/com.example.musicplayer.MainActivity}
```

---

## 7. Tóm tắt quy trình chạy nhanh (mỗi lần dùng)

| Bước | Lệnh | Ghi chú |
|------|------|---------|
| 1 | Mở emulator (`Start-Process ... -no-snapshot-load`) | chờ `BOOT_COMPLETED` |
| 2 | `npm run dev` (trong `server/`) | giữ terminal mở |
| 3 | `adb reverse tcp:3000 tcp:3000` | bắt buộc sau restart emulator |
| 4 | `.\gradlew.bat :app:installDebug` | build + cài |
| 5 | `adb shell am start -n com.example.musicplayer/.MainActivity` | chạy app |

---

## 8. Các lỗi thường gặp & cách xử lý

### ❌ Lỗi: `error: no devices/emulators found` hoặc `adb: device offline`
- **Nguyên nhân**: emulator chưa mở hoặc chưa boot xong.
- **Fix**: chạy lại Bước 2 và đợi `BOOT_COMPLETED`.

### ❌ Lỗi: `CLEARTEXT communication not permitted`
- **Nguyên nhân**: Android 9+ chặn HTTP thường.
- **Fix**: đảm bảo trong `app/src/main/AndroidManifest.xml` có:
  ```xml
  <application android:usesCleartextTraffic="true" ...>
  ```

### ❌ Lỗi: `Failed to connect to /10.0.2.2:3000`
- **Nguyên nhân**: `10.0.2.2` (NAT) không hoạt động với app process trên máy này.
- **Fix**: dùng `adb reverse` (Bước 5) và đảm bảo `RetrofitClient.kt` dùng `http://127.0.0.1:3000/`.

### ❌ Lỗi: Không nghe thấy tiếng nhạc (MediaPlayer đang phát nhưng câm)
- **Nguyên nhân**: emulator restore snapshot → audio backend không khởi tạo đúng.
- **Fix**: thêm `hw.audioOutput=yes` vào `config.ini` + luôn boot với `-no-snapshot-load`.

### ❌ Lỗi: Bài hát VIP bị crash / lỗi `setDataSource("VIP")`
- **Nguyên nhân**: ZingMP3 trả chuỗi `"VIP"` cho bài trả phí.
- **Fix**: đã lọc sẵn ở cả server (`server/index.js`) và app (`StreamData.getBestStreamUrl`) — không cần xử lý thêm.

### ❌ Lỗi: Bấm play/pause lúc bài đang buffer → không phát được (log: `start called in state 4`, `error (-38,0)`)
- **Nguyên nhân**: gọi `MediaPlayer.start()` trong lúc bài chưa prepare xong (state PREPARING) → lỗi INVALID_OPERATION → MediaPlayer hỏng.
- **Fix**: **đã sửa trong `MusicService.kt`** (thêm cờ `isPrepared` — chỉ cho phép `start()` khi player sẵn sàng; bấm play/pause quá sớm sẽ bị bỏ qua và bài tự phát khi buffer xong). Đồng thời thêm bộ đếm `playbackGeneration` để bỏ qua callback từ player cũ khi bấm nhiều bài liên tiếp.

### ❌ Lỗi: `adb` không được nhận diện
- **Nguyên nhân**: adb không nằm trong PATH.
- **Fix**: dùng đường dẫn đầy đủ `C:\Users\LENOVO\AppData\Local\Android\Sdk\platform-tools\adb.exe` hoặc khai báo `$sdk` như trên.

---

## 9. Tắt / dọn dẹp

- Tắt server: vào terminal đang chạy `npm run dev` bấm `Ctrl+C`.
- Tắt emulator:
  ```powershell
  $sdk = "C:\Users\LENOVO\AppData\Local\Android\Sdk"
  & "$sdk\platform-tools\adb.exe" emu kill
  ```
