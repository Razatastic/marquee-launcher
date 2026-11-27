package com.marquee.launcher.data

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.media.tv.TvContract
import android.provider.BaseColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One card from an app's Android TV home-screen row. */
class Program(
    val key: String,
    val pkg: String,
    val title: String,
    val subtitle: String?,
    val image: String?,
    /** width / height of [image] */
    val aspect: Float,
    val progress: Float,
    val intentUri: String?,
)

class Channel(val pkg: String, val name: String, val programs: List<Program>)

/**
 * The rows apps publish for the Android TV home screen (Nuvio's and Stremio's Continue Watching,
 * Prime Video's and Paramount+'s picks, ...). Reading them needs the "TV listings" permission;
 * without it this quietly returns nothing.
 */
object TvChannels {
    private const val NUVIO = "com.nuvio.tv"
    private const val STREMIO = "com.stremio.one"
    /** These show their catalog row instead of their app channel. */
    private val catalogFirst = setOf(NUVIO, STREMIO)
    /** YouTube apps get no row: their home-screen rows are mostly noise. */
    private val noRow = setOf("org.smarttube.stable", "com.google.android.youtube.tv")

    suspend fun load(context: Context): List<Channel> = withContext(Dispatchers.IO) {
        runCatching { query(context.contentResolver) }.getOrDefault(emptyList())
    }

    /** Nuvio first, then Stremio; one card per title. */
    fun continueWatching(channels: List<Channel>): List<Program> =
        listOf(NUVIO, STREMIO)
            .flatMap { p -> channels.filter { it.pkg == p && it.name.contains("continue", ignoreCase = true) } }
            .flatMap { it.programs }
            .distinctBy { it.title.lowercase() }
            .take(20)

    /** The row to show under an app when it's focused, if that app publishes one. */
    fun rowFor(channels: List<Channel>, pkg: String?): Channel? {
        if (pkg == null || pkg in catalogFirst || pkg in noRow) return null
        return channels.firstOrNull { it.pkg == pkg }
    }

    private fun query(cr: ContentResolver): List<Channel> {
        val out = ArrayList<Channel>()
        cr.query(
            TvContract.Channels.CONTENT_URI,
            arrayOf(BaseColumns._ID, TvContract.Channels.COLUMN_PACKAGE_NAME, TvContract.Channels.COLUMN_DISPLAY_NAME),
            null, null, null,
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val pkg = c.getString(1) ?: continue
                val programs = programs(cr, id, pkg)
                if (programs.isNotEmpty()) out += Channel(pkg, c.getString(2).orEmpty(), programs)
            }
        }
        return out
    }

    private fun programs(cr: ContentResolver, channelId: Long, pkg: String): List<Program> {
        val rows = ArrayList<Pair<Int, Program>>()
        cr.query(TvContract.buildPreviewProgramsUriForChannel(channelId), PROJECTION, null, null, null)?.use { c ->
            var i = 0
            while (c.moveToNext()) {
                val p = toProgram(c, "$channelId:${i++}", pkg) ?: continue
                rows += (c.int(TvContract.PreviewPrograms.COLUMN_WEIGHT) ?: 0) to p
            }
        }
        return rows.sortedByDescending { it.first }.map { it.second }
    }

    private val PROJECTION = arrayOf(
        TvContract.PreviewPrograms.COLUMN_TITLE,
        TvContract.PreviewPrograms.COLUMN_EPISODE_TITLE,
        TvContract.PreviewPrograms.COLUMN_SEASON_DISPLAY_NUMBER,
        TvContract.PreviewPrograms.COLUMN_EPISODE_DISPLAY_NUMBER,
        TvContract.PreviewPrograms.COLUMN_POSTER_ART_URI,
        TvContract.PreviewPrograms.COLUMN_POSTER_ART_ASPECT_RATIO,
        TvContract.PreviewPrograms.COLUMN_THUMBNAIL_URI,
        TvContract.PreviewPrograms.COLUMN_THUMBNAIL_ASPECT_RATIO,
        TvContract.PreviewPrograms.COLUMN_INTENT_URI,
        TvContract.PreviewPrograms.COLUMN_LAST_PLAYBACK_POSITION_MILLIS,
        TvContract.PreviewPrograms.COLUMN_DURATION_MILLIS,
        TvContract.PreviewPrograms.COLUMN_WEIGHT,
    )

    private fun Cursor.str(col: String): String? {
        val i = getColumnIndex(col)
        return if (i < 0 || isNull(i)) null else getString(i)?.takeIf { it.isNotBlank() }
    }

    private fun Cursor.long(col: String): Long? {
        val i = getColumnIndex(col)
        return if (i < 0 || isNull(i)) null else getLong(i)
    }

    private fun Cursor.int(col: String): Int? {
        val i = getColumnIndex(col)
        return if (i < 0 || isNull(i)) null else getInt(i)
    }

    /** TvContract aspect-ratio codes -> width / height. */
    private fun ratio(code: Int?): Float = when (code) {
        1 -> 1.5f
        2 -> 4f / 3f
        3 -> 1f
        4 -> 2f / 3f
        5 -> 1f / 1.441f
        else -> 16f / 9f
    }

    private fun toProgram(c: Cursor, key: String, pkg: String): Program? {
        val title = c.str(TvContract.PreviewPrograms.COLUMN_TITLE) ?: return null
        val season = c.str(TvContract.PreviewPrograms.COLUMN_SEASON_DISPLAY_NUMBER)
        val episode = c.str(TvContract.PreviewPrograms.COLUMN_EPISODE_DISPLAY_NUMBER)
        val pos = c.long(TvContract.PreviewPrograms.COLUMN_LAST_PLAYBACK_POSITION_MILLIS) ?: 0L
        val dur = c.long(TvContract.PreviewPrograms.COLUMN_DURATION_MILLIS) ?: 0L
        val parts = ArrayList<String>()
        if (season != null && episode != null) parts += "S$season · E$episode"
        else c.str(TvContract.PreviewPrograms.COLUMN_EPISODE_TITLE)?.let { parts += it }
        if (dur > 0 && pos in 1 until dur) parts += left(dur - pos)
        val thumb = c.str(TvContract.PreviewPrograms.COLUMN_THUMBNAIL_URI)
        val poster = c.str(TvContract.PreviewPrograms.COLUMN_POSTER_ART_URI)
        val image = thumb ?: poster
        val aspect = if (thumb != null) ratio(c.int(TvContract.PreviewPrograms.COLUMN_THUMBNAIL_ASPECT_RATIO))
        else ratio(c.int(TvContract.PreviewPrograms.COLUMN_POSTER_ART_ASPECT_RATIO))
        return Program(
            key = key,
            pkg = pkg,
            title = title,
            subtitle = parts.joinToString(" · ").ifEmpty { null },
            image = image,
            aspect = aspect,
            progress = if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f,
            intentUri = c.str(TvContract.PreviewPrograms.COLUMN_INTENT_URI),
        )
    }

    /** Debug aid: logs every row and card apps publish (adb logcat -s MarqueeDump). */
    fun dump(context: Context) {
        val cr = context.contentResolver
        fun row(c: Cursor) = (0 until c.columnCount).mapNotNull { i ->
            if (c.isNull(i) || c.getType(i) == Cursor.FIELD_TYPE_BLOB) null else "${c.getColumnName(i)}=${c.getString(i)}"
        }.joinToString(" | ")
        runCatching {
            cr.query(TvContract.Channels.CONTENT_URI, null, null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(c.getColumnIndexOrThrow(BaseColumns._ID))
                    android.util.Log.i("MarqueeDump", "CHANNEL " + row(c))
                    cr.query(TvContract.buildPreviewProgramsUriForChannel(id), null, null, null, null)?.use { p ->
                        while (p.moveToNext()) android.util.Log.i("MarqueeDump", "  PROGRAM " + row(p))
                    }
                }
            }
        }.onFailure { android.util.Log.e("MarqueeDump", "dump failed", it) }
    }

    private fun left(ms: Long): String {
        val min = (ms / 60_000L).toInt().coerceAtLeast(1)
        return if (min < 60) "$min min left" else "${min / 60}h ${min % 60}m left"
    }
}
