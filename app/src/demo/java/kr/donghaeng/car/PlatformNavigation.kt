package kr.donghaeng.car

import android.content.Context
import kr.donghaeng.core.Place

class PlatformNavigation(context: Context, private val status: (String) -> Unit) : NavigationGateway {
    override fun connect() { status("시연 연결 · 실제 경로 변경 없음") }
    override fun addStop(place: Place) { status("시연: ${place.name} 선택됨 · 실제 경유지 미등록") }
    override fun close() = Unit
}
