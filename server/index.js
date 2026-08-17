/**
 * Server Node.js proxy cho ZingMP3 API
 *
 * TẠI SAO cần server proxy?
 * - ZingMP3 API không public, cần tính toán signature (hash) phía server
 * - Package "zingmp3-api-full" đã xử lý việc ký request, chỉ chạy được trên Node.js
 * - Android app không gọi trực tiếp ZingMP3 được → cần server trung gian
 * - Giúp ẩn logic xác thực, chỉ expose REST API đơn giản cho mobile
 */

const express = require("express");
const cors = require("cors");
const { ZingMp3 } = require("zingmp3-api-full");

const app = express();

// Bật CORS cho mọi origin → Android app từ bất kỳ IP nào đều gọi được
// Trong production nên giới hạn origin, nhưng đây là đồ án học tập nên mở rộng
app.use(cors());

// Parse JSON body (nếu cần POST request sau này)
app.use(express.json());

// ============================================================
// ENDPOINT 1: Tìm kiếm bài hát
// GET /api/search?q=<keyword>
//
// TẠI SAO tách search riêng?
// → Search chỉ trả metadata (tên, ảnh, id), KHÔNG trả link mp3
// → Giúp load nhanh danh sách kết quả, chỉ lấy link mp3 khi user bấm phát
// ============================================================
app.get("/api/search", async (req, res, next) => {
  try {
    const { q } = req.query;
    if (!q) {
      return res.status(400).json({ error: "Thiếu query parameter 'q'" });
    }

    const data = await ZingMp3.search(q);

    // ZingMP3 trả về object lớn, ta chỉ lấy phần songs để giảm bandwidth
    // data.data.songs chứa danh sách bài hát match với keyword
    const songs = data?.data?.songs || [];

    res.json({
      success: true,
      data: songs.map((song) => ({
        encodeId: song.encodeId,       // ID dùng để gọi API stream
        title: song.title,             // Tên bài hát
        artistsNames: song.artistsNames, // Tên ca sĩ (đã join sẵn)
        thumbnail: song.thumbnail,     // URL ảnh nhỏ (94x94)
        thumbnailM: song.thumbnailM,   // URL ảnh trung (240x240)
        duration: song.duration,       // Thời lượng (giây)
      })),
    });
  } catch (err) {
    next(err);
  }
});

// ============================================================
// ENDPOINT 2: Lấy link stream mp3
// GET /api/song/:id/stream
//
// TẠI SAO tách stream riêng khỏi search?
// → Link mp3 có thời hạn (expire), nếu lấy hết trong search sẽ hết hạn
//   trước khi user kịp bấm nghe
// → Chỉ gọi khi user thực sự muốn phát → tiết kiệm request
// ============================================================
app.get("/api/song/:id/stream", async (req, res, next) => {
  try {
    const { id } = req.params;
    const data = await ZingMp3.getSong(id);

    if (data?.err !== 0) {
      return res.status(404).json({
        success: false,
        error: data?.msg || "Không tìm thấy bài hát hoặc bài hát VIP",
      });
    }

    // data.data chứa object { "128": "url", "320": "url" }
    // 128 = chất lượng thường (miễn phí), 320 = cao (VIP)
    // QUAN TRỌNG: bài VIP ZingMP3 trả CHUỖI "VIP" thay vì null
    // → phải lọc "VIP" thành null để app biết bài này không phát được miễn phí
    //   (nếu để nguyên, app sẽ cố mở file tên "VIP" → crash FileNotFoundException)
    const quality128 = data?.data?.["128"];
    const quality320 = data?.data?.["320"];

    res.json({
      success: true,
      data: {
        "128": quality128 && quality128 !== "VIP" ? quality128 : null,
        "320": quality320 && quality320 !== "VIP" ? quality320 : null,
      },
    });
  } catch (err) {
    next(err);
  }
});

// ============================================================
// ENDPOINT 3: Lấy bảng xếp hạng
// GET /api/chart
// ============================================================
app.get("/api/chart", async (req, res, next) => {
  try {
    const data = await ZingMp3.getChartHome();

    // RTChart chứa danh sách bài hát trending
    const items = data?.data?.RTChart?.items || [];

    res.json({
      success: true,
      data: items.map((song) => ({
        encodeId: song.encodeId,
        title: song.title,
        artistsNames: song.artistsNames,
        thumbnail: song.thumbnail,
        thumbnailM: song.thumbnailM,
        duration: song.duration,
        rakingStatus: song.rakingStatus, // Thứ hạng tăng/giảm
      })),
    });
  } catch (err) {
    next(err);
  }
});

// ============================================================
// ENDPOINT 4: Chi tiết playlist/album
// GET /api/playlist/:id
// ============================================================
app.get("/api/playlist/:id", async (req, res, next) => {
  try {
    const { id } = req.params;
    const data = await ZingMp3.getDetailPlaylist(id);

    if (data?.err !== 0) {
      return res.status(404).json({
        success: false,
        error: "Không tìm thấy playlist",
      });
    }

    const playlist = data?.data;

    res.json({
      success: true,
      data: {
        encodeId: playlist?.encodeId,
        title: playlist?.title,
        thumbnail: playlist?.thumbnail,
        thumbnailM: playlist?.thumbnailM,
        artistsNames: playlist?.artistsNames,
        songs: (playlist?.song?.items || []).map((song) => ({
          encodeId: song.encodeId,
          title: song.title,
          artistsNames: song.artistsNames,
          thumbnail: song.thumbnail,
          thumbnailM: song.thumbnailM,
          duration: song.duration,
        })),
      },
    });
  } catch (err) {
    next(err);
  }
});

// ============================================================
// Middleware xử lý lỗi chung
// Mọi lỗi throw ra đều được bắt tại đây → trả JSON thay vì crash server
// ============================================================
app.use((err, req, res, _next) => {
  console.error("Server error:", err.message);
  res.status(500).json({
    success: false,
    error: "Lỗi server: " + err.message,
  });
});

// ============================================================
// Start server
// Render.com sẽ inject biến PORT, local thì dùng 3000
// ============================================================
const PORT = process.env.PORT || 3000;
app.listen(PORT, () => {
  console.log(`🎵 MusicPlayer Server đang chạy tại http://localhost:${PORT}`);
  console.log(`📡 Endpoints:`);
  console.log(`   GET /api/search?q=keyword`);
  console.log(`   GET /api/song/:id/stream`);
  console.log(`   GET /api/chart`);
  console.log(`   GET /api/playlist/:id`);
});
