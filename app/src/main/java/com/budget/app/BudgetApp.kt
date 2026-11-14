package com.budget.app

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.budget.app.data.SessionState
import com.budget.app.data.Settings
import com.budget.app.data.update.UpdateCheckWorker
import com.budget.app.data.update.UpdateNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BudgetApp : Application() {
    lateinit var container: AppContainer
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        scope.launch { container.session.load() }

        // Send waiting changes whenever the network comes back.
        scope.launch {
            container.network.online.collect { online ->
                if (online && container.session.state.value is SessionState.SignedIn) container.sync.flush()
            }
        }

        // Updates: the last result at once, then a check in the background about every two hours.
        scope.launch { container.updates.restore() }
        UpdateCheckWorker.schedule(this)

        // ...and whenever the app comes to the front; check for an update at most every 30 minutes.
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                container.network.recheck()
                scope.launch {
                    if (container.session.state.value is SessionState.SignedIn) container.sync.flush()
                    val last = container.settings.get(Settings.LAST_UPDATE_CHECK)?.toLongOrNull() ?: 0L
                    if (System.currentTimeMillis() - last > 30 * 60_000L) {
                        container.updates.check()
                        container.settings.set(Settings.LAST_UPDATE_CHECK, System.currentTimeMillis().toString())
                        container.updates.available.value?.let { UpdateNotifier.notifyOnce(this@BudgetApp, container.settings, it) }
                    }
                }
            }
        })
    }
}
