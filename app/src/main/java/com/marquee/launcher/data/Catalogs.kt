package com.marquee.launcher.data

import android.content.Context
import com.marquee.launcher.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class Title(
    val id: String,
    val type: String,
    val name: String,
    val background: String?,
    val logo: String?,
    val year: String?,
    val runtime: String?,
    val rating: String?,
    val genres: List<String>,
    val description: String?,
)

/**
 * Where an app's preview comes from. `paths` are AIOMetadata catalog paths or full URLs;
 * `open` is the app a title opens in.
 */
data class Source(val label: String, val paths: List<String>, val open: String = "com.nuvio.tv")

/** TMDB serves any width; ask for what the screen needs instead of the 4K originals. */
object Img {
    fun backdrop(url: String?) = url?.replace("/t/p/original/", "/t/p/w1280/")
    fun card(url: String?) = url?.replace("/t/p/original/", "/t/p/w780/")
    fun logo(url: String?) = url?.replace("/t/p/original/", "/t/p/w500/")
}

/** Catalogs per app, cached on disk so Home paints instantly. Apps without one get a spotlight instead. */
object Catalogs {
    /**
     * Optional: an AIOMetadata addon URL (https://.../stremio/<config-id>) for richer, per-app catalogs.
     * Set `marquee.aiometadata=` in local.properties (never committed); the catalog IDs below are the
     * ones defined in that addon's manifest.json. Without it, Nuvio and Stremio show Cinemeta's
     * public "popular" lists and other apps show their own banner.
     */
    private val AIOMETADATA = BuildConfig.AIOMETADATA_URL.trimEnd('/')
    private val BASE = "$AIOMETADATA/catalog/"
    private const val CINEMETA = "https://cinemeta-catalogs.strem.io/top/catalog/"
    private const val MAX_AGE_MS = 3L * 60 * 60 * 1000
    private const val MEMORY_TTL_MS = 30L * 60 * 1000

    private val popular = listOf("${CINEMETA}series/top.json", "${CINEMETA}movie/top.json")

    private val byPackage: Map<String, Source> = if (AIOMETADATA.isEmpty()) mapOf(
        "com.nuvio.tv" to Source("Popular right now", popular),
        "com.stremio.one" to Source("Popular on Stremio", popular, open = "com.stremio.one"),
    ) else mapOf(
        "com.nuvio.tv" to
            Source("Trending on Nuvio", listOf("movie/mdblist.87667", "series/mdblist.88434")),
        "com.stremio.one" to
            Source("Popular on Stremio", popular, open = "com.stremio.one"),
        "com.amazon.amazonvideo.livingroom" to
            Source(
                "New on Prime Video",
                listOf("series/mdblist.86753", "movie/mdblist.86755"),
                open = "com.amazon.amazonvideo.livingroom",
            ),
        // Shows only: Paramount+ can open a show's page from a link, but not a single movie.
        "com.cbs.ott" to
            Source("New on Paramount+", listOf("series/mdblist.86761"), open = "com.cbs.ott"),
        "com.netflix.ninja" to
            Source("New on Netflix", listOf("series/mdblist.86751", "movie/mdblist.88328")),
        "com.crunchyroll.crunchyroid" to
            Source("New on Crunchyroll", listOf("series/streaming.cru_series", "movie/streaming.cru_movie")),
    )

    private class Entry(val titles: List<Title>, val at: Long)

    private var dir: File? = null
    private val memory = HashMap<List<String>, Entry>()

    fun init(context: Context) {
        dir = File(context.cacheDir, "catalogs").apply { mkdirs() }
    }

    /** Null means this app has no catalog: Home shows the app itself instead. */
    fun sourceFor(pkg: String?): Source? = pkg?.let { byPackage[it] }

    fun cached(source: Source): List<Title>? = memory[source.paths]?.titles

    fun isStale(source: Source): Boolean =
        memory[source.paths]?.let { System.currentTimeMillis() - it.at > MEMORY_TTL_MS } ?: true

    suspend fun load(source: Source): List<Title> {
        val old = memory[source.paths]
        if (old != null && System.currentTimeMillis() - old.at < MEMORY_TTL_MS) return old.titles
        val lists = withContext(Dispatchers.IO) { source.paths.map { fetch(it) } }
        val merged = interleave(lists).distinctBy { it.id }
        if (merged.isEmpty()) return old?.titles ?: emptyList()
        memory[source.paths] = Entry(merged, System.currentTimeMillis())
        return merged
    }

    private fun fetch(path: String): List<Title> {
        val url = if (path.startsWith("http")) path else "$BASE$path.json"
        val file = dir?.let { File(it, path.replace(Regex("[^A-Za-z0-9]+"), "_") + ".json") }
        val fresh = file != null && file.exists() &&
            System.currentTimeMillis() - file.lastModified() < MAX_AGE_MS
        val body: String? = if (fresh) {
            readOrNull(file)
        } else {
            val downloaded = download(url)
            if (downloaded != null) runCatching { file?.writeText(downloaded) }
            downloaded ?: readOrNull(file)
        }
        return if (body == null) emptyList() else parseList(body)
    }

    private fun readOrNull(f: File?): String? =
        runCatching { f?.takeIf { it.exists() }?.readText() }.getOrNull()

    private fun download(url: String): String? = runCatching {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 20000
        c.instanceFollowRedirects = true
        c.setRequestProperty("Accept", "application/json")
        try {
            if (c.responseCode != 200) null else c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }.getOrNull()

    private fun interleave(lists: List<List<Title>>): List<Title> {
        val out = ArrayList<Title>()
        val max = lists.maxOfOrNull { it.size } ?: 0
        for (i in 0 until max) for (l in lists) if (i < l.size) out += l[i]
        return out
    }

    private fun parseList(body: String): List<Title> = runCatching {
        val arr = JSONObject(body).optJSONArray("metas") ?: JSONArray()
        (0 until arr.length()).mapNotNull { i -> parse(arr.optJSONObject(i)) }
    }.getOrDefault(emptyList())

    private fun JSONObject.str(key: String): String? =
        if (!has(key) || isNull(key)) null
        else optString(key).trim().takeIf { it.isNotEmpty() && it != "null" }

    private fun parse(o: JSONObject?): Title? {
        if (o == null) return null
        val name = o.str("name") ?: return null
        val imdb = o.str("imdb_id")?.takeIf { it.startsWith("tt") }
        val id = imdb ?: o.str("id") ?: return null
        val genreArr = o.optJSONArray("genres") ?: o.optJSONArray("genre")
        val genres = genreArr?.let { a ->
            (0 until a.length()).mapNotNull { idx -> a.optString(idx).takeIf { g -> g.isNotBlank() } }
        } ?: emptyList()
        return Title(
            id = id,
            type = o.str("type") ?: "movie",
            name = name,
            background = o.str("background")?.takeIf { it.startsWith("http") },
            logo = o.str("logo")?.takeIf { it.startsWith("http") },
            year = (o.str("releaseInfo") ?: o.str("year"))?.replace('-', '–'),
            runtime = o.str("runtime")?.let { prettyRuntime(it) },
            rating = o.str("imdbRating")?.takeIf { it != "0" && it != "0.0" },
            genres = genres,
            description = o.str("description"),
        )
    }

    /** "2h53min", "47min", "120 min" -> "2h 53m", "47m", "2h". */
    private fun prettyRuntime(r: String): String? {
        val h = Regex("(\\d+)\\s*h").find(r)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val m = Regex("(\\d+)\\s*m").find(r)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val total = h * 60 + m
        if (total <= 0) return null
        if (total < 60) return "${total}m"
        val rest = total % 60
        return if (rest == 0) "${total / 60}h" else "${total / 60}h ${rest}m"
    }
}
