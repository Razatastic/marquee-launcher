package com.marquee.launcher.data

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.Settings
import android.util.DisplayMetrics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.palette.graphics.Palette

/** One launchable TV app, with its real banner and icon pulled from the app itself. */
class TvApp(
    val packageName: String,
    val label: String,
    val banner: ImageBitmap?,
    val icon: ImageBitmap?,
    /** A tiny copy of the banner; stretched full-screen it becomes a soft ambient glow. */
    val ambient: ImageBitmap?,
    val accent: Color,
)

object AppRepo {
    /** Home row order. Anything not installed is skipped. */
    val defaultPinned = listOf(
        "com.nuvio.tv",
        "org.smarttube.stable",
        "com.amazon.amazonvideo.livingroom",
        "com.cbs.ott",
        "com.google.android.youtube.tv",
        "com.stremio.one",
    )

    private fun leanbackQuery() =
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)

    /** Cheap check used on every resume to see whether apps were installed or removed. */
    fun packages(context: Context): List<String> =
        context.packageManager.queryIntentActivities(leanbackQuery(), 0)
            .map { it.activityInfo.packageName }
            .filter { it != context.packageName }
            .distinct()
            .sorted()

    fun loadAll(context: Context): List<TvApp> {
        val pm = context.packageManager
        return pm.queryIntentActivities(leanbackQuery(), 0)
            .map { it.activityInfo }
            .filter { it.packageName != context.packageName }
            .distinctBy { it.packageName }
            .map { build(pm, it) }
            .sortedBy { it.label.lowercase() }
    }

    private fun build(pm: PackageManager, ai: ActivityInfo): TvApp {
        val label = ai.loadLabel(pm).toString()
        val bannerRes = if (ai.banner != 0) ai.banner else ai.applicationInfo.banner
        val bannerDrawable = hiRes(pm, ai, bannerRes) ?: runCatching { ai.loadBanner(pm) }.getOrNull()
        val iconDrawable = hiRes(pm, ai, ai.iconResource) ?: runCatching { ai.loadIcon(pm) }.getOrNull()
        val banner = bannerDrawable?.let { render(it, 640, 360) }
        val icon = iconDrawable?.let { render(it, 192, 192) }
        val ambient = (banner ?: icon)?.let { Bitmap.createScaledBitmap(it, 32, 18, true) }
        val accent = (icon ?: banner)?.let { bmp ->
            val p = Palette.from(bmp).maximumColorCount(16).generate()
            (p.vibrantSwatch ?: p.dominantSwatch)?.rgb?.let { Color(it) }
        } ?: Color(0xFF8E7BFF)
        return TvApp(
            ai.packageName,
            label,
            banner?.asImageBitmap(),
            icon?.asImageBitmap(),
            ambient?.asImageBitmap(),
            accent,
        )
    }

    /** Ask the app's own resources for its sharpest version of a drawable. */
    private fun hiRes(pm: PackageManager, ai: ActivityInfo, resId: Int): Drawable? {
        if (resId == 0) return null
        return runCatching {
            pm.getResourcesForApplication(ai.applicationInfo)
                .getDrawableForDensity(resId, DisplayMetrics.DENSITY_XXXHIGH, null)
        }.getOrNull()
    }

    private fun render(d: Drawable, maxW: Int, maxH: Int): Bitmap {
        val iw = if (d.intrinsicWidth > 0) d.intrinsicWidth else maxW
        val ih = if (d.intrinsicHeight > 0) d.intrinsicHeight else maxH
        val scale = minOf(maxW.toFloat() / iw, maxH.toFloat() / ih, 1f)
        val w = (iw * scale).toInt().coerceAtLeast(1)
        val h = (ih * scale).toInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        d.setBounds(0, 0, w, h)
        d.draw(Canvas(bmp))
        return bmp
    }

    fun launch(context: Context, pkg: String) {
        val pm = context.packageManager
        val intent = pm.getLeanbackLaunchIntentForPackage(pkg)
            ?: pm.getLaunchIntentForPackage(pkg)
            ?: return
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        runCatching { context.startActivity(intent) }
    }

    fun openSettings(context: Context) {
        runCatching {
            context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    fun openAppInfo(context: Context, pkg: String) {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", pkg, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    /** Open a card from an app's home-screen row using the link that app stored with it. */
    fun openProgram(context: Context, intentUri: String?, fallbackPkg: String) {
        val intent = intentUri?.let { runCatching { Intent.parseUri(it, Intent.URI_INTENT_SCHEME) }.getOrNull() }
        if (intent == null) {
            launch(context, fallbackPkg)
            return
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { context.startActivity(intent) }.isFailure) launch(context, fallbackPkg)
    }

    /** Open a title in [pkg], as close to its own page as that app allows. Falls back to just opening the app. */
    fun openTitle(context: Context, t: Title, pkg: String) {
        val link = when (pkg) {
            "com.nuvio.tv" -> "nuvio://detail/${t.type}/${t.id}"
            "com.stremio.one" -> "stremio:///detail/${t.type}/${t.id}"
            // Prime Video only links to titles by Amazon's own ids; its search puts the title first.
            "com.amazon.amazonvideo.livingroom" -> "https://app.primevideo.com/search?phrase=" + Uri.encode(t.name)
            // The referral tag makes Paramount+ continue to the show after its "Who's watching?" screen.
            "com.cbs.ott" ->
                if (t.type == "series") "pplus://www.paramountplus.com/shows/${showSlug(t.name)}/?searchReferral=AndroidTV_Paramount%2B"
                else null
            else -> null
        }
        if (link == null) {
            launch(context, pkg)
            return
        }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link))
            .setPackage(pkg)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val ok = runCatching { context.startActivity(intent) }.isSuccess
        if (!ok) launch(context, pkg)
    }

    /** "Georgie & Mandy's First Marriage" -> "georgie-and-mandys-first-marriage", the way Paramount+ names its pages. */
    private fun showSlug(name: String): String =
        java.text.Normalizer.normalize(name, java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase()
            .replace("&", " and ")
            .replace(Regex("['’]"), "")
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
}
