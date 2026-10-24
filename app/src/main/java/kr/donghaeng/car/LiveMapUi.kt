@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package kr.donghaeng.car

import android.annotation.SuppressLint
import android.webkit.WebResourceRequest
import android.webkit.WebResourceError
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kr.donghaeng.core.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI

@Composable
fun LivePlacesPanel(m: DonghaengModel, locate: () -> Unit) {
    val p = m.planner
    var originQuery by remember { mutableStateOf("") }
    var destinationQuery by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf<LivePlace?>(null) }
    var sendDestination by remember { mutableStateOf(false) }
    Text("실제 장소·자동차 경로", fontSize = 22.sp)
    if (!m.consentMap) {
        Text("MY → 지도 서비스에서 서버 주소를 설정하고 위치·검색어 처리에 동의해 주세요. API 키가 없으면 실제 지도와 장소 결과를 표시할 수 없습니다.")
        Button(onClick = { m.tab = 4 }) { Text("지도 연결 설정") }
    } else {
        if (m.editingAllowed) {
            OutlinedTextField(originQuery, { originQuery = it.take(100) }, label = { Text("출발지 이름 또는 주소") }, modifier = Modifier.fillMaxWidth())
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { p.search(originQuery, true) }, enabled = !p.busy && originQuery.isNotBlank()) { Text("출발지 검색") }
                Button(onClick = locate, enabled = !p.busy) { Text("GPS를 출발지로") }
            }
            OutlinedTextField(destinationQuery, { destinationQuery = it.take(100) }, label = { Text("목적지 이름 또는 주소") }, modifier = Modifier.fillMaxWidth())
            Button(onClick = { p.search(destinationQuery, false) }, enabled = !p.busy && destinationQuery.isNotBlank()) { Text("목적지 검색") }
        }
        p.searchResults.forEach { place ->
            OutlinedButton(onClick = { p.pick(place) }, enabled = m.editingAllowed && !p.busy, modifier = Modifier.fillMaxWidth()) {
                Text("${place.name}\n${place.address}")
            }
        }
        Text("출발: ${p.origin?.name ?: "미선택"}  →  도착: ${p.destination?.name ?: "미선택"}")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("DISTANCE" to "최단 거리", "TIME" to "최단 시간", "RECOMMEND" to "추천 경로").forEach { (value, label) ->
                FilterChip(p.priority == value, { p.selectPriority(value) }, enabled = !p.busy && m.editingAllowed, label = { Text(label) })
            }
            Button(onClick = { p.plan() }, enabled = !p.busy && p.origin != null && p.destination != null && m.editingAllowed) { Text("경로 조회") }
            OutlinedButton(onClick = { sendDestination = true }, enabled = p.destination != null && !p.busy && m.editingAllowed) { Text("차량에 목적지 요청") }
        }
        Text(p.status, color = MaterialTheme.colorScheme.primary)
        if (p.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        p.displayedRoute?.let { route -> Text("경로 ${(route.distance / 1000.0).let { "%.1f".format(it) }}km · 약 ${(route.duration + 59) / 60}분\n교통 상황에 따른 예상치 · 실시간 턴바이턴 안내는 차량 내비게이션 이용") }
        val server = runCatching { MapApi(m.mapServer, "").baseUrl }.getOrNull()
        if (server != null) KakaoMap(server, p, interactive = m.editingAllowed)
        else Text("유효한 서버 주소를 MY에서 설정하세요")
        if (m.editingAllowed) OutlinedTextField(p.query, { p.query = it.take(100) }, label = { Text("경로 주변 장소: 휴게소·공중화장실·애견동반 카페 등") }, modifier = Modifier.fillMaxWidth())
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("휴게소", "졸음쉼터", "공중화장실", "애견동반 카페", "반려견 공원", "동물병원").forEach { query ->
                OutlinedButton(onClick = { p.searchAlong(query) }, enabled = p.baseRoute != null && !p.busy) { Text(query) }
            }
            Button(onClick = { p.searchAlong() }, enabled = p.baseRoute != null && !p.busy && p.query.isNotBlank()) { Text("이 경로 주변 검색") }
        }
        Text("가족의 필요 조건: ${m.required.joinToString { it.label }.ifBlank { "없음" }}\n아래는 실제 장소 후보이며 시설 조건 충족을 확인한 추천 목록은 아닙니다. 기저귀 교환대·수유실·반려동물 출입·운영시간은 장소에 직접 확인하세요.", fontSize = 14.sp)
        p.previewStop?.let { stop ->
            val baseline = p.baseRoute; val preview = p.previewRoute
            if (baseline != null && preview != null) {
                Text("${stop.name} 경유 시 예상 추가 ${(preview.duration - baseline.duration).coerceAtLeast(0).let { (it + 59) / 60 }}분 / ${"%.1f".format((preview.distance - baseline.distance).coerceAtLeast(0) / 1000.0)}km\n체류시간 제외 · 조회 시각의 교통 상황에 따라 달라집니다", color = MaterialTheme.colorScheme.primary)
            }
        }
        p.nearby.forEachIndexed { i, place ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${i+1}. ${place.name}", fontSize = 20.sp)
                    Text(place.address); Text(place.category)
                    place.offset?.let { Text("지도상 경로에서 약 ${it}m · 실제 진입 방향은 미확인") }
                    Text("출처: 카카오 로컬 · 시설/반려동물 동반/진료 가능 여부 미확인", fontSize = 13.sp)
                    if (place.phone.isNotBlank()) Text("문의: ${place.phone}")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { p.preview(place) }, enabled = !p.busy) { Text("경유 경로·추가 시간 보기") }
                        OutlinedButton(onClick = { confirm = place }, enabled = !p.busy && m.editingAllowed) { Text("차량 경유지 요청") }
                    }
                }
            }
        }
    }
    confirm?.let { place -> AlertDialog(onDismissRequest = { confirm = null }, title = { Text("실제 장소를 경유지로 요청할까요?") }, text = {
        Text("${place.name}\n시설과 반려동물 출입 여부를 확인하세요. 차량의 첫 번째 경유지가 변경될 수 있습니다. 카카오 경로와 차량 경로는 다를 수 있습니다.")
    }, confirmButton = { TextButton(onClick = { if (m.editingAllowed) m.choose(place.toPlace()); confirm = null }) { Text("요청") } }, dismissButton = { TextButton(onClick = { confirm = null }) { Text("취소") } }) }
    if (sendDestination) AlertDialog(onDismissRequest = { sendDestination = false }, title = { Text("차량 목적지를 변경할까요?") }, text = { Text("${p.destination?.name}\n차량 내비게이션의 기존 경로가 바뀔 수 있습니다. 앱에서 계산한 카카오 경로와 동일한 경로를 보장하지 않습니다.") },
        confirmButton = { TextButton(onClick = { if (m.editingAllowed) p.destination?.let { m.nav.setDestination(it.toPlace()) }; sendDestination = false }) { Text("목적지 요청") } }, dismissButton = { TextButton(onClick = { sendDestination = false }) { Text("취소") } })
}

@SuppressLint("SetJavaScriptEnabled")
@Composable private fun KakaoMap(server: String, p: RoutePlanner, interactive: Boolean) {
    var web by remember(server) { mutableStateOf<WebView?>(null) }
    var ready by remember(server) { mutableStateOf(false) }
    var error by remember(server) { mutableStateOf("") }
    val payload = JSONObject().apply {
        p.origin?.let { put("origin", MapApi.pointJson(it.point)) }; p.destination?.let { put("destination", MapApi.pointJson(it.point)) }
        put("route", JSONArray().apply { p.displayedRoute?.points?.forEach { put(MapApi.pointJson(it)) } })
        put("places", JSONArray().apply { p.nearby.forEach { put(MapApi.pointJson(it.point).put("name", it.name)) } })
        put("label", "파란 선: 자동차 경로 · 마커: 실제 장소 / 시설 미확인")
    }.toString()
    LaunchedEffect(ready, payload, interactive) {
        if (ready) web?.evaluateJavascript("window.renderDonghaeng && window.renderDonghaeng($payload);", null)
    }
    key(server) {
        AndroidView(modifier = Modifier.fillMaxWidth().height(400.dp), factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true; settings.domStorageEnabled = true
                settings.allowFileAccess = false; settings.allowContentAccess = false
                settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                setOnTouchListener { _, _ -> !interactive }
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = request.url.toString() != "$server/map"
                    override fun onPageFinished(view: WebView, url: String) { if (url == "$server/map") ready = true }
                    override fun onReceivedError(view: WebView, request: WebResourceRequest, e: WebResourceError) { if (request.isForMainFrame) error = "지도 연결 실패: 서버 실행·adb reverse·MY의 주소를 확인하세요" }
                }
                web = this; loadUrl("$server/map")
            }
        }, update = { view -> view.setOnTouchListener { _, _ -> !interactive } })
    }
    DisposableEffect(server) { onDispose { web?.stopLoading(); web?.destroy(); web = null } }
    if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
}
