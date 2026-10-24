package kr.donghaeng.car

import kr.donghaeng.core.Place

interface NavigationGateway {
    fun connect()
    fun addStop(place: Place)
    fun close()
}
