package dev.franklyn.foldscenes.fold

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.os.SystemClock
import android.util.Log
import android.view.Display
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Angle assumed before the first sensor event: flat (180) when the inner display is on, else closed (0). */
fun defaultAngle(innerDisplayOn: Boolean): Float = if (innerDisplayOn) 180f else 0f

/** Hinge angle in degrees (0..180) from [Sensor.TYPE_HINGE_ANGLE] at the fastest delay. */
class HingeSource(context: Context) {
    private val sensorManager = context.getSystemService(SensorManager::class.java)
    private val displayManager = context.getSystemService(DisplayManager::class.java)
    private val sensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE)

    private val _angle = MutableStateFlow(initialAngle())
    val angle: StateFlow<Float> = _angle.asStateFlow()

    private val _lastEventNanos = MutableStateFlow(0L)
    val lastEventNanos: StateFlow<Long> = _lastEventNanos.asStateFlow()

    @Volatile
    internal var isRegistered: Boolean = false
        private set

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            _angle.value = sanitizeAngle(event.values[0])
            _lastEventNanos.value = SystemClock.elapsedRealtimeNanos()
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    @Synchronized
    fun start() {
        if (isRegistered) return
        if (sensor == null) {
            Log.w(TAG, "no hinge angle sensor; angle stays at its default")
            return
        }
        _angle.value = initialAngle()
        isRegistered = sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_FASTEST)
        if (!isRegistered) Log.w(TAG, "registerListener failed for hinge angle sensor")
    }

    @Synchronized
    fun stop() {
        if (!isRegistered) return
        sensorManager.unregisterListener(listener)
        isRegistered = false
    }

    private fun initialAngle() =
        defaultAngle(displayManager?.getDisplay(0)?.state == Display.STATE_ON)

    private companion object {
        const val TAG = "FoldScenes"
    }
}
