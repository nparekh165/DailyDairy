package com.khodiyar.dairybilling.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.khodiyar.dairybilling.AppContainer
import com.khodiyar.dairybilling.DairyApp
import com.khodiyar.dairybilling.MessageKind
import com.khodiyar.dairybilling.R
import com.khodiyar.dairybilling.domain.AppException
import com.khodiyar.dairybilling.domain.UiText
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Composable
fun appContainer(): AppContainer = (LocalContext.current.applicationContext as DairyApp).container

@Composable
inline fun <reified VM : ViewModel> appViewModel(key: String? = null, crossinline create: (AppContainer) -> VM): VM {
    val container = appContainer()
    return viewModel(key = key, factory = viewModelFactory { initializer { create(container) } })
}

/** Shared plumbing: runs an action, maps business errors to localized snackbars, tracks busy state. */
abstract class AppViewModel(protected val c: AppContainer) : ViewModel() {
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    protected fun <T> Flow<T>.stateInVm(initial: T): StateFlow<T> =
        stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

    protected fun action(
        success: UiText? = null,
        onError: ((Throwable) -> Unit)? = null,
        block: suspend () -> Unit,
    ): Job = viewModelScope.launch {
        _busy.value = true
        try {
            block()
            success?.let { c.notify(it, MessageKind.SUCCESS) }
        } catch (e: AppException) {
            c.notify(e.text, MessageKind.ERROR)
            onError?.invoke(e)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: android.database.sqlite.SQLiteConstraintException) {
            c.notify(UiText.res(R.string.err_constraint), MessageKind.ERROR)
            onError?.invoke(e)
        } catch (e: Exception) {
            c.notify(UiText.res(R.string.err_unexpected, e.message ?: e.javaClass.simpleName), MessageKind.ERROR)
            onError?.invoke(e)
        } finally {
            _busy.value = false
        }
    }

    suspend fun verifyPin(pin: String): Boolean = c.auth.verifyCurrentUser(pin)
}
