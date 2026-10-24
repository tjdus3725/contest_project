package kr.donghaeng.car

import android.Manifest
import android.car.Car
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import kr.donghaeng.core.PhoneNumbers
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val model: DonghaengModel by viewModels()
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var car: Car? = null
    private var uxManager: CarUxRestrictionsManager? = null
    private var locationManager: LocationManager? = null
    private val handler = Handler(Looper.getMainLooper())
    private val stopLocation: Runnable = Runnable {
        locationManager?.removeUpdates(locationListener)
        if (model.locationText.startsWith("현재 위치 수신 중")) model.locationText = "30초 내 위치 수신 실패 · 주소나 주변 표지판을 직접 알려주세요"
    }
    private val locationListener: LocationListener = LocationListener { loc ->
        val age = ((System.currentTimeMillis() - loc.time) / 1000).coerceAtLeast(0)
        val time = SimpleDateFormat("HH:mm:ss", Locale.KOREA).format(Date(loc.time))
        model.locationText = "위도 %.5f · 경도 %.5f\n정확도 약 %.0fm · %s 수신 · %d초 전".format(Locale.KOREA, loc.latitude, loc.longitude, loc.accuracy, time, age)
        locationManager?.removeUpdates(locationListener)
        handler.removeCallbacks(stopLocation)
    }
    private val speechResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let(model::query)
        } else model.notice = "음성 입력이 취소되었거나 인식되지 않았습니다"
    }
    private val microphone = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchSpeech() else model.notice = "마이크 권한이 없습니다. 상황 버튼을 사용해 주세요."
    }
    private val locationPermission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants.values.any { it }) locate() else model.notice = "위치 권한이 없습니다. 신고할 때 주변 표지판과 주소를 직접 알려주세요."
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale.KOREA)
                ttsReady = result != null && result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
            }
        }
        connectVehicle()
        setContent { DonghaengApp(model, ::voice, ::speak, ::dial, ::locate) }
    }
    private fun connectVehicle() {
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) {
            model.uxRestricted = false; model.uxStatus = "일반 Android · 차량 상태 미연결"; return
        }
        model.uxRestricted = true
        if (android.os.Build.VERSION.SDK_INT < 29) { model.uxStatus = "차량 상태 미확인 · 편집 잠금"; return }
        try {
            car = Car.createCar(this, handler, Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT) { connectedCar, ready ->
                runOnUiThread {
                    if (!ready) { model.uxRestricted = true; model.uxStatus = "차량 연결 끊김 · 편집 잠금" }
                    else try {
                        uxManager = connectedCar.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as CarUxRestrictionsManager
                        fun apply(restricted: Boolean) { model.uxRestricted = restricted; model.uxStatus = if (restricted) "주행 제한 적용 · 편집 잠금" else "차량 편집 허용 상태" }
                        apply(uxManager!!.currentCarUxRestrictions.isRequiresDistractionOptimization)
                        uxManager!!.registerListener { state -> runOnUiThread { apply(state.isRequiresDistractionOptimization) } }
                    } catch (e: Exception) { model.uxRestricted = true; model.uxStatus = "차량 상태 읽기 실패 · 편집 잠금" }
                }
            }
        } catch (e: Exception) { model.uxRestricted = true; model.uxStatus = "차량 상태 미확인 · 편집 잠금" }
    }
    private fun voice() {
        if (!model.consentSpeech) { model.notice = "MY에서 음성 인식 서비스 사용에 동의해 주세요. 서비스 제공자가 음성을 처리할 수 있습니다."; return }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) microphone.launch(Manifest.permission.RECORD_AUDIO)
        else launchSpeech()
    }
    private fun launchSpeech() {
        try {
            speechResult.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR").putExtra(RecognizerIntent.EXTRA_PROMPT, "필요한 곳이나 현재 상황을 말씀해 주세요"))
        } catch (e: Exception) { model.notice = "음성 인식 서비스가 설치되지 않았습니다. 화면의 상황 버튼을 사용해 주세요." }
    }
    private fun speak(text: String) {
        if (!ttsReady) { model.notice = "한국어 음성 엔진이 준비되지 않았습니다. 화면 안내를 확인해 주세요."; return }
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "donghaeng-guide")
    }
    private fun dial(raw: String) {
        val number = PhoneNumbers.clean(raw)
        if (number == null) { model.notice = "유효한 연락처를 먼저 등록해 주세요"; return }
        tts?.stop()
        if (BuildConfig.FLAVOR == "demo") { model.notice = "시연: $number 전화 화면 요청 · 실제 전화하지 않습니다"; return }
        try { startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))); model.notice = "전화 화면 요청 · 통화 연결 여부는 확인할 수 없습니다" }
        catch (e: Exception) { model.notice = "차량에서 전화를 열 수 없습니다. 연결된 휴대전화로 $number 에 전화하세요." }
    }
    private fun locate() {
        if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            locationPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)); return
        }
        model.locationText = "현재 위치 수신 중 · 수신되지 않으면 주변 주소·표지판을 확인하세요"
        try {
            locationManager = getSystemService(LocationManager::class.java)
            val providers = locationManager!!.getProviders(true).filter { it == LocationManager.GPS_PROVIDER || it == LocationManager.NETWORK_PROVIDER }
            if (providers.isEmpty()) { model.locationText = "위치 제공자가 꺼져 있습니다 · 기기 위치 설정을 확인하세요"; return }
            locationManager!!.removeUpdates(locationListener)
            handler.removeCallbacks(stopLocation)
            providers.forEach { locationManager!!.requestLocationUpdates(it, 1000, 0f, locationListener, Looper.getMainLooper()) }
            handler.postDelayed(stopLocation, 30000)
        } catch (e: Exception) { model.locationText = "위치 수신 실패 · 주변 주소·표지판을 직접 확인하세요" }
    }
    override fun onStop() { super.onStop(); locationManager?.removeUpdates(locationListener); handler.removeCallbacksAndMessages(null); tts?.stop() }
    override fun onDestroy() {
        runCatching { uxManager?.unregisterListener(); car?.disconnect() }
        tts?.shutdown(); super.onDestroy()
    }
}
