package app.swiftdrop.ui

import android.graphics.Bitmap
import android.graphics.Color as AColor
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.util.Locale

/** Target sentuh minimal 48dp sesuai PRD. */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(56.dp),
        colors = ButtonDefaults.buttonColors(),
    ) { Text(text, style = MaterialTheme.typography.titleMedium) }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(52.dp),
    ) { Text(text) }
}

@Composable
fun ScreenColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(PaddingValues(horizontal = 24.dp, vertical = 32.dp)),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.Start,
        content = content,
    )
}

@Composable
fun Heading(text: String) {
    Text(text, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
}

@Composable
fun Body(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
fun KeyValue(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun QrImage(text: String, description: String) {
    val bitmap = androidx.compose.runtime.remember(text) { qrBitmap(text) }
    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = description,
        modifier = Modifier.size(220.dp).semantics { contentDescription = description },
    )
}

@Composable
fun Spacer16() = Spacer(Modifier.height(16.dp))

fun formatBytes(bytes: Long): String {
    val b = bytes.toDouble()
    return when {
        b < 1024 -> "${bytes} B"
        b < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", b / 1024)
        b < 1024L * 1024 * 1024 -> String.format(Locale.US, "%.1f MB", b / (1024 * 1024))
        else -> String.format(Locale.US, "%.2f GB", b / (1024.0 * 1024 * 1024))
    }
}

/** Kecepatan ditampilkan sebagai nilai terukur sesaat, bukan estimasi. */
fun formatSpeed(bps: Long): String = "${formatBytes(bps)}/s"

private fun qrBitmap(text: String, size: Int = 512): Bitmap {
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
    val pixels = IntArray(size * size) { i ->
        if (matrix.get(i % size, i / size)) AColor.BLACK else AColor.WHITE
    }
    return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
}
