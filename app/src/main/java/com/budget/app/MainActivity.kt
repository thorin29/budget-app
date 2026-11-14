package com.budget.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.budget.app.data.EnrollLinks
import com.budget.app.ui.AppRoot
import com.budget.app.ui.theme.BudgetTheme

class MainActivity : ComponentActivity() {
    private val container get() = (application as BudgetApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        takeLink(intent)
        setContent {
            BudgetTheme {
                AppRoot(container)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        takeLink(intent)
    }

    private fun takeLink(intent: Intent?) {
        EnrollLinks.parse(intent?.data?.toString())?.let { container.pendingEnrollLink.value = it }
    }
}
