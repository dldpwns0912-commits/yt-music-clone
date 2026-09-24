package com.ytmusic.core.designsystem.palette

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.palette.graphics.Palette
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.ytmusic.core.designsystem.theme.YtBlack
import com.ytmusic.core.designsystem.theme.YtSurface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class ExtractedPalette(
    val dominantColor: Color = YtSurface,
    val vibrantColor: Color = Color(0xFFE50914),
    val darkMutedColor: Color = YtBlack,
    val accentColor: Color = Color(0xFFFF5252)
)

@Singleton
class PaletteExtractor @Inject constructor() {

    suspend fun extractColorsFromUrl(context: Context, imageUrl: String): ExtractedPalette =
        withContext(Dispatchers.IO) {
            if (imageUrl.isBlank()) return@withContext ExtractedPalette()

            try {
                val loader = ImageLoader(context)
                val request = ImageRequest.Builder(context)
                    .data(imageUrl)
                    .allowHardware(false) // required to access Bitmap pixels for Palette
                    .build()

                val result = loader.execute(request)
                if (result is SuccessResult) {
                    val bitmap = (result.drawable as? BitmapDrawable)?.bitmap
                    if (bitmap != null) {
                        return@withContext extractFromBitmap(bitmap)
                    }
                }
                ExtractedPalette()
            } catch (_: Exception) {
                ExtractedPalette()
            }
        }

    fun extractFromBitmap(bitmap: Bitmap): ExtractedPalette {
        val palette = Palette.from(bitmap).generate()

        val dominant = palette.getDominantColor(0xFF1E1E1E.toInt())
        val vibrant = palette.getVibrantColor(0xFFFF334B.toInt())
        val darkMuted = palette.getDarkMutedColor(0xFF030303.toInt())
        val lightVibrant = palette.getLightVibrantColor(vibrant)

        return ExtractedPalette(
            dominantColor = Color(dominant),
            vibrantColor = Color(vibrant),
            darkMutedColor = Color(darkMuted),
            accentColor = Color(lightVibrant)
        )
    }
}

/**
 * Animated dynamic ambient glow composable that renders a blurred radial/linear gradient
 * behind the album artwork based on real-time extracted colors.
 */
@Composable
fun DynamicAmbientGlow(
    palette: ExtractedPalette,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit = {}
) {
    val animatedDominant by animateColorAsState(
        targetValue = palette.dominantColor.copy(alpha = 0.55f),
        animationSpec = tween(durationMillis = 850, easing = FastOutSlowInEasing),
        label = "dominantColorAnim"
    )

    val animatedVibrant by animateColorAsState(
        targetValue = palette.vibrantColor.copy(alpha = 0.35f),
        animationSpec = tween(durationMillis = 850, easing = FastOutSlowInEasing),
        label = "vibrantColorAnim"
    )

    val backgroundBrush = Brush.verticalGradient(
        0.0f to animatedDominant,
        0.35f to animatedVibrant,
        0.75f to YtBlack.copy(alpha = 0.95f),
        1.0f to YtBlack
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundBrush)
    ) {
        content()
    }
}
