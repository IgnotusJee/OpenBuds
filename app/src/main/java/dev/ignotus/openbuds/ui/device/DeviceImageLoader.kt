package dev.ignotus.openbuds.ui.device

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.ignotus.openbuds.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

internal val DeviceImageCache = LruCache<String, Bitmap>(16)

internal fun cachedBitmap(imageUrl: String): Bitmap? = synchronized(DeviceImageCache) {
    DeviceImageCache.get(imageUrl)
}

internal suspend fun loadRemoteBitmap(imageUrl: String): Bitmap? = withContext(Dispatchers.IO) {
    cachedBitmap(imageUrl)?.let { return@withContext it }
    runCatching {
        val connection = (URL(imageUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 5_000
            readTimeout = 8_000
            instanceFollowRedirects = true
        }
        try {
            connection.inputStream.use(BitmapFactory::decodeStream)
        } finally {
            connection.disconnect()
        }
    }.getOrNull()?.also { bitmap ->
        synchronized(DeviceImageCache) {
            DeviceImageCache.put(imageUrl, bitmap)
        }
    }
}

@Composable
internal fun DeviceModelImage(
    imageUrl: String?,
    modelName: String?,
) {
    val bitmap by produceState<Bitmap?>(initialValue = imageUrl?.let(::cachedBitmap), imageUrl) {
        value = imageUrl?.let { cachedBitmap(it) ?: loadRemoteBitmap(it) }
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(148.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = modelName ?: stringResource(R.string.device_image_desc),
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(132.dp)
                    .padding(12.dp),
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.Bluetooth,
                contentDescription = modelName ?: stringResource(R.string.device_image_desc),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp),
            )
        }
    }
}
