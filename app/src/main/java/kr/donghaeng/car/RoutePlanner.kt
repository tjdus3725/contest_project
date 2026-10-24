package kr.donghaeng.car

import androidx.compose.runtime.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kr.donghaeng.core.*

class RoutePlanner(private val scope: CoroutineScope, private val api: () -> MapApi) {
    var origin by mutableStateOf<LivePlace?>(null); private set
    var destination by mutableStateOf<LivePlace?>(null); private set
    var priority by mutableStateOf("DISTANCE"); private set
    var baseRoute by mutableStateOf<DrivingRoute?>(null); private set
    var previewRoute by mutableStateOf<DrivingRoute?>(null); private set
    var previewStop by mutableStateOf<LivePlace?>(null); private set
    var searchResults by mutableStateOf(emptyList<LivePlace>()); private set
    var nearby by mutableStateOf(emptyList<LivePlace>()); private set
    var searchForOrigin by mutableStateOf(false); private set
    var busy by mutableStateOf(false); private set
    var status by mutableStateOf("서버와 지도 사용 동의 설정 후 출발지·목적지를 검색하세요"); private set
    var query by mutableStateOf("휴게소")
    private var generation = 0
    private var job: Job? = null
    val displayedRoute get() = previewRoute ?: baseRoute
    private fun invalidate() { generation++; job?.cancel(); busy = false }
    private fun resetRoute() { baseRoute = null; previewRoute = null; previewStop = null; nearby = emptyList() }
    fun reset() { invalidate(); origin = null; destination = null; resetRoute(); searchResults = emptyList(); status = "이동 경로 정보가 초기화되었습니다" }
    fun cancelForConfiguration() { invalidate(); resetRoute(); searchResults = emptyList(); status = "연결 설정이 변경되었습니다. 경로를 다시 조회하세요" }
    private fun run(label: String, block: suspend (MapApi) -> Unit) {
        if (busy) return
        busy = true; status = label
        val epoch = generation
        job = scope.launch {
            try { block(api()) }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { if (epoch == generation) status = e.message?.take(220) ?: "지도 서비스 연결 실패" }
            finally { if (epoch == generation) busy = false }
        }
    }
    fun search(text: String, forOrigin: Boolean) {
        searchForOrigin = forOrigin; searchResults = emptyList()
        run("실제 장소 검색 중…") { service ->
            searchResults = service.search(text)
            status = if (searchResults.isEmpty()) "검색 결과가 없습니다. 주소나 지역명을 함께 입력하세요" else "${searchResults.size}개 검색됨 · ${if(forOrigin) "출발지" else "목적지"}를 선택하세요"
        }
    }
    fun pick(place: LivePlace) {
        invalidate(); resetRoute()
        if (searchForOrigin) origin = place else destination = place
        searchResults = emptyList(); status = "출발지와 목적지 확인 후 경로 조회를 누르세요"
    }
    fun useLocation(point: GeoPoint) {
        invalidate(); resetRoute(); searchResults = emptyList()
        origin = LivePlace("gps", "수신한 현재 위치", point)
        status = "GPS 수신 위치를 출발지로 설정했습니다. 실제 위치를 확인하세요"
    }
    fun selectPriority(value: String) { invalidate(); priority = value; resetRoute(); status = "경로 기준이 변경되었습니다. 경로를 다시 조회하세요" }
    fun plan() {
        val start = origin ?: return; val end = destination ?: return
        resetRoute(); nearby = emptyList()
        run("자동차 경로 계산 중…") { service ->
            baseRoute = service.route(start.point, end.point, priority)
            status = "경로 표시 완료 · 카카오 API 경로이며 차량 내비게이션 경로와 다를 수 있습니다"
        }
    }
    fun searchAlong(text: String = query) {
        val route = baseRoute ?: run { status = "먼저 출발지와 목적지의 경로를 조회하세요"; return }
        query = text; nearby = emptyList(); previewRoute = null; previewStop = null
        run("경로 주변의 실제 장소 검색 중…") { service ->
            nearby = service.along(text, route)
            status = "${nearby.size}개 후보 · 최대 8개 지점의 부분 검색입니다. 반대편 도로·시설·반려동물 동반 여부는 별도 확인하세요"
        }
    }
    fun preview(stop: LivePlace) {
        val start = origin ?: return; val end = destination ?: return
        previewRoute = null; previewStop = null
        run("정차 장소를 포함한 경로 계산 중…") { service ->
            previewRoute = service.route(start.point, end.point, priority, stop.point)
            previewStop = stop
            status = "${stop.name} 경유 미리보기 · 차량 내비게이션에는 아직 전송하지 않았습니다"
        }
    }
}
