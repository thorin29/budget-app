package com.budget.app

import android.content.Context
import com.budget.app.data.EnrollLink
import com.budget.app.data.Session
import com.budget.app.data.Settings
import com.budget.app.data.budgetStore
import com.budget.app.data.local.BudgetDatabase
import com.budget.app.data.remote.ApiClient
import com.budget.app.data.remote.NetworkMonitor
import com.budget.app.data.secure.TokenStore
import com.budget.app.data.sync.LocalStore
import com.budget.app.data.sync.SyncManager
import com.budget.app.data.update.UpdateChecker
import com.budget.app.data.update.UpdateInstaller
import kotlinx.coroutines.flow.MutableStateFlow

/** Everything the screens share, built once per process. */
class AppContainer(context: Context) {
    private val app = context.applicationContext

    val settings = Settings(app.budgetStore)
    val tokens = TokenStore(settings)

    // The session supplies the server; the token store the token.
    private var sessionRef: Session? = null
    val api = ApiClient(serverProvider = { sessionRef?.server }, tokenProvider = { tokens.current() })

    val database = BudgetDatabase.build(app)
    val local = LocalStore(database.localDao(), app.budgetStore, api.json)
    val session = Session(settings, tokens, local, api).also { sessionRef = it }
    val sync = SyncManager(api, local, session)
    val network = NetworkMonitor(app)
    val updates = UpdateChecker()
    val installer = UpdateInstaller(app)

    /** A budget://enroll link the phone was opened with, waiting for the setup screen. */
    val pendingEnrollLink = MutableStateFlow<EnrollLink?>(null)
}
