package kr.donghaeng.car

import kr.donghaeng.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI

class MapApi(base: String, private val token: String) {
    val baseUrl = base.trim().trimEnd('/')
    init {
        require(token.none { it.code < 32 || it.code == 127 }) { "서버 연결 토큰에 줄바꿈이나 제어 문자를 사용할 수 없습니다" }
        val uri = URI(baseUrl)
        require(uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null && uri.path.orEmpty().isEmpty()) { "서버 주소는 경로 없이 입력하세요" }
        require(uri.scheme == "https" || (BuildConfig.DEBUG && uri.scheme == "http" && uri.host in listOf("localhost", "127.0.0.1"))) { "HTTPS 서버 또는 debug용 localhost 주소를 입력하세요" }
        require(!uri.host.isNullOrBlank())
    }
    private suspend fun post(path: String, data: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        val connection = URI(baseUrl + path).toURL().openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"; connection.doOutput = true
            connection.instanceFollowRedirects = false; connection.connectTimeout = 10000; connection.readTimeout = 115000
            connection.setRequestProperty("Content-Type", "application/json")
            if (token.isNotBlank()) connection.setRequestProperty("Authorization", "Bearer $token")
            connection.outputStream.use { it.write(data.toString().toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            if (status !in 200..299) {
                val message = connection.errorStream?.bufferedReader()?.use { it.readText().take(1000) }
                val safe = runCatching { JSONObject(message ?: "{}").optString("error") }.getOrDefault("")
                error(safe.ifBlank { "지도 서버 응답 $status" })
            }
            JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        } finally { connection.disconnect() }
    }
    suspend fun search(query: String, center: GeoPoint? = null): List<LivePlace> {
        val data = JSONObject().put("query", query)
        center?.let { data.put("center", pointJson(it)) }
        return parsePlaces(post("/search", data))
    }
    suspend fun route(origin: GeoPoint, destination: GeoPoint, priority: String, waypoint: GeoPoint? = null): DrivingRoute {
        val data = JSONObject().put("origin", pointJson(origin)).put("destination", pointJson(destination)).put("priority", priority)
        waypoint?.let { data.put("waypoint", pointJson(it)) }
        val result = post("/route", data)
        val points = result.getJSONArray("points")
        return DrivingRoute((0 until points.length()).map { val p = points.getJSONObject(it); GeoPoint(p.getDouble("lat"), p.getDouble("lon")) }, result.getInt("distance"), result.getInt("duration"), result.getString("priority"))
    }
    suspend fun along(query: String, route: DrivingRoute): List<LivePlace> {
        val stride = ((route.points.size + 3999) / 4000).coerceAtLeast(1)
        val sampled = route.points.filterIndexed { index, _ -> index % stride == 0 }.let { if (it.last() != route.points.last()) it + route.points.last() else it }
        return parsePlaces(post("/near-route", JSONObject().put("query", query).put("points", JSONArray().apply { sampled.forEach { put(pointJson(it)) } })))
    }
    private fun parsePlaces(data: JSONObject): List<LivePlace> {
        val a = data.getJSONArray("places")
        return (0 until a.length()).map { i -> val p = a.getJSONObject(i)
            LivePlace(p.getString("id"), p.getString("name"), GeoPoint(p.getDouble("lat"), p.getDouble("lon")), p.optString("address"), p.optString("phone"), p.optString("category"), p.optString("url"), if (p.has("offset")) p.getInt("offset") else null)
        }
    }
    companion object { fun pointJson(p: GeoPoint) = JSONObject().put("lat", p.lat).put("lon", p.lon) }
}
