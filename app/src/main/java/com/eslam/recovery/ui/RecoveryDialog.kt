package com.eslam.recovery.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun RecoveryDialog(vm: MainViewModel) {
    val state by vm.recovery.collectAsStateWithLifecycle()
    when (val s = state) {
        RecoveryState.Idle -> Unit

        is RecoveryState.Running -> AlertDialog(
            onDismissRequest = {},
            title = { Text("Recovering…") },
            text = {
                val p = s.progress
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    LinearProgressIndicator(
                        progress = { if (p.totalBytes > 0) (p.doneBytes.toFloat() / p.totalBytes).coerceIn(0f, 1f) else 0f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("${p.doneFiles} / ${p.totalFiles} files · ${formatSize(p.doneBytes)} of ${formatSize(p.totalBytes)}")
                }
            },
            confirmButton = { TextButton(vm::cancelRecovery) { Text("Cancel") } },
        )

        is RecoveryState.Done -> AlertDialog(
            onDismissRequest = vm::dismissRecovery,
            title = { Text(if (s.summary.error != null) "Recovery failed" else "Recovery finished") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val sum = s.summary
                    if (sum.error != null) Text(sum.error)
                    else {
                        Text("${sum.recoveredIds.size} files recovered to:\n${sum.destination}")
                        Text("Photos and videos also appear in your gallery.", style = MaterialTheme.typography.bodySmall)
                    }
                    if (sum.failures.isNotEmpty()) {
                        Text("${sum.failures.size} failed:", color = MaterialTheme.colorScheme.error)
                        sum.failures.take(5).forEach { (name, reason) ->
                            Text("$name — $reason", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            },
            confirmButton = { TextButton(vm::dismissRecovery) { Text("OK") } },
        )
    }
}
