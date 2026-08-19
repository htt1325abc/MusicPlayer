package com.example.musicplayer.common

import android.os.Bundle
import androidx.annotation.CallSuper
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewbinding.ViewBinding
import kotlinx.coroutines.launch

/**
 * IActivity — Activity base, học theo `common/IActivity.kt` của project MẪU.
 *
 * TẠI SAO cần base class này?
 * → Ép MỌI màn hình đi theo 1 template method cố định (giống mẫu):
 *       onCreate → setupInit() → setContentView → initViews() → initObservers() → initListeners()
 *   Lợi ích:
 *     - Người đọc code biết chính xác mỗi phần nằm ở đâu.
 *     - Không ai quên bước nào (ví dụ quên observe loading → không hiện progress).
 *
 * ⚠️ ĐIỂM KHÁC MẪU (cố ý):
 * → Mẫu dùng DataBinding (`VB : ViewDataBinding`) vì app mẫu bật dataBinding.
 *   Project này dùng ViewBinding thuần (`VB : ViewBinding`) — vẫn cùng tinh thần,
 *   không cần bật thêm plugin.
 * → Mẫu có edge-to-edge + status bar + ads + loading dialog. Project này chỉ giữ
 *   phần template + observerLoadingState (không ads, không dialog) cho gọn.
 *
 * @param VB  Kiểu ViewBinding của layout.
 * @param VM  Kiểu ViewModel (phải extends [IViewModel]).
 * @param State Kiểu sealed class State (phải implement [IViewModel.IState]).
 */
abstract class IActivity<VB : ViewBinding, VM : IViewModel<State>, State : IViewModel.IState> :
    AppCompatActivity() {

    // Activity con override để trả Lazy<VM> — thường là `viewModel<HomeViewModel>()` (Koin).
    protected val viewModel: VM by this.getLazyViewModel()
    abstract fun getLazyViewModel(): Lazy<VM>

    // Activity con override để trả Lazy<VB> — thường là `lazy { ActivityXBinding.inflate(layoutInflater) }`.
    protected val viewBinding: VB by this.getLazyViewBinding()
    abstract fun getLazyViewBinding(): Lazy<VB>

    protected val TAG: String
        get() = this::class.java.simpleName

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // setupInit chạy TRƯỚC setContentView → cho phép gọi enableEdgeToEdge() v.v.
        setupInit()
        setContentView(viewBinding.root)
        initViews(savedInstanceState)
        initObservers()
        initListeners()
    }

    /** Hook trước khi inflate view (dùng để gọi enableEdgeToEdge, khởi tạo service...). */
    protected open fun setupInit() = Unit

    /** Bắt buộc: khởi tạo view + adapter. */
    protected abstract fun initViews(savedInstanceState: Bundle?)

    /** Bắt buộc (mặc định rỗng): observe StateFlow của ViewModel → cập nhật UI. */
    @CallSuper
    protected open fun initObservers() = Unit

    /** Tùy chọn: gắn sự kiện click cho view. */
    protected open fun initListeners() = Unit

    /**
     * Tự collect `viewModel.isLoading` theo lifecycle STARTED.
     * Activity con gọi trong [initObservers]:
     *   observerLoadingState(
     *       onLoading = { binding.progressBar.visibility = View.VISIBLE },
     *       onLoaded  = { binding.progressBar.visibility = View.GONE }
     *   )
     */
    protected fun observerLoadingState(
        onLoading: () -> Unit,
        onLoaded: () -> Unit
    ) {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.isLoading.collect { loading ->
                    if (loading) onLoading() else onLoaded()
                }
            }
        }
    }
}
