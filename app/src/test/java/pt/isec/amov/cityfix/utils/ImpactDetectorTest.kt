package pt.isec.amov.cityfix.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImpactDetectorTest {
    private var now = 0L
    private val impacts = mutableListOf<Float>()
    private val detector = ImpactDetector(onImpact = { impacts += it }, clock = { now })

    private fun rest(samples: Int = 20) = repeat(samples) { detector.onSample(0f, 9.81f, 0f); now += 60 }

    @Test
    fun `telemovel parado nao gera impactos`() {
        rest(100)
        assertTrue(impacts.isEmpty())
    }

    @Test
    fun `pico vertical acima do limiar e detetado`() {
        rest()
        detector.onSample(0f, 26f, 0f)
        assertEquals(1, impacts.size)
        assertTrue(impacts.first() >= 8f)
    }

    @Test
    fun `pico pequeno nao e detetado`() {
        rest()
        detector.onSample(0f, 14f, 0f)
        assertTrue(impacts.isEmpty())
    }

    @Test
    fun `segundo impacto dentro do intervalo minimo e ignorado`() {
        rest()
        detector.onSample(0f, 26f, 0f)
        rest(5)
        detector.onSample(0f, 26f, 0f)
        assertEquals(1, impacts.size)

        now += 20_000
        rest()
        detector.onSample(0f, 26f, 0f)
        assertEquals(2, impacts.size)
    }
}
