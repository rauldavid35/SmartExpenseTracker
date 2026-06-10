package com.example.smartexpensetracker

import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.*
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.example.smartexpensetracker.ui.navigation.MainApp
import com.example.smartexpensetracker.ui.theme.SmartExpenseTrackerTheme
import com.example.smartexpensetracker.utils.BiometricPromptManager
import com.example.smartexpensetracker.utils.NotificationHelper
import com.example.smartexpensetracker.utils.ShakeDetector
import com.example.smartexpensetracker.utils.NetworkMonitor
import com.example.smartexpensetracker.viewmodel.AuthViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {

    private val authViewModel: AuthViewModel by viewModels()
    private val biometricManager by lazy { BiometricPromptManager(this) }

    private lateinit var sensorManager: SensorManager
    private var shakeDetector: ShakeDetector? = null

    private var triggerAction = mutableIntStateOf(0)
    private var volumeUpJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        NetworkMonitor.init(applicationContext)

        // Create notification channels as early as possible
        NotificationHelper.createChannels(this)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        shakeDetector = ShakeDetector { triggerAction.intValue = 99 }

        handleIntent(intent)

        setContent {
            SmartExpenseTrackerTheme {
                MainApp(authViewModel, biometricManager, triggerAction)
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (event?.repeatCount == 0) {
            when (keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP -> {
                    if (volumeUpJob?.isActive == true) {
                        volumeUpJob?.cancel(); volumeUpJob = null
                        triggerAction.intValue = 2
                    } else {
                        volumeUpJob = lifecycleScope.launch {
                            delay(400)
                            triggerAction.intValue = 1
                            volumeUpJob = null
                        }
                    }
                    return true
                }
                KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    triggerAction.intValue = 3
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent?.getStringExtra("SHORTCUT_ACTION")?.let { action ->
            when (action) {
                "voice_expense"  -> triggerAction.intValue = 1
                "voice_list"     -> triggerAction.intValue = 2
                "camera_expense" -> triggerAction.intValue = 3
            }
        }
    }

    override fun onResume() {
        super.onResume()
        shakeDetector?.let {
            sensorManager.registerListener(
                it,
                sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
                SensorManager.SENSOR_DELAY_UI
            )
        }
    }

    override fun onPause() {
        super.onPause()
        shakeDetector?.let { sensorManager.unregisterListener(it) }
    }
}