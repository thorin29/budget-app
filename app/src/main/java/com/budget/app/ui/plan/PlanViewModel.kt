package com.budget.app.ui.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budget.app.AppContainer
import com.budget.app.data.remote.ApiException
import com.budget.app.data.remote.Projection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

data class PlanUi(
    val horizonDays: Int = DEFAULT_HORIZON,
    val projection: Projection? = null,
    /** Whole cents, from the slider. */
    val payCents: Long = 0,
    val loading: Boolean = false,
    val offline: Boolean = false,
    val error: String? = null,
)

const val DEFAULT_HORIZON = 45
val HORIZONS = listOf(30, 45, 60, 90)

class PlanViewModel(private val c: AppContainer) : ViewModel() {
    private val _ui = MutableStateFlow(PlanUi())
    val ui: StateFlow<PlanUi> = _ui

    fun load() {
        val horizon = _ui.value.horizonDays
        viewModelScope.launch {
            _ui.update { it.copy(loading = true, error = null) }
            if (_ui.value.projection == null && horizon == DEFAULT_HORIZON) {
                c.local.loadProjection()?.let { p -> _ui.update { it.copy(projection = p) } }
            }
            try {
                val fresh = c.api.projection(horizon)
                if (horizon == DEFAULT_HORIZON) c.local.saveProjection(fresh)
                c.network.confirmOnline()
                if (_ui.value.horizonDays == horizon) _ui.update { it.copy(projection = fresh, loading = false, offline = false) }
            } catch (e: ApiException) {
                if (e.status == 401) c.session.onUnauthorized()
                _ui.update { it.copy(loading = false, error = e.message) }
            } catch (_: IOException) {
                _ui.update { it.copy(loading = false, offline = true) }
            }
        }
    }

    fun setHorizon(days: Int) {
        if (days == _ui.value.horizonDays) return
        _ui.update { it.copy(horizonDays = days) }
        load()
    }

    fun setPay(cents: Long) {
        _ui.update { it.copy(payCents = cents) }
    }
}
