package app.swiftdrop

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.swiftdrop.core.Transfers
import app.swiftdrop.ui.SwiftDropApp
import app.swiftdrop.ui.SwiftDropTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        var startError: String? = null
        try {
            Transfers.init(applicationContext)
        } catch (e: Throwable) {
            startError = e.stackTraceToString()
        }

        setContent {
            SwiftDropTheme {
                if (startError != null) {
                    ErrorScreen(startError)
                } else {
                    try {
                        SwiftDropApp()
                    } catch (e: Throwable) {
                        ErrorScreen(e.stackTraceToString())
                    }
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun ErrorScreen(message: String) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("Terjadi error saat memulai aplikasi:", style = MaterialTheme.typography.titleMedium)
        Text(message, style = MaterialTheme.typography.bodySmall)
    }
}