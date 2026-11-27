package com.marquee.launcher.space

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt

/**
 * Current conditions from Open-Meteo (free, no account). The place comes from the TV's own time zone
 * (America/New_York -> New York), so nothing about you or your network is looked up anywhere.
 */
object Weather {
    class Now(val temp: Int, val label: String)

    suspend fun now(context: Context): Now? = withContext(Dispatchers.IO) {
        runCatching {
            val (lat, lon) = place(context)
            val fahrenheit = Locale.getDefault().country in setOf("US", "LR", "MM")
            val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                "&current=temperature_2m,weather_code" + if (fahrenheit) "&temperature_unit=fahrenheit" else ""
            val cur = JSONObject(get(url)).getJSONObject("current")
            Now(cur.getDouble("temperature_2m").roundToInt(), label(cur.getInt("weather_code")))
        }.getOrNull()
    }

    private fun place(context: Context): Pair<Double, Double> {
        val prefs = context.getSharedPreferences("marquee", Context.MODE_PRIVATE)
        val zone = TimeZone.getDefault().id
        if (prefs.getString("wx_zone", null) == zone && prefs.contains("wx_lat")) {
            return prefs.getFloat("wx_lat", 0f).toDouble() to prefs.getFloat("wx_lon", 0f).toDouble()
        }
        val city = zone.substringAfterLast('/').replace('_', ' ')
        val geo = JSONObject(
            get("https://geocoding-api.open-meteo.com/v1/search?count=1&language=en&format=json&name=" +
                URLEncoder.encode(city, "UTF-8"))
        ).getJSONArray("results").getJSONObject(0)
        val lat = geo.getDouble("latitude")
        val lon = geo.getDouble("longitude")
        prefs.edit().putString("wx_zone", zone).putFloat("wx_lat", lat.toFloat()).putFloat("wx_lon", lon.toFloat()).apply()
        return lat to lon
    }

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 8000
        return try {
            c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    /** WMO weather codes, as Open-Meteo reports them. */
    private fun label(code: Int): String = when (code) {
        0 -> "Clear"
        1 -> "Mostly clear"
        2 -> "Partly cloudy"
        3 -> "Cloudy"
        45, 48 -> "Fog"
        in 51..57 -> "Drizzle"
        in 61..67 -> "Rain"
        in 71..77 -> "Snow"
        in 80..82 -> "Showers"
        85, 86 -> "Snow showers"
        in 95..99 -> "Thunderstorms"
        else -> ""
    }
}
