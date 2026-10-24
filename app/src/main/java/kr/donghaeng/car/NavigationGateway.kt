package kr.donghaeng.car

import kr.donghaeng.core.Place

interface NavigationGateway {
    fun connect()
    fun addStop(place: Place)
    fun setDestination(place: Place)
    fun close()
}
