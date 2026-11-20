/*
 * Neiro — a BitChord fork. GNU General Public License v3.0 or later.
 */
package com.music.bitchord.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope

internal const val TARGET_PACKAGE = "com.music.bitchord"

internal fun MacrobenchmarkScope.coldStartToHome() {
    pressHome()
    startActivityAndWait()
    device.waitForIdle()
    Thread.sleep(3_000)
}

internal fun MacrobenchmarkScope.firstSecondsJourney() {
    val w = device.displayWidth
    val h = device.displayHeight
    val cx = w / 2
    repeat(4) {
        device.swipe(cx, h * 7 / 10, cx, h * 3 / 10, 20)
        device.waitForIdle()
    }
    repeat(2) {
        device.swipe(cx, h * 3 / 10, cx, h * 7 / 10, 20)
        device.waitForIdle()
    }
    val barY = h * 93 / 100
    device.swipe(w / 8, barY, w * 7 / 8, barY, 40)
    device.waitForIdle()
    for (i in 0 until 4) {
        device.click(w * (2 * i + 1) / 8, barY)
        device.waitForIdle()
        Thread.sleep(600)
    }
    device.click(cx, h * 85 / 100)
    Thread.sleep(1_500)
    device.pressBack()
    device.waitForIdle()
}
