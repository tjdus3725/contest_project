package kr.donghaeng.car

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.graphics.asAndroidBitmap
import android.graphics.Bitmap
import java.io.File
import org.junit.Rule
import org.junit.Test

class NavigationTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private fun screenshot(name: String) {
        rule.waitForIdle()
        val folder = File(rule.activity.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        File(folder, "$name.png").outputStream().use {
            rule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
    @Test fun allFiveTabsRemainReachable() {
        screenshot("01-home")
        rule.onNodeWithText("장소 찾기", useUnmergedTree = true).performClick()
        rule.onNodeWithText("우리 가족에게 맞는 다음 쉼").assertIsDisplayed()
        screenshot("02-places")
        rule.onNodeWithText("SOS", useUnmergedTree = true).performClick()
        rule.onNodeWithText("지금, 도움이 필요한가요?").assertIsDisplayed()
        screenshot("03-sos")
        rule.onNodeWithText("가족", useUnmergedTree = true).performClick()
        rule.onNodeWithText("함께 타는 가족").assertIsDisplayed()
        screenshot("04-family")
        rule.onNodeWithText("MY", useUnmergedTree = true).performClick()
        rule.onNodeWithText("MY · 이동 설정").assertIsDisplayed()
        screenshot("05-settings")
        rule.onNodeWithText("홈", useUnmergedTree = true).performClick()
        rule.onNodeWithText("함께 가는 길, 조금 더 편안하게").assertIsDisplayed()
    }
    @Test fun emergencyDialIsImmediatelyAvailable() {
        rule.onNodeWithText("SOS", useUnmergedTree = true).performClick()
        rule.onNodeWithText(" 119 구조·구급").assertIsDisplayed().performClick()
        rule.onNodeWithText("시연: 119 전화 화면 요청 · 실제 전화하지 않습니다").assertIsDisplayed()
    }
}
