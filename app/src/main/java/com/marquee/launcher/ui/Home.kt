package com.marquee.launcher.ui

import android.content.Context
import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.marquee.launcher.data.AppRepo
import com.marquee.launcher.data.Catalogs
import com.marquee.launcher.data.Channel
import com.marquee.launcher.data.Img
import com.marquee.launcher.data.Prefs
import com.marquee.launcher.data.Program
import com.marquee.launcher.data.Title
import com.marquee.launcher.data.TvApp
import com.marquee.launcher.data.TvChannels
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.lazy.grid.items as gridItems

private val Ink = Color(0xFF050608)
private val TextHi = Color(0xFFF6F4EF)
private val TextMid = Color(0xFFD5D8DE)
private val TextLo = Color(0xFFA3A8B3)
private val Amber = Color(0xFFFFC24D)
private val TileBase = Color(0xFF15171E)

private const val ALL_APPS_KEY = "marquee:all-apps"

private enum class Area { Apps, Hero, Continue, Content }

private fun isSelectKey(e: KeyEvent) =
    e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter

private class PressState {
    var down = false
    var fired = false
}

/** OK on the remote; hold OK for [onLongClick]. Fires on release so a long press never also clicks. */
@Composable
private fun Modifier.remoteClick(onLongClick: (() -> Unit)? = null, onClick: () -> Unit): Modifier {
    val state = remember { PressState() }
    return this.onKeyEvent { e ->
        if (!isSelectKey(e)) return@onKeyEvent false
        when (e.type) {
            KeyEventType.KeyDown -> {
                if (e.nativeKeyEvent.repeatCount == 0) {
                    state.down = true
                    state.fired = false
                } else if (onLongClick != null && state.down && !state.fired) {
                    state.fired = true
                    onLongClick()
                }
            }
            KeyEventType.KeyUp -> {
                if (state.down && !state.fired) onClick()
                state.down = false
                state.fired = false
            }
        }
        true
    }
}

private fun backdropRequest(context: Context, url: String) =
    ImageRequest.Builder(context).data(url).size(1920, 1080).build()

private fun programTitle(p: Program) = Title(
    id = "p:${p.key}",
    type = "",
    name = p.title,
    background = p.image?.takeIf { p.aspect >= 1.3f },
    logo = null,
    year = null,
    runtime = null,
    rating = null,
    genres = emptyList(),
    description = p.subtitle,
)

private fun channelLabel(ch: Channel, app: TvApp?): String {
    val appName = app?.label ?: ch.name
    return if (ch.name.isBlank() || ch.name.equals(appName, ignoreCase = true)) "On $appName" else "$appName · ${ch.name}"
}

@Composable
fun MarqueeApp(resumeTick: Int, homeTick: Int) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<TvApp>>(emptyList()) }
    var pinned by remember { mutableStateOf(Prefs.pinned(context)) }
    var showAll by remember { mutableStateOf(false) }
    LaunchedEffect(resumeTick) {
        val have = apps.map { it.packageName }.sorted()
        val now = withContext(Dispatchers.IO) { AppRepo.packages(context) }
        if (now != have) apps = withContext(Dispatchers.IO) { AppRepo.loadAll(context) }
    }
    LaunchedEffect(homeTick) { showAll = false }
    val setPinned: (List<String>) -> Unit = { list ->
        pinned = list
        Prefs.setPinned(context, list)
    }
    Box(Modifier.fillMaxSize().background(Ink)) {
        if (apps.isNotEmpty()) {
            if (showAll) {
                AllAppsScreen(apps, pinned, setPinned, onBack = { showAll = false })
            } else {
                HomeScreen(apps, pinned, setPinned, resumeTick, homeTick, onAllApps = { showAll = true })
            }
        }
    }
}

@Composable
private fun HomeScreen(
    apps: List<TvApp>,
    pinnedPkgs: List<String>,
    onPinnedChange: (List<String>) -> Unit,
    resumeTick: Int,
    homeTick: Int,
    onAllApps: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pinned = remember(apps, pinnedPkgs) {
        pinnedPkgs.mapNotNull { p -> apps.firstOrNull { it.packageName == p } }
    }
    val requesters = remember { HashMap<String, FocusRequester>() }
    val allAppsFocus = remember { FocusRequester() }
    val heroFocus = remember { FocusRequester() }
    var heroRequest by remember { mutableIntStateOf(0) }
    val appsState = rememberLazyListState()

    var focusedPkg by remember { mutableStateOf(pinned.firstOrNull()?.packageName) }
    var shownPkg by remember { mutableStateOf(focusedPkg) }
    var area by remember { mutableStateOf(Area.Apps) }
    var titles by remember { mutableStateOf<List<Title>>(emptyList()) }
    var titlesPaths by remember { mutableStateOf<List<String>?>(null) }
    var heroIndex by remember { mutableIntStateOf(0) }
    var focusedTitle by remember { mutableStateOf<Title?>(null) }
    var channels by remember { mutableStateOf<List<Channel>>(emptyList()) }
    var focusedProgram by remember { mutableStateOf<Program?>(null) }
    var menuFor by remember { mutableStateOf<TvApp?>(null) }
    var movingPkg by remember { mutableStateOf<String?>(null) }
    var pendingFocus by remember { mutableStateOf<String?>(null) }
    val progress = remember { Animatable(0f) }

    fun requesterFor(pkg: String): FocusRequester = requesters.getOrPut(pkg) { FocusRequester() }
    fun focusApp(pkg: String?) {
        pendingFocus = pkg ?: pinned.firstOrNull()?.packageName ?: ALL_APPS_KEY
    }

    LaunchedEffect(pendingFocus) {
        val target = pendingFocus ?: return@LaunchedEffect
        repeat(20) {
            delay(30)
            val fr = if (target == ALL_APPS_KEY) allAppsFocus else requesters[target]
            if (fr != null && runCatching { fr.requestFocus() }.isSuccess) {
                pendingFocus = null
                return@LaunchedEffect
            }
        }
        pendingFocus = null
    }
    LaunchedEffect(Unit) { focusApp(pinned.firstOrNull()?.packageName) }
    // Home pressed while already home: back to the start of the app row.
    LaunchedEffect(homeTick) {
        if (homeTick == 0) return@LaunchedEffect
        menuFor = null
        movingPkg = null
        area = Area.Apps
        runCatching { appsState.scrollToItem(0) }
        focusApp(pinned.firstOrNull()?.packageName)
    }

    // Follow focus a beat after it settles, so skimming across the row stays smooth.
    LaunchedEffect(focusedPkg) {
        if (focusedPkg != shownPkg) {
            delay(220)
            shownPkg = focusedPkg
        }
    }
    val source = Catalogs.sourceFor(shownPkg)
    val shownApp = pinned.firstOrNull { it.packageName == shownPkg }

    LaunchedEffect(source, resumeTick) {
        if (source == null) return@LaunchedEffect
        if (titlesPaths != source.paths) {
            titles = Catalogs.cached(source) ?: emptyList()
            heroIndex = 0
            titlesPaths = source.paths
        }
        if (titles.isEmpty() || Catalogs.isStale(source)) {
            val fresh = Catalogs.load(source)
            if (titlesPaths == source.paths && fresh.isNotEmpty() && fresh.map { it.id } != titles.map { it.id }) {
                titles = fresh
                if (heroIndex >= fresh.size) heroIndex = 0
            }
        }
    }
    LaunchedEffect(resumeTick) { channels = TvChannels.load(context) }
    // Warm every pinned app's catalog and first backdrop so switching apps is instant.
    LaunchedEffect(pinnedPkgs) {
        delay(1500)
        pinned.mapNotNull { Catalogs.sourceFor(it.packageName) }.distinct().forEach { s ->
            Catalogs.load(s).firstOrNull { it.background != null }?.let { t ->
                Img.backdrop(t.background)?.let { context.imageLoader.enqueue(backdropRequest(context, it)) }
            }
        }
    }

    // Lead with well-rated titles that have real logo art.
    val heroPool = remember(titles) {
        val withArt = titles.take(24).filter { it.background != null }
        val strong = withArt.filter { it.logo != null && (it.rating?.toFloatOrNull() ?: 0f) >= 6.5f }
        (if (strong.size >= 3) strong else withArt).take(6)
    }
    val cw = remember(channels) { TvChannels.continueWatching(channels) }
    val appChannel = remember(channels, shownPkg) { TvChannels.rowFor(channels, shownPkg) }
    val browsing = area == Area.Apps || area == Area.Hero
    val spotlight = if (browsing && (source == null || heroPool.isEmpty())) shownApp else null
    val programHero = remember(focusedProgram) { focusedProgram?.let { programTitle(it) } }
    val hero: Title? = when (area) {
        Area.Content -> if (appChannel != null) programHero else focusedTitle
        Area.Continue -> programHero
        Area.Apps, Area.Hero -> if (spotlight != null) null else heroPool.getOrNull(heroIndex)
    }
    val heroLabel = when {
        area == Area.Continue -> "Continue watching"
        area == Area.Content && appChannel != null -> channelLabel(appChannel, shownApp)
        else -> source?.label.orEmpty()
    }
    val heroApp = if (area == Area.Continue) {
        apps.firstOrNull { it.packageName == focusedProgram?.pkg } ?: shownApp
    } else {
        shownApp
    }
    val showIndicator = browsing && spotlight == null && heroPool.size > 1

    // The featured title can take focus: Up from the app row lands on it, OK opens it.
    val heroActionable = browsing && spotlight == null && source != null && heroPool.isNotEmpty()
    val openPkg = source?.open ?: "com.nuvio.tv"
    val openApp = apps.firstOrNull { it.packageName == openPkg }
    val openName = when (openPkg) {
        "com.nuvio.tv" -> "Nuvio"
        "com.stremio.one" -> "Stremio"
        "com.amazon.amazonvideo.livingroom" -> "Prime Video"
        "com.cbs.ott" -> "Paramount+"
        else -> openApp?.label ?: "app"
    }

    fun tryFocusHero(): Boolean = runCatching { heroFocus.requestFocus(FocusDirection.Enter) }.getOrDefault(false)

    fun enterHero(): Boolean {
        val pkg = focusedPkg ?: return false
        if (Catalogs.sourceFor(pkg) == null) return false
        if (pkg == shownPkg) return heroActionable && tryFocusHero()
        // Up pressed before the hero caught up with the row: switch now, focus once it's drawn.
        shownPkg = pkg
        heroRequest++
        return true
    }

    LaunchedEffect(heroRequest) {
        if (heroRequest == 0) return@LaunchedEffect
        repeat(25) {
            delay(30)
            if (tryFocusHero()) return@LaunchedEffect
        }
    }
    // If the featured titles vanish while the hero has focus, hand focus back to the row.
    LaunchedEffect(area, heroActionable) {
        if (area == Area.Hero && !heroActionable) focusApp(shownPkg)
    }

    // Rotate every 9s while browsing the row; hold still while the hero itself has focus.
    val autoAdvance = showIndicator && area == Area.Apps
    LaunchedEffect(heroPool, heroIndex, autoAdvance) {
        if (autoAdvance) {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(9000, easing = LinearEasing))
            heroIndex = (heroIndex + 1) % heroPool.size
        } else {
            progress.snapTo(if (area == Area.Hero) 1f else 0f)
        }
    }
    // Keep both neighbours warm so stepping either way is instant.
    LaunchedEffect(heroPool, heroIndex) {
        if (heroPool.size > 1) {
            listOf(1, heroPool.size - 1).distinct().forEach { step ->
                val t = heroPool[(heroIndex + step) % heroPool.size]
                Img.backdrop(t.background)?.let { context.imageLoader.enqueue(backdropRequest(context, it)) }
                Img.logo(t.logo)?.let {
                    context.imageLoader.enqueue(ImageRequest.Builder(context).data(it).build())
                }
            }
        }
    }

    // Back never leaves Home: it steps back to the app row, then to the first app.
    BackHandler(enabled = menuFor == null) {
        when {
            movingPkg != null -> movingPkg = null
            area != Area.Apps -> focusApp(shownPkg)
            else -> {
                scope.launch { runCatching { appsState.animateScrollToItem(0) } }
                focusApp(pinned.firstOrNull()?.packageName)
            }
        }
    }

    val hasCw = cw.isNotEmpty()
    val showContent = appChannel != null || (source != null && titles.isNotEmpty())
    // Focusing the hero clears the stage: the rows drop away and the title settles lower, like a poster.
    val heroTop by animateDpAsState(if (area == Area.Hero) 164.dp else 70.dp, tween(420), label = "heroTop")
    val appsTop by animateDpAsState(
        when (area) {
            Area.Apps -> 328.dp
            Area.Hero -> 400.dp
            else -> 250.dp
        },
        tween(380),
        label = "appsTop",
    )
    val appsAlpha by animateFloatAsState(if (area == Area.Apps) 1f else 0f, tween(260), label = "appsAlpha")
    val cwTop by animateDpAsState(
        when (area) {
            Area.Apps -> 436.dp
            Area.Hero -> 508.dp
            Area.Continue -> 334.dp
            Area.Content -> 250.dp
        },
        tween(380),
        label = "cwTop",
    )
    val cwAlpha by animateFloatAsState(
        if (area == Area.Content || area == Area.Hero) 0f else 1f,
        tween(260),
        label = "cwAlpha",
    )
    val contentTop by animateDpAsState(
        when {
            area == Area.Content -> 334.dp
            area == Area.Hero -> if (hasCw) 678.dp else 508.dp
            hasCw && area == Area.Continue -> 499.dp
            hasCw -> 606.dp
            else -> 436.dp
        },
        tween(380),
        label = "contentTop",
    )
    val contentAlpha by animateFloatAsState(if (area == Area.Hero) 0f else 1f, tween(260), label = "contentAlpha")

    Box(Modifier.fillMaxSize()) {
        Backdrop(url = if (spotlight != null) null else Img.backdrop(hero?.background), spotlight = spotlight)
        Scrims()
        Crossfade(targetState = spotlight, animationSpec = tween(500), label = "heroMode") { sp ->
            if (sp != null) {
                Box(Modifier.fillMaxSize()) {
                    SpotlightHero(sp, Modifier.padding(start = 48.dp, top = 70.dp))
                    SpotlightCard(sp, Modifier.align(Alignment.TopEnd).padding(top = 108.dp, end = 96.dp))
                }
            } else {
                Hero(
                    title = hero,
                    label = heroLabel,
                    app = heroApp,
                    showIndicator = showIndicator,
                    count = heroPool.size,
                    index = heroIndex,
                    progress = { progress.value },
                    expanded = area == Area.Hero,
                    actionLabel = if (heroActionable) "Open in $openName" else null,
                    actionIcon = openApp?.icon,
                    focusRequester = heroFocus,
                    onFocused = { area = Area.Hero },
                    onOpen = { heroPool.getOrNull(heroIndex)?.let { AppRepo.openTitle(context, it, openPkg) } },
                    onStep = { d -> if (heroPool.isNotEmpty()) heroIndex = (heroIndex + d + heroPool.size) % heroPool.size },
                    onExit = { focusApp(shownPkg) },
                    modifier = Modifier.offset { IntOffset(48.dp.roundToPx(), heroTop.roundToPx()) },
                )
            }
        }
        TopBar(
            onAllApps = onAllApps,
            // Down from the top bar goes back where you came from, not to whatever tile is underneath.
            onDown = { if (!(area == Area.Hero && tryFocusHero())) focusApp(shownPkg) },
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 26.dp, end = 48.dp),
        )

        LazyRow(
            state = appsState,
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(0, appsTop.roundToPx()) }
                .graphicsLayer { alpha = appsAlpha }
                .onPreviewKeyEvent { e ->
                    val moving = movingPkg
                    if (moving == null) {
                        // Up from anywhere in the row lands on the featured title, when the app has one.
                        return@onPreviewKeyEvent e.type == KeyEventType.KeyDown && e.key == Key.DirectionUp && enterHero()
                    }
                    if (e.type == KeyEventType.KeyDown) {
                        val delta = when (e.key) {
                            Key.DirectionLeft -> -1
                            Key.DirectionRight -> 1
                            else -> 0
                        }
                        val visible = pinned.map { it.packageName }
                        val from = visible.indexOf(moving)
                        if (delta != 0 && from >= 0) {
                            val to = (from + delta).coerceIn(0, visible.lastIndex)
                            if (to != from) {
                                val list = visible.toMutableList()
                                list.add(to, list.removeAt(from))
                                onPinnedChange(list)
                                scope.launch { runCatching { appsState.animateScrollToItem((to - 2).coerceAtLeast(0)) } }
                            }
                        }
                    } else if (e.type == KeyEventType.KeyUp && (isSelectKey(e) || e.key == Key.Back)) {
                        movingPkg = null
                    }
                    true
                },
            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(pinned, key = { it.packageName }) { app ->
                AppTile(
                    app = app,
                    width = 136.dp,
                    height = 77.dp,
                    focusRequester = requesterFor(app.packageName),
                    onFocused = { focusedPkg = app.packageName; area = Area.Apps },
                    onClick = { AppRepo.launch(context, app.packageName) },
                    onLongClick = { menuFor = app },
                    moving = movingPkg == app.packageName,
                )
            }
            item(key = "all-apps") {
                AllAppsTile(focusRequester = allAppsFocus, onFocused = { area = Area.Apps }, onClick = onAllApps)
            }
        }

        val moving = movingPkg
        if (moving != null) {
            val label = pinned.firstOrNull { it.packageName == moving }?.label.orEmpty()
            Row(
                Modifier.offset { IntOffset(48.dp.roundToPx(), (appsTop + 114.dp).roundToPx()) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                KeyCap("← →")
                Spacer(Modifier.width(8.dp))
                BasicText("Move $label", style = TextStyle(color = TextMid, fontSize = 13.sp, fontWeight = FontWeight.Medium))
                Spacer(Modifier.width(18.dp))
                KeyCap("OK")
                Spacer(Modifier.width(8.dp))
                BasicText("Done", style = TextStyle(color = TextMid, fontSize = 13.sp, fontWeight = FontWeight.Medium))
            }
        }

        if (hasCw && moving == null) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .offset { IntOffset(0, cwTop.roundToPx()) }
                    .graphicsLayer { alpha = cwAlpha }
            ) {
                RowLabel("Continue Watching", Modifier.padding(start = 48.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 48.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(cw, key = { it.key }) { p ->
                        ProgramCard(
                            p = p,
                            badge = apps.firstOrNull { it.packageName == p.pkg }?.icon,
                            onFocused = { focusedProgram = p; area = Area.Continue },
                            onClick = { AppRepo.openProgram(context, p.intentUri, p.pkg) },
                        )
                    }
                }
            }
        }

        if (showContent && moving == null) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .offset { IntOffset(0, contentTop.roundToPx()) }
                    .graphicsLayer { alpha = contentAlpha }
            ) {
                val ch = appChannel
                RowLabel(if (ch != null) channelLabel(ch, shownApp) else source?.label.orEmpty(), Modifier.padding(start = 48.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 48.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    if (ch != null) {
                        items(ch.programs.take(30), key = { it.key }) { p ->
                            ProgramCard(
                                p = p,
                                badge = null,
                                onFocused = { focusedProgram = p; area = Area.Content },
                                onClick = { AppRepo.openProgram(context, p.intentUri, p.pkg) },
                            )
                        }
                    } else {
                        items(titles.take(24), key = { it.id }) { t ->
                            TitleCard(
                                t = t,
                                onFocused = { focusedTitle = t; area = Area.Content },
                                onClick = { AppRepo.openTitle(context, t, source?.open ?: "com.nuvio.tv") },
                            )
                        }
                    }
                }
            }
        }

        val menuApp = menuFor
        if (menuApp != null) {
            val visible = pinned.map { it.packageName }
            AppMenu(
                app = menuApp,
                actions = listOf(
                    MenuAction("Move") {
                        menuFor = null
                        movingPkg = menuApp.packageName
                        focusApp(menuApp.packageName)
                    },
                    MenuAction("Remove from Home") {
                        menuFor = null
                        val idx = visible.indexOf(menuApp.packageName)
                        val list = visible - menuApp.packageName
                        onPinnedChange(list)
                        focusApp(list.getOrNull(idx.coerceAtMost(list.lastIndex)) ?: ALL_APPS_KEY)
                    },
                    MenuAction("App info") {
                        menuFor = null
                        focusApp(menuApp.packageName)
                        AppRepo.openAppInfo(context, menuApp.packageName)
                    },
                ),
                onDismiss = {
                    menuFor = null
                    focusApp(menuApp.packageName)
                },
            )
        }
    }
}

@Composable
private fun RowLabel(text: String, modifier: Modifier) {
    BasicText(
        text,
        style = TextStyle(color = TextHi, fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
        modifier = modifier,
    )
}

@Composable
private fun Backdrop(url: String?, spotlight: TvApp?) {
    val context = LocalContext.current
    var shown by remember { mutableStateOf<Any?>(null) }
    LaunchedEffect(url, spotlight) {
        if (spotlight != null) {
            shown = spotlight
            return@LaunchedEffect
        }
        if (url == null) return@LaunchedEffect
        val result = context.imageLoader.execute(backdropRequest(context, url))
        if (result is SuccessResult) shown = url
    }
    Crossfade(targetState = shown, animationSpec = tween(800), label = "backdrop") { key ->
        when (key) {
            is String -> {
                val zoom = remember(key) { Animatable(1f) }
                LaunchedEffect(key) { zoom.animateTo(1.07f, tween(16000, easing = LinearEasing)) }
                AsyncImage(
                    model = backdropRequest(context, key),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.TopCenter,
                    modifier = Modifier.fillMaxSize().graphicsLayer {
                        scaleX = zoom.value
                        scaleY = zoom.value
                        transformOrigin = TransformOrigin(0.75f, 0.35f)
                    },
                )
            }
            is TvApp -> AmbientBackdrop(key)
            else -> Box(Modifier.fillMaxSize())
        }
    }
}

/** For apps with no catalog: a soft glow in the app's colours. */
@Composable
private fun AmbientBackdrop(app: TvApp) {
    Box(Modifier.fillMaxSize().background(Ink)) {
        val ambient = app.ambient
        if (ambient != null) {
            Image(
                ambient,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                filterQuality = FilterQuality.High,
                alpha = 0.5f,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.radialGradient(
                    listOf(app.accent.copy(alpha = 0.42f), Color.Transparent),
                    center = Offset(1440f, 400f),
                    radius = 1000f,
                )
            )
        )
    }
}

/** The app's real banner, floating on the right. Drawn above the scrims so it stays crisp. */
@Composable
private fun SpotlightCard(app: TvApp, modifier: Modifier) {
    val banner = app.banner ?: return
    val shape = RoundedCornerShape(20.dp)
    Image(
        banner,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        filterQuality = FilterQuality.High,
        modifier = modifier
            .size(320.dp, 180.dp)
            .shadow(36.dp, shape, ambientColor = app.accent, spotColor = app.accent)
            .clip(shape),
    )
}

@Composable
private fun Scrims() {
    Box(
        Modifier.fillMaxSize().background(
            Brush.horizontalGradient(
                0f to Ink.copy(alpha = 0.94f),
                0.26f to Ink.copy(alpha = 0.78f),
                0.46f to Ink.copy(alpha = 0.32f),
                0.66f to Color.Transparent,
            )
        )
    )
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                0f to Ink.copy(alpha = 0.55f),
                0.16f to Color.Transparent,
                0.48f to Color.Transparent,
                0.64f to Ink.copy(alpha = 0.72f),
                0.8f to Ink.copy(alpha = 0.95f),
                1f to Ink,
            )
        )
    )
}

@Composable
private fun Eyebrow(app: TvApp?, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        val icon = app?.icon
        if (icon != null) {
            Image(icon, contentDescription = null, modifier = Modifier.size(20.dp).clip(RoundedCornerShape(5.dp)))
            Spacer(Modifier.width(8.dp))
        }
        BasicText(
            text.uppercase(),
            style = TextStyle(color = TextMid, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.8.sp),
        )
    }
}

@Composable
private fun SpotlightHero(app: TvApp, modifier: Modifier) {
    Column(modifier.width(470.dp)) {
        Eyebrow(app, "App")
        Spacer(Modifier.height(14.dp))
        Box(Modifier.size(420.dp, 108.dp), contentAlignment = Alignment.BottomStart) {
            BasicText(
                app.label,
                style = TextStyle(color = TextHi, fontSize = 56.sp, fontWeight = FontWeight.Black, letterSpacing = (-1.2).sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            KeyCap("OK")
            Spacer(Modifier.width(10.dp))
            BasicText(
                "Open ${app.label}",
                style = TextStyle(color = TextMid, fontSize = 14.sp, fontWeight = FontWeight.Medium),
            )
        }
    }
}

@Composable
private fun KeyCap(text: String) {
    Box(
        Modifier
            .height(22.dp)
            .border(1.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
            .padding(horizontal = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(text, style = TextStyle(color = TextHi, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp))
    }
}

@Composable
private fun Hero(
    title: Title?,
    label: String,
    app: TvApp?,
    showIndicator: Boolean,
    count: Int,
    index: Int,
    progress: () -> Float,
    expanded: Boolean,
    actionLabel: String?,
    actionIcon: ImageBitmap?,
    focusRequester: FocusRequester,
    onFocused: () -> Unit,
    onOpen: () -> Unit,
    onStep: (Int) -> Unit,
    onExit: () -> Unit,
    modifier: Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    // Only focusable while there's a title to open; otherwise Up keeps its old job.
    val focus = if (actionLabel != null) {
        Modifier
            .focusRequester(focusRequester)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .onKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (e.key) {
                    Key.DirectionLeft, Key.DirectionRight -> {
                        if (e.nativeKeyEvent.repeatCount == 0) onStep(if (e.key == Key.DirectionLeft) -1 else 1)
                        true
                    }
                    Key.DirectionDown -> {
                        onExit()
                        true
                    }
                    else -> false
                }
            }
            .remoteClick(onClick = onOpen)
            .focusable()
    } else {
        Modifier
    }
    val lineHeight = with(LocalDensity.current) { 19.sp.toDp() }
    val actionAlpha by animateFloatAsState(if (expanded) 1f else 0f, tween(320, delayMillis = 90), label = "heroAction")
    Column(modifier.width(if (expanded) 600.dp else 470.dp).then(focus)) {
        Eyebrow(app, label)
        Spacer(Modifier.height(14.dp))
        Crossfade(targetState = title, animationSpec = tween(600), label = "hero") { t ->
            Column {
                Box(Modifier.size(400.dp, 108.dp), contentAlignment = Alignment.BottomStart) {
                    if (t?.logo != null) {
                        AsyncImage(
                            model = Img.logo(t.logo),
                            contentDescription = t.name,
                            contentScale = ContentScale.Fit,
                            alignment = Alignment.BottomStart,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else if (t != null) {
                        BasicText(
                            t.name,
                            style = TextStyle(color = TextHi, fontSize = 44.sp, fontWeight = FontWeight.Black, lineHeight = 46.sp),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                if (t != null) MetaRow(t)
                Spacer(Modifier.height(10.dp))
                val lines = if (expanded) 3 else 2
                BasicText(
                    t?.description ?: "",
                    style = TextStyle(color = Color(0xFFC9CCD3), fontSize = 13.sp, lineHeight = 19.sp),
                    maxLines = lines,
                    overflow = TextOverflow.Ellipsis,
                    // Fixed height so the button and indicator don't hop around between titles.
                    modifier = Modifier.width(420.dp).height(lineHeight * lines),
                )
            }
        }
        if (expanded && actionLabel != null) {
            Spacer(Modifier.height(22.dp))
            Row(Modifier.graphicsLayer { alpha = actionAlpha }, verticalAlignment = Alignment.CenterVertically) {
                HeroButton(actionLabel, actionIcon, focused)
                if (showIndicator) {
                    Spacer(Modifier.width(24.dp))
                    Indicator(count, index, progress)
                    Spacer(Modifier.width(20.dp))
                    KeyCap("← →")
                    Spacer(Modifier.width(8.dp))
                    BasicText("Browse", style = TextStyle(color = TextMid, fontSize = 13.sp, fontWeight = FontWeight.Medium))
                }
            }
        } else {
            Spacer(Modifier.height(16.dp))
            if (showIndicator) Indicator(count, index, progress)
        }
    }
}

@Composable
private fun HeroButton(label: String, icon: ImageBitmap?, focused: Boolean) {
    val shape = RoundedCornerShape(21.dp)
    val scale by animateFloatAsState(if (focused) 1f else 0.96f, tween(170), label = "heroButton")
    Row(
        Modifier
            .height(42.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale; transformOrigin = TransformOrigin(0f, 0.5f) }
            .clip(shape)
            .background(if (focused) TextHi else Color.White.copy(alpha = 0.14f))
            .border(1.dp, Color.White.copy(alpha = if (focused) 0f else 0.22f), shape)
            .padding(start = if (icon != null) 9.dp else 20.dp, end = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Image(icon, contentDescription = null, modifier = Modifier.size(24.dp).clip(RoundedCornerShape(7.dp)))
            Spacer(Modifier.width(10.dp))
        }
        BasicText(
            label,
            style = TextStyle(color = if (focused) Ink else TextHi, fontSize = 15.sp, fontWeight = FontWeight.Bold),
            maxLines = 1,
        )
    }
}

@Composable
private fun MetaRow(t: Title) {
    val parts = buildList {
        t.year?.let { add(it) }
        t.runtime?.let { add(it) }
        if (t.genres.isNotEmpty()) add(t.genres.take(3).joinToString(" · "))
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        val rating = t.rating
        if (rating != null) {
            BasicText("★", style = TextStyle(color = Amber, fontSize = 13.sp))
            Spacer(Modifier.width(4.dp))
            BasicText(rating, style = TextStyle(color = TextHi, fontSize = 13.sp, fontWeight = FontWeight.Bold))
            if (parts.isNotEmpty()) Dot()
        }
        parts.forEachIndexed { i, p ->
            if (i > 0) Dot()
            BasicText(p, style = TextStyle(color = TextMid, fontSize = 13.sp, fontWeight = FontWeight.Medium))
        }
    }
}

@Composable
private fun Dot() {
    Box(Modifier.padding(horizontal = 8.dp).size(3.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.45f)))
}

@Composable
private fun Indicator(count: Int, index: Int, progress: () -> Float) {
    Canvas(Modifier.width((count * 26 + (count - 1) * 5).dp).height(3.dp)) {
        val seg = 26.dp.toPx()
        val gap = 5.dp.toPx()
        val h = size.height
        val p = progress()
        for (i in 0 until count) {
            val x = i * (seg + gap)
            drawRoundRect(Color.White.copy(alpha = 0.22f), Offset(x, 0f), Size(seg, h), CornerRadius(h / 2))
            val f = when {
                i < index -> 1f
                i == index -> p
                else -> 0f
            }
            if (f > 0f) drawRoundRect(Color.White, Offset(x, 0f), Size(seg * f, h), CornerRadius(h / 2))
        }
    }
}

@Composable
private fun TopBar(onAllApps: () -> Unit, onDown: () -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    Row(
        modifier.onPreviewKeyEvent { e ->
            if (e.key != Key.DirectionDown) return@onPreviewKeyEvent false
            if (e.type == KeyEventType.KeyDown) onDown()
            true
        },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        RoundButton(onClick = onAllApps) { c -> GridGlyph(c) }
        RoundButton(onClick = { AppRepo.openSettings(context) }) { c -> GearGlyph(c) }
        Spacer(Modifier.width(8.dp))
        Clock()
    }
}

@Composable
private fun RoundButton(onClick: () -> Unit, glyph: @Composable (Color) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.12f else 1f, tween(150), label = "round")
    Box(
        Modifier
            .size(34.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .onFocusChanged { focused = it.isFocused }
            .remoteClick(onClick = onClick)
            .focusable()
            .clip(CircleShape)
            .background(if (focused) TextHi else Color.White.copy(alpha = 0.10f))
            .border(1.dp, Color.White.copy(alpha = if (focused) 0f else 0.16f), CircleShape),
        contentAlignment = Alignment.Center,
    ) { glyph(if (focused) Ink else TextHi) }
}

@Composable
private fun GridGlyph(color: Color) {
    Canvas(Modifier.size(15.dp)) {
        val g = size.width * 0.14f
        val cell = (size.width - g) / 2f
        for (r in 0..1) for (c in 0..1) {
            drawRoundRect(
                color,
                topLeft = Offset(c * (cell + g), r * (cell + g)),
                size = Size(cell, cell),
                cornerRadius = CornerRadius(cell * 0.28f),
                style = Stroke(width = size.width * 0.1f),
            )
        }
    }
}

@Composable
private fun GearGlyph(color: Color) {
    Canvas(Modifier.size(16.dp)) {
        val r = size.minDimension / 2f
        for (i in 0 until 8) {
            rotate(i * 45f) {
                drawRoundRect(
                    color,
                    topLeft = Offset(center.x - r * 0.15f, center.y - r),
                    size = Size(r * 0.3f, r * 0.42f),
                    cornerRadius = CornerRadius(r * 0.08f),
                )
            }
        }
        drawCircle(color, radius = r * 0.56f, center = center, style = Stroke(width = r * 0.3f))
    }
}

@Composable
private fun Clock() {
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
    val date = SimpleDateFormat("EEE · MMM d", Locale.getDefault()).format(now).uppercase()
    Column(horizontalAlignment = Alignment.End) {
        Row {
            BasicText(
                time,
                style = TextStyle(color = TextHi, fontSize = 30.sp, fontWeight = FontWeight.Light, letterSpacing = (-0.6).sp),
            )
            if (ampm.isNotEmpty()) {
                BasicText(
                    ampm,
                    style = TextStyle(color = Color(0xFFC7CAD1), fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                    modifier = Modifier.padding(start = 3.dp, top = 5.dp),
                )
            }
        }
        BasicText(date, style = TextStyle(color = TextLo, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp))
    }
}

@Composable
private fun ArrowBubble(text: String) {
    Box(
        Modifier.size(22.dp).clip(CircleShape).background(Ink.copy(alpha = 0.75f)),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(text, style = TextStyle(color = Amber, fontSize = 13.sp, fontWeight = FontWeight.Bold))
    }
}

@Composable
private fun AppTile(
    app: TvApp,
    width: Dp,
    height: Dp,
    focusRequester: FocusRequester?,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    moving: Boolean = false,
    badge: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        when {
            moving -> 1.14f
            focused -> 1.1f
            else -> 1f
        },
        tween(170),
        label = "tile",
    )
    val shape = RoundedCornerShape(10.dp)
    Box(
        Modifier
            .size(width, height)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(
                elevation = if (focused || moving) 22.dp else 3.dp,
                shape = shape,
                clip = false,
                ambientColor = app.accent,
                spotColor = if (focused || moving) app.accent else Color.Black,
            )
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .remoteClick(onLongClick = onLongClick, onClick = onClick)
            .focusable()
            .clip(shape)
            .background(TileBase)
    ) {
        val banner = app.banner
        val icon = app.icon
        if (banner != null) {
            Image(banner, contentDescription = app.label, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(app.accent.copy(alpha = 0.55f), TileBase))))
            Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Image(icon, contentDescription = null, modifier = Modifier.size(height * 0.5f).clip(RoundedCornerShape(8.dp)))
                    Spacer(Modifier.width(10.dp))
                }
                BasicText(
                    app.label,
                    style = TextStyle(color = TextHi, fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (badge) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(TextHi)
                    .border(2.dp, Ink, CircleShape)
            )
        }
        if (moving) {
            Row(
                Modifier.fillMaxSize().padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ArrowBubble("‹")
                ArrowBubble("›")
            }
            Box(Modifier.fillMaxSize().border(3.dp, Amber, shape))
        } else if (focused) {
            Box(Modifier.fillMaxSize().border(2.dp, Color.White, shape))
        }
    }
}

@Composable
private fun AllAppsTile(focusRequester: FocusRequester, onFocused: () -> Unit, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.1f else 1f, tween(170), label = "allTile")
    val shape = RoundedCornerShape(10.dp)
    Box(
        Modifier
            .size(100.dp, 77.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .focusRequester(focusRequester)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .remoteClick(onClick = onClick)
            .focusable()
            .clip(shape)
            .background(if (focused) TextHi else Color.White.copy(alpha = 0.08f))
            .border(1.dp, Color.White.copy(alpha = 0.16f), shape),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            GridGlyph(if (focused) Ink else TextHi)
            Spacer(Modifier.height(8.dp))
            BasicText(
                "All apps",
                style = TextStyle(color = if (focused) Ink else TextMid, fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
            )
        }
    }
}

@Composable
private fun TitleCard(t: Title, onFocused: () -> Unit, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.08f else 1f, tween(170), label = "card")
    val shape = RoundedCornerShape(10.dp)
    Box(
        Modifier
            .size(208.dp, 117.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(if (focused) 18.dp else 3.dp, shape, clip = false)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .remoteClick(onClick = onClick)
            .focusable()
            .clip(shape)
            .background(TileBase)
    ) {
        AsyncImage(
            model = Img.card(t.background),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.35f to Color.Transparent, 1f to Ink.copy(alpha = 0.9f))))
        if (t.logo != null) {
            AsyncImage(
                model = Img.logo(t.logo),
                contentDescription = t.name,
                contentScale = ContentScale.Fit,
                alignment = Alignment.BottomStart,
                modifier = Modifier.align(Alignment.BottomStart).padding(10.dp).size(128.dp, 38.dp),
            )
        } else {
            BasicText(
                t.name,
                style = TextStyle(color = TextHi, fontSize = 13.sp, fontWeight = FontWeight.Bold),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.BottomStart).padding(10.dp),
            )
        }
        if (focused) Box(Modifier.fillMaxSize().border(2.dp, Color.White, shape))
    }
}

@Composable
private fun ProgramCard(p: Program, badge: ImageBitmap?, onFocused: () -> Unit, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.08f else 1f, tween(170), label = "program")
    val shape = RoundedCornerShape(10.dp)
    val wide = p.aspect >= 1.3f
    Box(
        Modifier
            .size((117f * p.aspect).coerceIn(78f, 208f).dp, 117.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(if (focused) 18.dp else 3.dp, shape, clip = false)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .remoteClick(onClick = onClick)
            .focusable()
            .clip(shape)
            .background(TileBase)
    ) {
        AsyncImage(
            model = p.image,
            contentDescription = p.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        // Posters carry their own title art; only landscape cards get a caption.
        if (wide) {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.3f to Color.Transparent, 1f to Ink.copy(alpha = 0.92f))))
            Column(Modifier.align(Alignment.BottomStart).padding(start = 10.dp, end = 10.dp, bottom = 12.dp)) {
                BasicText(
                    p.title,
                    style = TextStyle(color = TextHi, fontSize = 13.sp, fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val sub = p.subtitle
                if (sub != null) {
                    BasicText(
                        sub,
                        style = TextStyle(color = Color(0xFFC7CAD1), fontSize = 10.sp, fontWeight = FontWeight.Medium),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (badge != null) {
            Image(
                badge,
                contentDescription = null,
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp).size(18.dp).clip(RoundedCornerShape(5.dp)),
            )
        }
        if (p.progress > 0f) {
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(Color.White.copy(alpha = 0.2f)))
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(p.progress).height(3.dp).background(Color.White))
        }
        if (focused) Box(Modifier.fillMaxSize().border(2.dp, Color.White, shape))
    }
}

private class MenuAction(val label: String, val run: () -> Unit)

/** Hold-OK menu. It owns the remote while open, so focus can't wander off behind it. */
@Composable
private fun AppMenu(app: TvApp, actions: List<MenuAction>, onDismiss: () -> Unit) {
    var sel by remember(app) { mutableIntStateOf(0) }
    var armed by remember(app) { mutableStateOf(false) }
    val fr = remember { FocusRequester() }
    BackHandler(onBack = onDismiss)
    LaunchedEffect(app) {
        repeat(20) {
            delay(30)
            if (runCatching { fr.requestFocus() }.isSuccess) return@LaunchedEffect
        }
    }
    Box(Modifier.fillMaxSize().background(Ink.copy(alpha = 0.72f)), contentAlignment = Alignment.Center) {
        val shape = RoundedCornerShape(20.dp)
        Column(
            Modifier
                .width(300.dp)
                .shadow(40.dp, shape, clip = false, ambientColor = app.accent, spotColor = app.accent)
                .clip(shape)
                .background(Color(0xFF14161D))
                .border(1.dp, Color.White.copy(alpha = 0.10f), shape)
                .focusRequester(fr)
                .onKeyEvent { e ->
                    when {
                        e.type == KeyEventType.KeyDown && e.key == Key.DirectionUp -> sel = (sel - 1).coerceAtLeast(0)
                        e.type == KeyEventType.KeyDown && e.key == Key.DirectionDown -> sel = (sel + 1).coerceAtMost(actions.lastIndex)
                        isSelectKey(e) && e.type == KeyEventType.KeyDown && e.nativeKeyEvent.repeatCount == 0 -> armed = true
                        isSelectKey(e) && e.type == KeyEventType.KeyUp && armed -> {
                            armed = false
                            actions[sel].run()
                        }
                        e.key == Key.Back && e.type == KeyEventType.KeyUp -> onDismiss()
                    }
                    true
                }
                .focusable()
                .padding(12.dp)
        ) {
            Row(
                Modifier.padding(start = 6.dp, end = 6.dp, top = 4.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val banner = app.banner
                val icon = app.icon
                if (banner != null) {
                    Image(banner, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(64.dp, 36.dp).clip(RoundedCornerShape(6.dp)))
                } else if (icon != null) {
                    Image(icon, contentDescription = null, modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)))
                }
                Spacer(Modifier.width(12.dp))
                BasicText(
                    app.label,
                    style = TextStyle(color = TextHi, fontSize = 16.sp, fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            actions.forEachIndexed { i, a ->
                val on = i == sel
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (on) TextHi else Color.Transparent)
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    BasicText(
                        a.label,
                        style = TextStyle(color = if (on) Ink else TextMid, fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                    )
                }
            }
        }
    }
}

@Composable
private fun AllAppsScreen(
    apps: List<TvApp>,
    pinnedPkgs: List<String>,
    onPinnedChange: (List<String>) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val requesters = remember { HashMap<String, FocusRequester>() }
    var menuFor by remember { mutableStateOf<TvApp?>(null) }
    var pendingFocus by remember { mutableStateOf(apps.firstOrNull()?.packageName) }
    val onHome = pinnedPkgs.filter { p -> apps.any { it.packageName == p } }
    BackHandler(enabled = menuFor == null, onBack = onBack)
    LaunchedEffect(pendingFocus) {
        val target = pendingFocus ?: return@LaunchedEffect
        repeat(20) {
            delay(30)
            val fr = requesters[target]
            if (fr != null && runCatching { fr.requestFocus() }.isSuccess) {
                pendingFocus = null
                return@LaunchedEffect
            }
        }
        pendingFocus = null
    }
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF0D0F16), Ink)))) {
        Column(Modifier.fillMaxSize().padding(top = 30.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 48.dp), verticalAlignment = Alignment.Bottom) {
                BasicText("All apps", style = TextStyle(color = TextHi, fontSize = 28.sp, fontWeight = FontWeight.Bold))
                Spacer(Modifier.width(12.dp))
                BasicText(
                    "Hold OK on an app to add it to Home or remove it",
                    style = TextStyle(color = TextLo, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                    modifier = Modifier.padding(bottom = 5.dp),
                )
                Spacer(Modifier.weight(1f))
                Clock()
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                contentPadding = PaddingValues(start = 48.dp, end = 48.dp, top = 22.dp, bottom = 40.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                gridItems(apps, key = { it.packageName }) { app ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        AppTile(
                            app = app,
                            width = 156.dp,
                            height = 88.dp,
                            focusRequester = requesters.getOrPut(app.packageName) { FocusRequester() },
                            onFocused = {},
                            onClick = { AppRepo.launch(context, app.packageName) },
                            onLongClick = { menuFor = app },
                            badge = app.packageName in onHome,
                        )
                        Spacer(Modifier.height(8.dp))
                        BasicText(
                            app.label,
                            style = TextStyle(color = TextLo, fontSize = 11.sp, fontWeight = FontWeight.Medium),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        val menuApp = menuFor
        if (menuApp != null) {
            val pkg = menuApp.packageName
            val isOnHome = pkg in onHome
            AppMenu(
                app = menuApp,
                actions = listOf(
                    MenuAction(if (isOnHome) "Remove from Home" else "Add to Home") {
                        menuFor = null
                        onPinnedChange(if (isOnHome) onHome - pkg else onHome + pkg)
                        pendingFocus = pkg
                    },
                    MenuAction("App info") {
                        menuFor = null
                        pendingFocus = pkg
                        AppRepo.openAppInfo(context, pkg)
                    },
                ),
                onDismiss = {
                    menuFor = null
                    pendingFocus = pkg
                },
            )
        }
    }
}
