package com.example.musicplayer.common

/**
 * ResultFlow — sealed class bọc trạng thái tải dữ liệu, giống hệt project MẪU
 * (DIYWallpaper: `common/ResultFlow.kt`).
 *
 * TẠI SAO cần sealed class này?
 * → Thay vì dùng nhiều StateFlow rời rạc (isLoading, errorMessage, data) và phải
 *   tự suy luận trạng thái, `ResultFlow` gộp CẢ 4 trạng thái của 1 nguồn dữ liệu
 *   vào 1 kiểu duy nhất: Đang tải / Thành công / Thất bại / Chưa bắt đầu.
 * → `when (result)` buộc compiler kiểm tra đủ mọi nhánh → ít quên xử lý lỗi hơn.
 *
 * Trong project này chúng ta chủ yếu dùng StateFlow (như mẫu cũng dùng cho
 * categories), nhưng giữ sẵn ResultFlow để dùng cho các màn hình cần phân biệt
 * rõ Loading/Success/Error.
 */
sealed class ResultFlow<out T> {
    data class Success<T>(val data: T) : ResultFlow<T>()
    data class Error<T>(val msg: String? = null) : ResultFlow<T>()
    data class Loading<T>(val info: String = "") : ResultFlow<T>()
    class Initial<T> : ResultFlow<T>()
}

/** Chỉ chạy action nếu đang ở trạng thái Success */
inline fun <T> ResultFlow<T>.doOnSuccess(
    crossinline action: (data: T) -> Unit
) {
    if (this is ResultFlow.Success<T>) {
        action.invoke(data)
    }
}

/** Chỉ chạy action nếu đang ở trạng thái Loading */
inline fun <T> ResultFlow<T>.doOnLoading(
    crossinline action: (info: String) -> Unit
) {
    if (this is ResultFlow.Loading<T>) {
        action.invoke(info)
    }
}

/** Chỉ chạy action nếu đang ở trạng thái Error */
inline fun <T> ResultFlow<T>.doOnError(
    crossinline action: (msg: String?) -> Unit
) {
    if (this is ResultFlow.Error<T>) {
        action.invoke(msg)
    }
}
