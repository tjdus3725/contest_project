package kr.donghaeng.car

import android.content.Context
import android.os.Handler
import android.os.Looper
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.WaypointIndex
import ai.pleos.playground.navi.data.RequestWaypointInfo
import ai.pleos.playground.navi.data.WaypointInfo
import kr.donghaeng.core.Place

class PlatformNavigation(context: Context, private val status: (String) -> Unit) : NavigationGateway {
    private val handler = Handler(Looper.getMainLooper())
    private val helper = NaviHelper(context.applicationContext)
    private var connected = false
    private var pending = false
    private val timeout = Runnable { if (pending) { pending = false; status("응답 시간 초과 · 경유지 등록 여부 미확인. 차량 내비게이션을 확인하세요.") } }
    private fun report(message: String) { handler.post { status(message) } }
    private val listener = object : NaviHelperEventListener {
        override fun onError(errorCode: NaviErrorCode) {
            pending = false
            handler.removeCallbacks(timeout)
            report("내비게이션 오류: $errorCode · 등록 여부를 확인하세요")
        }
        override fun onWayPointInfo(waypointInfo: List<WaypointInfo>) {
            // SDK notifications can be unsolicited. Do not equate any event to our request succeeding.
            if (pending) report("경유지 정보 수신 · 실제 반영은 차량 내비게이션에서 확인하세요")
        }
    }
    override fun connect() {
        try {
            helper.initialize(onConnected = {
                connected = true
                helper.addListener(listener)
                report("Pleos 연결됨 · 차량 내비게이션에서 목적지를 먼저 설정하세요")
            }, onFailed = { report("Pleos 연결 실패 · 차량 환경과 권한을 확인하세요") })
        } catch (e: Exception) { report("Pleos 연결 불가 · ${e.javaClass.simpleName}") }
    }
    override fun addStop(place: Place) {
        if (place.demo) { report("가상 장소는 실제 내비게이션에 전송하지 않습니다"); return }
        if (!connected) { report("내비게이션 미연결 · 경유지 등록 안 됨"); return }
        if (pending) { report("이전 요청 확인 중 · 중복 전송하지 않았습니다"); return }
        try {
            pending = true
            helper.addWaypoint(RequestWaypointInfo(latitude = place.latitude, longitude = place.longitude,
                poiName = place.name, waypointIndex = WaypointIndex.FIRST, poiId = null, poiSubId = null, address = null))
            report("경유지 요청 전송 · 반영 여부 확인 중")
            handler.postDelayed(timeout, 15000)
            helper.getWaypointInfo()
        } catch (e: Exception) { pending = false; report("경유지 요청 실패 · ${e.javaClass.simpleName}") }
    }
    override fun close() {
        handler.removeCallbacksAndMessages(null)
        runCatching { helper.removeListener(listener); helper.release() }
        connected = false
    }
}
