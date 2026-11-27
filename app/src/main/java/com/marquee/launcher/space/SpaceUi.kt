package com.marquee.launcher.space

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

/** How long each photo stays up, and how long the dissolve between them takes. */
private const val SHOW_MS = 40_000L
private const val FADE_MS = 2_500

private val shade = Shadow(Color.Black.copy(alpha = 0.6f), blurRadius = 14f)

private class Shown(val photo: Photo, val seed: Int)

private fun request(context: Context, url: String) =
    ImageRequest.Builder(context).data(url).size(1920, 1200).build()

@Composable
fun SpaceScreen() {
    val context = LocalContext.current
    val density = LocalDensity.current
    var shown by remember { mutableStateOf<Shown?>(null) }
    var weather by remember { mutableStateOf<Weather.Now?>(null) }
    var drift by remember { mutableStateOf(IntOffset.Zero) }

    LaunchedEffect(Unit) {
        val playlist = SpacePhotos.Playlist(context)
        val loader = context.imageLoader
        var misses = 0
        while (true) {
            val photo = playlist.next()
            // Only switch once the next photo is fully loaded, so the dissolve is always photo to photo.
            if (loader.execute(request(context, photo.url)) !is SuccessResult) {
                misses++
                delay(if (misses % SpacePhotos.all.size == 0) 60_000L else 500L)
                continue
            }
            misses = 0
            shown = Shown(photo, Random.nextInt())
            // The text moves a little with every photo so nothing sits in one spot for long.
            val r = with(density) { 14.dp.roundToPx() }
            drift = IntOffset(Random.nextInt(-r, r + 1), Random.nextInt(-r, r + 1))
            loader.enqueue(request(context, playlist.peek().url))
            delay(SHOW_MS)
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            Weather.now(context)?.let { weather = it }
            delay(30 * 60_000L)
        }
    }
    val driftNow by animateIntOffsetAsState(drift, tween(2_000), label = "drift")

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Crossfade(targetState = shown, animationSpec = tween(FADE_MS), label = "space") { s ->
            if (s != null) Drifting(s)
        }
        // Just enough shade at the bottom for the text; the photo stays the star.
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0.58f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.7f))
            )
        )
        Caption(
            shown,
            Modifier.align(Alignment.BottomStart).padding(start = 56.dp, bottom = 46.dp).offset { driftNow },
        )
        ClockBlock(
            weather,
            Modifier.align(Alignment.BottomEnd).padding(end = 56.dp, bottom = 42.dp).offset { -driftNow },
        )
    }
}

/** A slow zoom and pan, different for every photo, that never shows the photo's edges. */
@Composable
private fun Drifting(s: Shown) {
    val context = LocalContext.current
    val m = remember(s) {
        val r = Random(s.seed)
        val a = 1.06f + r.nextFloat() * 0.03f
        val b = a + 0.05f
        val zoomIn = r.nextBoolean()
        val reach = (a - 1f) / 2f * 0.85f
        fun pan() = (r.nextFloat() * 2f - 1f) * reach
        floatArrayOf(if (zoomIn) a else b, if (zoomIn) b else a, pan(), pan(), pan(), pan())
    }
    val t = remember(s) { Animatable(0f) }
    LaunchedEffect(s) { t.animateTo(1f, tween((SHOW_MS + FADE_MS * 2).toInt(), easing = LinearEasing)) }
    AsyncImage(
        model = request(context, s.photo.url),
        contentDescription = s.photo.title,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize().graphicsLayer {
            val p = t.value
            val sc = m[0] + (m[1] - m[0]) * p
            scaleX = sc
            scaleY = sc
            translationX = (m[2] + (m[3] - m[2]) * p) * size.width
            translationY = (m[4] + (m[5] - m[4]) * p) * size.height
        },
    )
}

/** What you're looking at: fades in after each dissolve, then gets out of the way. */
@Composable
private fun Caption(s: Shown?, modifier: Modifier) {
    val alpha = remember { Animatable(0f) }
    var photo by remember { mutableStateOf<Photo?>(null) }
    LaunchedEffect(s) {
        alpha.animateTo(0f, tween(600))
        photo = s?.photo
        if (s == null) return@LaunchedEffect
        delay(1_500)
        alpha.animateTo(1f, tween(1_200))
        delay(14_000)
        alpha.animateTo(0f, tween(2_000))
    }
    val p = photo ?: return
    Column(modifier.graphicsLayer { this.alpha = alpha.value }) {
        BasicText(
            p.title,
            style = TextStyle(color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Light, shadow = shade),
        )
        Spacer(Modifier.height(6.dp))
        BasicText(
            p.subtitle,
            style = TextStyle(color = Color(0xFFD9DCE3), fontSize = 13.sp, fontWeight = FontWeight.Medium, shadow = shade),
        )
        Spacer(Modifier.height(4.dp))
        BasicText(
            p.credit,
            style = TextStyle(color = Color(0xFFA9AEB8), fontSize = 9.sp, shadow = shade),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 520.dp),
        )
    }
}

@Composable
private fun ClockBlock(weather: Weather.Now?, modifier: Modifier) {
    val context = LocalContext.current
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            delay(60_000L - (System.currentTimeMillis() % 60_000L) + 50L)
        }
    }
    val is24 = DateFormat.is24HourFormat(context)
    val time = SimpleDateFormat(if (is24) "H:mm" else "h:mm", Locale.getDefault()).format(now)
    val ampm = if (is24) "" else SimpleDateFormat("a", Locale.getDefault()).format(now).uppercase()
    val date = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(now).uppercase()
    Column(modifier, horizontalAlignment = Alignment.End) {
        Row(verticalAlignment = Alignment.Bottom) {
            BasicText(
                time,
                style = TextStyle(
                    color = Color.White,
                    fontSize = 64.sp,
                    fontWeight = FontWeight.ExtraLight,
                    letterSpacing = (-1).sp,
                    shadow = shade,
                ),
            )
            if (ampm.isNotEmpty()) {
                BasicText(
                    ampm,
                    style = TextStyle(color = Color(0xFFE3E5EA), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp, shadow = shade),
                    modifier = Modifier.padding(start = 6.dp, bottom = 15.dp),
                )
            }
        }
        BasicText(
            date,
            style = TextStyle(color = Color(0xFFE3E5EA), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp, shadow = shade),
        )
        if (weather != null) {
            Spacer(Modifier.height(6.dp))
            BasicText(
                "${weather.temp}°  ${weather.label}".trim(),
                style = TextStyle(color = Color(0xFFE3E5EA), fontSize = 13.sp, fontWeight = FontWeight.Medium, shadow = shade),
            )
        }
    }
}
