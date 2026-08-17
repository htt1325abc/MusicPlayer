# 🎵 MusicPlayer Server — Proxy ZingMP3 API

Server Node.js sử dụng Express + `zingmp3-api-full` để proxy dữ liệu nhạc Việt cho Android app.

## Cài đặt & Chạy local

```bash
# Di chuyển vào thư mục server
cd server

# Cài dependencies
npm install

# Chạy server (development - tự restart khi sửa code)
npm run dev

# Hoặc chạy production
npm start
```

Server sẽ chạy tại `http://localhost:3000`

## API Endpoints

| Method | Endpoint               | Mô tả                     |
|--------|------------------------|----------------------------|
| GET    | `/api/search?q=keyword`| Tìm kiếm bài hát          |
| GET    | `/api/song/:id/stream` | Lấy link mp3 để stream     |
| GET    | `/api/chart`           | Top nhạc thịnh hành        |
| GET    | `/api/playlist/:id`    | Chi tiết playlist/album    |

## Test bằng curl

> ⚠️ **Lưu ý cho Windows (PowerShell):**
> Trong PowerShell, `curl` là alias của `Invoke-WebRequest` nên cú pháp sẽ bị sai.
> Hãy dùng **`curl.exe`** thay cho `curl`:
> ```powershell
> curl.exe "http://localhost:3000/api/search?q=Son%20Tung"
> ```
> Nếu terminal hiện dấu tiếng Việt bị lỗi (ví dụ `Cá»§a`), đó chỉ là do bảng mã console
> Windows hiển thị sai — dữ liệu server trả về vẫn đúng UTF-8, Android đọc bình thường.
> Có thể gõ `chcp 65001` trước để console đọc UTF-8.

### 1. Tìm kiếm bài hát

```bash
curl "http://localhost:3000/api/search?q=Son%20Tung"
```

**Response mẫu:**
```json
{
  "success": true,
  "data": [
    {
      "encodeId": "ZOACFBBU",
      "title": "Chạy Ngay Đi",
      "artistsNames": "Sơn Tùng M-TP",
      "thumbnail": "https://photo-resize-zmp3.zmdcdn.me/...",
      "thumbnailM": "https://photo-resize-zmp3.zmdcdn.me/...",
      "duration": 262
    }
  ]
}
```

### 2. Lấy link stream

```bash
curl "http://localhost:3000/api/song/ZOACFBBU/stream"
```

**Response mẫu:**
```json
{
  "success": true,
  "data": {
    "128": "https://mp3-s1-zmp3.zmdcdn.me/...mp3",
    "320": null
  }
}
```

> **Lưu ý:** Bài VIP sẽ không có link 128/320 (trả null).

### 3. Lấy bảng xếp hạng

```bash
curl "http://localhost:3000/api/chart"
```

### 4. Chi tiết playlist

```bash
curl "http://localhost:3000/api/playlist/ZWZB969E"
```

## Deploy lên Render.com (Free Tier)

### Bước 1: Push code lên GitHub
```bash
# Tại thư mục gốc project
git init
git add server/
git commit -m "Add ZingMP3 proxy server"
git remote add origin https://github.com/<your-username>/musicplayer-server.git
git push -u origin main
```

> **Lưu ý:** Nên tạo repo riêng chỉ chứa thư mục `server/` hoặc đặt 
> Root Directory = `server` trên Render.

### Bước 2: Tạo Web Service trên Render

1. Đăng nhập [render.com](https://render.com) (dùng GitHub)
2. Click **"New +"** → **"Web Service"**
3. Kết nối GitHub repo vừa push
4. Cấu hình:
   - **Name:** `musicplayer-api` (hoặc tên tùy ý)
   - **Region:** Singapore (gần VN nhất)
   - **Branch:** `main`
   - **Root Directory:** `server` (nếu push cả project Android)
   - **Runtime:** `Node`
   - **Build Command:** `npm install`
   - **Start Command:** `npm start`
   - **Instance Type:** `Free`
5. Click **"Create Web Service"**

### Bước 3: Lấy URL

Sau khi deploy xong, Render cấp URL dạng:
```
https://musicplayer-api.onrender.com
```

Cập nhật URL này vào file `RetrofitClient.kt` trong Android app:
```kotlin
private const val BASE_URL = "https://musicplayer-api.onrender.com/"
```

### Bước 4: Test

```bash
curl "https://musicplayer-api.onrender.com/api/search?q=Son%20Tung"
```

> ⚠️ **Free tier sẽ sleep sau 15 phút không có request.**  
> Request đầu tiên sau khi sleep sẽ mất ~30-60s để khởi động lại.
