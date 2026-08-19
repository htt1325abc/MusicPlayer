# ✅ PROJECT COMPLETION — MusicPlayer

> Tài liệu tổng kết sau khi hoàn thiện **Phần 1 (Service next/prev)**, **Phần 2 (Room database)**
> và **Phần 3 (Refactor kiến trúc theo project MẪU DIYWallpaper + Room playlist đã lưu)**.
> Ngày cập nhật: 2026-08-19. Build `:app:assembleDebug` — **THÀNH CÔNG** (KSP + Room hoạt động tốt).

---

## 1. Bảng tổng kết 6 tiêu chí

| # | Tiêu chí | Trạng thái | File/Class đảm nhiệm |
|---|----------|------------|----------------------|
| 1 | **Service music player** (Foreground, play/pause/next/prev, Notification media control) | ✅ Đã có (hoàn thiện) | `service/MusicService.kt` (MediaPlayer + Foreground + 4 nút MediaStyle) |
| 2 | **Dùng 1 API free** (network, không hardcode) | ✅ Đã có | `server/index.js` (proxy ZingMP3) + `network/MusicApiService.kt` |
| 3 | **Retrofit gọi API** | ✅ Đã có | `network/MusicApiService.kt`, `network/RetrofitClient.kt`, `di/NetworkModule.kt` |
| 4 | **Koin DI** | ✅ Đã có (đã align theo mẫu) | `App.kt` + `di/` (networkModule, repositoryModule, serviceModule, viewModelModule) |
| 5 | **Room database** | ✅ Đã có (thêm playlist đã lưu) | `data/local/` (3 Entity, 3 DAO, `MusicDatabase` v2, `Migrations`, `mapper/`) |
| 6 | **MVVM** (Model – View – ViewModel, StateFlow) | ✅ Đã có (+ base class chuẩn) | `common/` (IViewModel/IActivity/ResultFlow) + `presenter/` — StateFlow + `onState()` |
| 7 | **Clean Architecture theo mẫu** (base class, interface/impl repo) | ✅ MỚI (Phần 3) | `common/`, `domain/repository/`, `data/repository/`, `presenter/<feature>/` |

> **Kết luận:** 6 tiêu chí ban đầu đều đạt. Phần 3 bổ sung đúng convention project MẪU:
> base class (`IViewModel`/`IActivity`/`ResultFlow`), pattern `onState()`, Repository
> interface + impl, Room `mapper/`, và tính năng **"Playlist đã lưu"** (Room).

---

## 2. Các file mới tạo / đã sửa

### Phần 1 — Service music player: thêm next/prev + Notification media control

| Loại | File | Vai trò |
|------|------|---------|
| Sửa | `app/src/main/java/com/example/musicplayer/service/MusicService.kt` | Thêm 4 action `ACTION_PLAY/PAUSE/NEXT/PREV`, thêm method `play()/pause()/next()/previous()`, dispatch trong `onStartCommand`, notification dùng **MediaStyle** với 4 nút (Prev · Play/Pause · Next · Stop). `next()/previous()` chuyển bài dựa trên `queue` + `currentIndex` có sẵn, tự fetch URL bài mới kể cả khi app ở background. |
| Mới | `.../service/PlaybackController.kt` | **Interface** điều khiển playback (play/pause/togglePlayPause/next/previous/isPlaying). ViewModel chỉ phụ thuộc interface này, không phụ thuộc Service trực tiếp → dễ test, dễ đổi engine (ExoPlayer). |
| Mới | `.../service/MusicPlaybackController.kt` | **Implementation** (Koin `single`) giữ tham chiếu `service: MusicService?` (do Activity gán khi bind xong). Mọi method ủy quyền xuống Service; service null → no-op an toàn. |
| Mới | `.../di/ServiceModule.kt` | Koin module đăng ký `single { MusicPlaybackController() }`. |
| Sửa | `.../App.kt` | Đăng ký `serviceModule` vào `startKoin`. |
| Sửa | `.../ui/BasePlayerActivity.kt` | Inject `MusicPlaybackController`; khi `onServiceConnected` → `playbackController.service = musicService` (onDisconnected → null). `setupMiniPlayer()` wire nút **Prev/Next** vào hook `onNext()/onPrevious()`. `recentStore.add()` chuyển sang coroutine (Room suspend). |
| Sửa | `.../ui/HomeActivity.kt` | Override `onNext() = homeViewModel.next()`, `onPrevious() = homeViewModel.previous()`. |
| Sửa | `.../ui/SongListActivity.kt` | Override `onNext()/onPrevious()` gọi `playlistViewModel.next()/previous()`. |
| Sửa | `.../MainActivity.kt` | Chuyển sang Koin `by viewModel<MusicViewModel>()`, inject controller, wire nút Prev/PlayPause/Next qua `viewModel`. |
| Sửa | `.../viewmodel/HomeViewModel.kt`, `PlaylistViewModel.kt`, `MusicViewModel.kt` | Thêm `next()/previous()/togglePlayPause()` ủy quyền cho `PlaybackController`. |
| Sửa | `res/layout/view_mini_player.xml`, `activity_main.xml` | Thêm 2 nút `btnMiniPrev` + `btnMiniNext` trên mini player. |
| Mới | `res/drawable/ic_next.xml`, `ic_prev.xml` | Icon vector "Skip Next" / "Skip Previous". |

**Luồng bấm nút Next/Prev:** UI (nút bấm) → `Activity.onNext()` → `ViewModel.next()` → `PlaybackController.next()` → `MusicService.next()` → `MediaPlayer` phát bài mới. Với notification: nút → `PendingIntent` → `onStartCommand(ACTION_NEXT/PREV)` → `MusicService.next()/previous()`.

### Phần 2 — Room database (mới hoàn toàn)

| Loại | File | Vai trò |
|------|------|---------|
| Mới | `.../data/local/entity/RecentSongEntity.kt` | Bảng `recent_songs` (encodeId PK, title, artist, thumbnail, thumbnailM, duration, playedAt) + hàm map ↔ `SongItem`. |
| Mới | `.../data/local/entity/FavoriteSongEntity.kt` | Bảng `favorite_songs` (encodeId PK, …, addedAt) + hàm map ↔ `SongItem`. |
| Mới | `.../data/local/dao/RecentSongDao.kt` | `upsert` (REPLACE), `observeRecent(limit)` → **Flow**, `getById`, `deleteById`, `clear`. |
| Mới | `.../data/local/dao/FavoriteSongDao.kt` | `insert`, `observeAll()` → **Flow**, `getById`, `deleteById`, `clear`. |
| Mới | `.../data/local/MusicDatabase.kt` | `@Database` version 1, khai báo 2 Entity + 2 DAO. |
| Sửa | `.../di/RepositoryModule.kt` | Đăng ký Room: `single { Room.databaseBuilder(...) }`, `single { get<MusicDatabase>().recentSongDao() }`, `single { ...favoriteSongDao() }` — **cùng module với Retrofit/Repository**. |
| Sửa | `.../repository/RecentPlayedStore.kt` | **Bỏ SharedPreferences**, dùng `RecentSongDao`: `observeRecent(): Flow`, `add(song)` (suspend), `getRecentSnapshot()`. |
| Sửa | `.../repository/MusicRepository.kt` | Inject thêm `FavoriteSongDao` → thêm `observeFavorites(): Flow`, `isFavorite(id)`, `toggleFavorite(song)` (đọc/ghi Room). |
| Sửa | `.../viewmodel/HomeViewModel.kt` | `recentSongs` + `favorites` + `favoriteIds` (StateFlow) từ **Room Flow** — tự cập nhật khi data đổi (single source of truth). |
| Sửa | `.../viewmodel/PlaylistViewModel.kt`, `MusicViewModel.kt` | Thêm `favoriteIds` StateFlow + `toggleFavorite(song)` (Room). |
| Sửa | `.../adapter/SongAdapter.kt` | Thêm nút trái tim trên mỗi dòng: `onFavoriteClick` callback + `updateFavorites(ids)` (chỉ re-bind dòng thay đổi). |
| Sửa | `res/layout/item_song.xml` | Thêm `btnFavorite` (trái tim yêu thích). |
| Sửa | `res/layout/activity_home.xml`, `.../ui/HomeActivity.kt` | Thêm section **"Yêu thích"** (hiện/ẩn theo Room Flow), bấm bài → phát theo queue. |
| Mới | `res/drawable/ic_favorite.xml`, `ic_favorite_border.xml` | Icon trái tim đầy/rỗng. |
| Sửa | `gradle/libs.versions.toml`, `build.gradle.kts`, `app/build.gradle.kts` | Thêm Room 2.7.1, KSP 2.3.9, `androidx.media:media:1.7.0`. |

**Luồng dữ liệu Room:** Activity phát bài → `RecentPlayedStore.add()` (Room upsert) / bấm tim → `ViewModel.toggleFavorite()` → `MusicRepository` (Room) → Room tự emit Flow → ViewModel collect → UI tự cập nhật (danh sách gần đây + yêu thích + icon tim).

---

## 2.5 Phần 3 — Refactor kiến trúc theo project MẪU (DIYWallpaper) + Room playlist đã lưu

> Mục đích: học và áp dụng đúng convention của project mẫu (base class, package structure,
> onState pattern, Repository interface/impl, Room mapper). Không đổi engine phát nhạc
> (vẫn MediaPlayer + Koin + StateFlow — đúng các pattern có trong mẫu).

### 2.5.1 Cấu trúc package MỚI (giống mẫu)

```
com.example.musicplayer/
├── common/                 ← Base classes: IViewModel (onState), IActivity, ResultFlow
├── data/
│   ├── local/              ← Room: MusicDatabase v2, Migrations, dao/, entities/, mapper/, repository/
│   └── repository/         ← MusicRepositoryImpl (impl)
├── domain/
│   └── repository/         ← MusicRepository (INTERFACE)
├── di/                     ← 4 Koin module (network/repository/service/viewModel)
├── model/  network/  service/  adapter/  (giữ nguyên)
└── presenter/              ← UI theo feature (như mẫu: presenter/home, presenter/search...)
    ├── base/BasePlayerActivity.kt     (kế thừa IActivity)
    ├── home/HomeActivity.kt + HomeViewModel.kt
    ├── search/MainActivity.kt + MusicViewModel.kt
    └── songlist/SongListActivity.kt + PlaylistViewModel.kt
```

| Thay đổi | Trước | Sau |
|---|---|---|
| `viewmodel/` | 3 ViewModel ở package gốc | → `presenter/home|search|songlist/` (cùng package với Activity) |
| `ui/` | 3 Activity ở `ui/` | → `presenter/` + `presenter/base/BasePlayerActivity` |
| `repository/` | `MusicRepository` (class), `RecentPlayedStore` | → `domain/repository/MusicRepository` (interface) + `data/repository/MusicRepositoryImpl` + `data/local/repository/RecentPlayedStore` |
| `data/local/entity` | số ít, extension function map | → `data/local/entities` (số nhiều) + `data/local/mapper/*Mapper` |
| Base class | Activity extends `AppCompatActivity`, VM extends `ViewModel` | → Activity extends `IActivity`/`BasePlayerActivity`, VM extends `IViewModel<State>` |

### 2.5.2 Base class (giống `common/` của mẫu)

| File | Vai trò |
|---|---|
| `common/IViewModel.kt` | Base VM: `onState(state)` (pattern command), `launchBlock()`, `withIO/withMain`, `isLoading` StateFlow, `toast/string`. Chứa `interface IState`. |
| `common/IActivity.kt` | Base Activity: template `setupInit → initViews → initObservers → initListeners`; `getLazyViewModel()/getLazyViewBinding()`; `observerLoadingState()`. |
| `common/ResultFlow.kt` | Sealed class `Initial/Loading/Success/Error` + `doOnSuccess/doOnLoading/doOnError`. |

**Pattern `onState()`:** UI gọi `viewModel.onState(HomeState.Next)` → ViewModel `when(state)` → xử lý.
Sealed class `HomeState/PlaylistState/MusicState : IViewModel.IState` khai báo **cuối file ViewModel** (đúng mẫu).
Next/Previous/TogglePlayPause trên mini player → `onState(State.Next/Previous/TogglePlayPause)` → `PlaybackController` → `MusicService`.

### 2.5.3 Repository pattern (interface ở domain, impl ở data)

- `domain/repository/MusicRepository.kt` — **interface**: network (search/stream/chart/playlist) + Room (favorites + saved playlists).
- `data/repository/MusicRepositoryImpl.kt` — **impl**: bọc Retrofit + 2 DAO + 2 Mapper.
- Koin: `singleOf(::MusicRepositoryImpl) bind MusicRepository::class` (đúng mẫu `singleOf(...) bind ...`).
- ⚠️ Khác mẫu 1 chút: mẫu dùng `viewModelOf(::HomeViewModel)` (Koin 4), project dùng Koin 3.5.6
  → dùng DSL tương đương `viewModel { HomeViewModel(get(), get(), get(), get()) }`.

### 2.5.4 Room: thêm "Playlist đã lưu" (bookmark) + Mapper

| File | Vai trò |
|---|---|
| `data/local/entities/PlaylistEntity.kt` | Bảng `saved_playlists` (encodeId PK, title, thumbnail, songCount, savedAt). |
| `data/local/dao/PlaylistDao.kt` | `insert` (REPLACE), `observeAll(): Flow`, `getById`, `deleteById`, `clear`. |
| `data/local/mapper/Mapper.kt` | Interface `Mapper<Entity, Model>` (giống mẫu). |
| `data/local/mapper/RecentSongMapper.kt`, `FavoriteSongMapper.kt`, `PlaylistMapper.kt` | Chuyển Entity ↔ Model (thay extension function cũ). |
| `data/local/Migrations.kt` | `MIGRATION_1_2`: tạo bảng `saved_playlists` — giữ dữ liệu cũ khi nâng cấp version 1 → 2. |
| `data/local/MusicDatabase.kt` | version 1 → 2, thêm `PlaylistEntity` + `playlistDao()`, `DATABASE_NAME`. |

**Luồng lưu playlist:** bấm nút bookmark trên card → `HomeActivity` → `viewModel.onState(HomeState.ToggleSavePlaylist(playlist))` → `MusicRepository.toggleSavePlaylist()` → `PlaylistDao` (Room) → Room Flow → `savedPlaylists` + `savedPlaylistIds` StateFlow → UI hiện section "Playlist đã lưu" + đổi icon bookmark đầy/rỗng.

### 2.5.5 UI mới / sửa

| File | Thay đổi |
|---|---|
| `adapter/PlaylistAdapter.kt` | Thêm `onSaveClick` + `updateSaved(ids)` (đổi icon bookmark). |
| `res/layout/item_playlist.xml` | Thêm nút `btnSave` (bookmark) góc trên-phải ảnh bìa. |
| `res/layout/activity_home.xml` | Thêm section "Playlist đã lưu" (`tvSavedPlaylistsHeader` + `rvSavedPlaylists`). |
| `res/drawable/ic_bookmark.xml`, `ic_bookmark_border.xml` | Icon bookmark đầy/rỗng. |
| `AndroidManifest.xml` | Cập nhật package Activity: `.presenter.home.HomeActivity` (launcher), `.presenter.search.MainActivity`, `.presenter.songlist.SongListActivity`. |
| `HUONG-DAN-CHAY-APP.md` | Cập nhật lệnh chạy app sang package mới. |

---

## 3. Sơ đồ luồng dữ liệu (kiến trúc cuối cùng)

```
                     ┌─────────────────── UI (View) ───────────────────┐
                     │  HomeActivity · SongListActivity · MainActivity  │
                     │  (mini player: Prev | Play/Pause | Next)          │
                     └──────────┬───────────────────────┬──────────────┘
                                │ bấm nút / bấm bài      │ bấm trái tim
                                ▼                        ▼
                     ┌─────────────────── ViewModel ───────────────────┐
                     │  HomeViewModel · PlaylistViewModel · MusicViewModel│
                     │  StateFlow: songs, recent, favorites, favoriteIds │
                     └──────┬──────────────────────────────┬────────────┘
                            │ next()/prev()/play/pause     │ toggleFavorite()
                            ▼                              ▼
                  ┌────────────────────┐         ┌──────────────────────────┐
                  │ PlaybackController │         │      Repository          │
                  │  (Koin singleton)  │         │  MusicRepository (Room)  │
                  └─────────┬──────────┘         │  RecentPlayedStore (Room)│
                            │ service ref        └───────┬──────────┬───────┘
                            ▼                            │          │
                  ┌────────────────────┐                  ▼          ▼
                  │    MusicService    │        ┌────────────┐  ┌─────────────┐
                  │  (Foreground +     │        │  Room DB   │  │  Retrofit   │
                  │   MediaPlayer)     │        │ (Entity/   │  │ (MusicApi-  │
                  │  next/prev/play/   │        │  DAO)      │  │  Service)   │
                  │  pause + notif     │        └────────────┘  └──────┬──────┘
                  └─────────┬──────────┘                               │
                            │ fetch URL stream khi auto-advance        ▼
                            └────────────────────────────────►  server (ZingMP3 proxy)
```

**Tóm tắt một luồng hoàn chỉnh:**
1. **Search** (MainActivity): UI gõ → `MusicViewModel.search()` → `MusicRepository.searchSongs()` → **Retrofit** → server ZingMP3 → StateFlow `songs` → UI.
2. **Play** (bấm bài): UI → `ViewModel.playSong()` (lấy stream URL) / `BasePlayerActivity.playQueue()` → gửi queue cho **MusicService** (Binder) → `MediaPlayer` phát → đồng thời `RecentPlayedStore.add()` ghi **Room** → Room Flow → Home cập nhật "Nghe gần đây".
3. **Next/Prev**: nút trên mini player/notification → ViewModel → `PlaybackController` → `MusicService.next()/previous()` → đổi bài trong queue.
4. **Favorite** (bấm tim): UI → `ViewModel.toggleFavorite()` → `MusicRepository` ghi **Room** → Room Flow → `favoriteIds` → đổi icon tim + cập nhật section "Yêu thích" trên Home.

---

## 4. Ghi chú test thủ công

### Phần 1 — next/prev
- [ ] Bấm **Next / Prev** trên mini player khi đang phát 1 playlist → bài chuyển đúng thứ tự, mini player cập nhật title/artist.
- [ ] Bấm **Next** đến hết danh sách → quay về bài đầu (vòng lặp). Bấm **Prev** ở bài đầu → nhảy về bài cuối.
- [ ] Bấm **Next / Prev** trên **Notification** khi app đang ở **background** (khóa màn hình) → nhạc vẫn chuyển bài đúng.
- [ ] Bấm **Play/Pause/Next/Prev/Stop** trên notification → icon và trạng thái đồng bộ với mini player.
- [ ] Khi bấm Next/PREV trong lúc bài đang buffer (chưa prepare) → không bị lỗi `-38`, bài mới tự phát khi có URL.
- [ ] (Optional) Quay lại màn hình sau khi tự chuyển bài → mini player hiển thị đúng bài đang phát.

### Phần 2 — Room
- [ ] Phát vài bài → thoát hẳn app → mở lại **Home** → section **"Nghe gần đây"** vẫn còn các bài đã phát (dữ liệu Room persist).
- [ ] Bấm trái tim trên 1 bài trong danh sách (Main/SongList) → icon chuyển **đầy** (đã thích). Bấm lại → **rỗng** (bỏ thích).
- [ ] Sau khi thích 1 bài → **Home** hiện section **"Yêu thích"** chứa bài đó. Thoát app, mở lại → vẫn còn (persist).
- [ ] Bấm bài trong "Yêu thích" → phát theo cả danh sách yêu thích (auto-advance sang bài kế tiếp).
- [ ] Kiểm tra file DB tồn tại (có thể xem qua Device Explorer: `data/data/com.example.musicplayer/databases/music_player.db`).
- [ ] Bấm tim liên tục nhanh nhiều lần → không crash, trạng thái cuối đúng (REPLACE/delete an toàn).
- [ ] Xoay màn hình (rotate) khi đang ở Home/SongList → danh sách gần đây/yêu thích không mất, không gọi lại API (ViewModel + Room Flow).

### Build / chạy
- [ ] Build: `gradlew.bat :app:assembleDebug` (đã PASS).
- [ ] Server: `cd server && npm run dev` + `adb reverse tcp:3000 tcp:3000` (như hướng dẫn `HUONG-DAN-CHAY-APP.md`).
