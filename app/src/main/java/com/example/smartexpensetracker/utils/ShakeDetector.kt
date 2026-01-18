package com.example.smartexpensetracker.utils

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import kotlin.math.abs
import kotlin.math.sqrt

class ShakeDetector(
    private val onShakeHorizontal: () -> Unit, // 2x Horizontal
    private val onShakeVertical: () -> Unit,   // 2x Vertical
    private val onShakeTriple: () -> Unit      // 3x Horizontal
) : SensorEventListener {

    // Thresholds
    private val SHAKE_THRESHOLD_GRAVITY = 2.5F
    private val SHAKE_SLOP_TIME_MS = 400
    private val SHAKE_COUNT_RESET_TIME_MS = 1500

    // Logic for delay
    private val ACTION_DELAY_MS = 600L // Wait this long after 2nd shake to see if 3rd happens
    private val handler = Handler(Looper.getMainLooper())

    private var mShakeTimestamp: Long = 0
    private var mShakeCountHorizontal = 0
    private var mShakeCountVertical = 0

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]

            val gX = x / SensorManager.GRAVITY_EARTH
            val gY = y / SensorManager.GRAVITY_EARTH
            val gZ = z / SensorManager.GRAVITY_EARTH

            val gForce = sqrt((gX * gX + gY * gY + gZ * gZ).toDouble()).toFloat()

            if (gForce > SHAKE_THRESHOLD_GRAVITY) {
                val now = System.currentTimeMillis()

                if (mShakeTimestamp + SHAKE_SLOP_TIME_MS > now) return

                if (mShakeTimestamp + SHAKE_COUNT_RESET_TIME_MS < now) {
                    mShakeCountHorizontal = 0
                    mShakeCountVertical = 0
                }

                mShakeTimestamp = now

                // Horizontal (X) vs Vertical (Y)
                if (abs(gX) > abs(gY)) {
                    // Cancel any pending 2-shake action because we are still shaking!
                    handler.removeCallbacksAndMessages(null)

                    mShakeCountHorizontal++
                    mShakeCountVertical = 0

                    if (mShakeCountHorizontal == 2) {
                        // Wait briefly to see if user shakes a 3rd time
                        handler.postDelayed({
                            onShakeHorizontal()
                            mShakeCountHorizontal = 0
                        }, ACTION_DELAY_MS)
                    } else if (mShakeCountHorizontal == 3) {
                        // 3rd shake detected! Cancel the pending 2-shake action
                        handler.removeCallbacksAndMessages(null)
                        onShakeTriple()
                        mShakeCountHorizontal = 0
                    }
                } else {
                    // Vertical Logic (Simpler, just 2x)
                    mShakeCountVertical++
                    mShakeCountHorizontal = 0

                    if (mShakeCountVertical == 2) {
                        onShakeVertical()
                        mShakeCountVertical = 0
                    }
                }
            }
        }
    }
}