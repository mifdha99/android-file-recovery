package com.eslam.recovery

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eslam.recovery.ui.AppNav
import com.eslam.recovery.ui.MainViewModel
import com.eslam.recovery.ui.RecoveryState
import com.eslam.recovery.ui.ScanState
import com.eslam.recovery.ui.theme.RecoveryTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels { MainViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RecoveryTheme {
                val scan by viewModel.scan.collectAsStateWithLifecycle()
                val recovery by viewModel.recovery.collectAsStateWithLifecycle()
                // A long scan or copy must not be interrupted by the screen turning off.
                val busy = scan is ScanState.Running || recovery is RecoveryState.Running
                DisposableEffect(busy) {
                    if (busy) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    onDispose { window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
                }
                AppNav(viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshAccess() // user may be returning from the All files access settings screen
    }
}
