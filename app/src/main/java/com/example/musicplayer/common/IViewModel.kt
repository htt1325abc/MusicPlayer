package com.example.musicplayer.common

import android.app.Application
import android.util.Log
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * IViewModel — ViewModel base, học theo `common/IViewModel.kt` của project MẪU.
 *
 * TẠI SAO cần base class này?
 * → Gộp những thứ LẶP LẠI ở mọi ViewModel về 1 chỗ:
 *     1. `onState(state)` — pattern COMMAND: UI chỉ gửi 1 sealed-class State,
 *        ViewModel tự quyết định làm gì (thay vì Activity gọi hàm trực tiếp).
 *     2. `launchBlock()`  — chạy coroutine có gắn sẵn CoroutineExceptionHandler
 *        (bắt exception, log ra nhưng không crash) — giống hệt mẫu.
 *     3. `withIO()/withMain()` — đổi thread có sẵn.
 *     4. `isLoading` StateFlow — mọi màn hình đều cần hiện/ẩn loading.
 *     5. `toast()/string()` — tiện ích UI từ ViewModel.
 *
 * ⚠️ ĐIỂM KHÁC MẪU (cố ý, không phải lỗi):
 * → Mẫu cho `IViewModel` kế thừa thêm `KoinComponent` để tự `by inject()` bên trong.
 * → Project này KHÔNG cần: mọi dependency đều được Koin bơm qua CONSTRUCTOR
 *   (`viewModelOf(::HomeViewModel)`), ViewModel không tự resolve gì → bỏ KoinComponent
 *   cho đơn giản, không phụ thuộc ngầm.
 *
 * @param State sealed class mô tả "ý định" của UI — phải implement [IState].
 */
abstract class IViewModel<State : IViewModel.IState>(application: Application) :
    AndroidViewModel(application) {

    // Bắt MỌI exception trong coroutine do launchBlock tạo ra:
    // log ra Logcat để debug, không để crash app (giống mẫu: silent-fail có log).
    private val coroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e("IViewModel", "Coroutine exception: $throwable", throwable)
    }

    protected val applicationContext
        get() = getApplication<Application>().applicationContext

    // ---- Loading state (tự observe bởi IActivity.observerLoadingState) ----
    private val _isLoading = MutableStateFlow(false)
    internal val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun setLoading(loading: Boolean) {
        _isLoading.update { loading }
    }

    /**
     * Chạy coroutine trong viewModelScope.
     * - Mặc định Main.immediate (UI thread) — đủ cho việc collect Flow / cập nhật state.
     * - Muốn chạy nền thì truyền `Dispatchers.IO`.
     */
    protected fun launchBlock(
        dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
        block: suspend CoroutineScope.() -> Unit
    ): Job {
        return viewModelScope.launch(
            context = dispatcher + coroutineExceptionHandler,
            block = block
        )
    }

    protected suspend fun <T> withMain(block: suspend CoroutineScope.() -> T): T =
        withContext(Dispatchers.Main.immediate, block)

    protected suspend fun <T> withIO(block: suspend CoroutineScope.() -> T): T =
        withContext(Dispatchers.IO, block)

    protected fun toast(str: String) {
        Toast.makeText(applicationContext, str, Toast.LENGTH_SHORT).show()
    }

    protected fun toast(resId: Int) {
        Toast.makeText(applicationContext, resId, Toast.LENGTH_SHORT).show()
    }

    protected fun string(@StringRes resId: Int): String = applicationContext.getString(resId)

    /**
     * Pattern COMMAND — Activity gọi `viewModel.onState(HomeState.Xxx)`,
     * ViewModel override và dùng `when(state)` để xử lý.
     */
    abstract fun onState(state: State)

    /** Marker interface cho sealed class State của từng màn hình (giống mẫu). */
    interface IState
}
