package pt.isec.amov.cityfix.utils

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.sqrt

/**
 * Deteção automática de impactos (enunciado I.2.F) com o acelerómetro.
 *
 * Um filtro passa-baixo estima a gravidade. A diferença entre a leitura e a gravidade é a aceleração
 * linear, que se projeta na direção da gravidade para obter a componente vertical (típica de um buraco
 * na estrada). Quando esta ultrapassa [thresholdMs2], o impacto é comunicado, com um período mínimo
 * de [cooldownMs] entre deteções.
 */
class ImpactDetector(
    private val onImpact: (magnitude: Float) -> Unit,
    private val thresholdMs2: Float = 12f,
    private val cooldownMs: Long = 20_000L,
    private val clock: () -> Long = System::currentTimeMillis
) : SensorEventListener {
    private val gravity = FloatArray(3)
    private var initialized = false
    private var lastImpactAt = 0L

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
        onSample(event.values[0], event.values[1], event.values[2])
    }

    /** Separado de onSensorChanged para poder ser testado sem SensorEvent. */
    fun onSample(x: Float, y: Float, z: Float) {
        val values = floatArrayOf(x, y, z)
        if (!initialized) {
            values.copyInto(gravity)
            initialized = true
            return
        }
        for (i in 0..2) gravity[i] = ALPHA * gravity[i] + (1 - ALPHA) * values[i]

        val gNorm = sqrt(gravity[0] * gravity[0] + gravity[1] * gravity[1] + gravity[2] * gravity[2])
        if (gNorm < 1e-3f) return
        // Componente vertical da aceleração linear: (a - g) · ĝ
        val vertical = ((values[0] - gravity[0]) * gravity[0] +
            (values[1] - gravity[1]) * gravity[1] +
            (values[2] - gravity[2]) * gravity[2]) / gNorm

        val now = clock()
        val isHorizontal = kotlin.math.abs(gravity[2]) > 7.5f
        if (isHorizontal && kotlin.math.abs(vertical) >= thresholdMs2 && now - lastImpactAt >= cooldownMs) {
            lastImpactAt = now
            onImpact(kotlin.math.abs(vertical))
        }

    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private companion object {
        const val ALPHA = 0.9f
    }
}

/**
 * Regista o [ImpactDetector] apenas enquanto o ecrã está visível (ON_START/ON_STOP), seguindo o
 * padrão de sensores da aula (DisposableEffect + LifecycleEventObserver).
 */
@Composable
fun ImpactDetectionEffect(enabled: Boolean, onImpact: (Float) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnImpact by rememberUpdatedState(onImpact)

    DisposableEffect(enabled, lifecycleOwner) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager?
        val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (!enabled || sensorManager == null || accelerometer == null) {
            return@DisposableEffect onDispose { }
        }
        val detector = ImpactDetector(onImpact = { currentOnImpact(it) })
        val observer = LifecycleEventObserver { _, event ->
            when (event) {

                Lifecycle.Event.ON_START ->
                    sensorManager.registerListener(detector, accelerometer, SensorManager.SENSOR_DELAY_UI)
                Lifecycle.Event.ON_STOP -> sensorManager.unregisterListener(detector)
                else -> Unit
            }
        }
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            sensorManager.registerListener(detector, accelerometer, SensorManager.SENSOR_DELAY_UI)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            sensorManager.unregisterListener(detector)
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
}
