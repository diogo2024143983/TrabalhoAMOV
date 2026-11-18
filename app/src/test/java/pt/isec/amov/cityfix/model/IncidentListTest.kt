package pt.isec.amov.cityfix.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IncidentListTest {
    private val lat = BusinessRules.PRACA_REPUBLICA_LAT
    private val lon = BusinessRules.PRACA_REPUBLICA_LON

    private fun incident(
        id: String,
        status: IncidentStatus = IncidentStatus.OPEN,
        isPublic: Boolean = true,
        authorId: String = "autor",
        createdAt: Long = 0L,
        resolvedAt: Long? = null,
        latitude: Double = lat,
        longitude: Double = lon
    ) = Incident(
        id = id, categoryId = "vias", situation = IncidentSituation.DAMAGE, status = status,
        isPublic = isPublic, authorId = authorId, createdAt = createdAt, resolvedAt = resolvedAt,
        latitude = latitude, longitude = longitude
    )

    @Test
    fun `anonimo so ve ocorrencias publicas ativas`() {
        val list = IncidentList(
            listOf(
                incident("publica"),
                incident("privada", isPublic = false),
                incident("encerrada", status = IncidentStatus.CLOSED)
            )
        )
        val visible = list.visibleTo(CityFixUser(role = UserRole.ANONYMOUS)).map { it.id }
        assertEquals(listOf("publica"), visible)
    }

    @Test
    fun `municipe ve as suas ocorrencias privadas mas nao as dos outros`() {
        val list = IncidentList(
            listOf(
                incident("minha", isPublic = false, authorId = "ana"),
                incident("alheia", isPublic = false, authorId = "rui")
            )
        )
        val visible = list.visibleTo(CityFixUser(id = "ana", role = UserRole.CITIZEN)).map { it.id }
        assertEquals(listOf("minha"), visible)
    }

    @Test
    fun `operador ve todas as ocorrencias`() {
        val list = IncidentList(listOf(incident("a", isPublic = false), incident("b", status = IncidentStatus.CLOSED)))
        assertEquals(2, list.visibleTo(CityFixUser(role = UserRole.OPERATOR)).size)
    }

    @Test
    fun `ocorrencia igual a menos de 10 metros e agrupada`() {
        val list = IncidentList(listOf(incident("existente")))
        // ~5 m para norte
        val duplicate = list.findDuplicate("vias", IncidentSituation.DAMAGE, lat + 0.000045, lon)
        assertNotNull(duplicate)
        assertEquals("existente", duplicate?.id)
    }

    @Test
    fun `ocorrencia a mais de 10 metros ou de outra situacao nao e agrupada`() {
        val list = IncidentList(listOf(incident("existente")))
        assertNull(list.findDuplicate("vias", IncidentSituation.DAMAGE, lat + 0.0002, lon)) // ~22 m
        assertNull(list.findDuplicate("vias", IncidentSituation.THEFT, lat, lon))
    }

    @Test
    fun `ocorrencia encerrada nao agrupa novas`() {
        val list = IncidentList(listOf(incident("antiga", status = IncidentStatus.CLOSED)))
        assertNull(list.findDuplicate("vias", IncidentSituation.DAMAGE, lat, lon))
    }

    @Test
    fun `estatisticas do dashboard`() {
        val day = 24L * 60 * 60 * 1000
        val list = IncidentList(
            listOf(
                incident("a"),
                incident("b", status = IncidentStatus.RESOLVED, createdAt = 0, resolvedAt = 2 * day),
                incident("c", status = IncidentStatus.CLOSED, createdAt = 0, resolvedAt = 4 * day),
                incident("d", status = IncidentStatus.IN_RESOLUTION)
            )
        )
        assertEquals(4, list.totalCount)
        assertEquals(2, list.activeCount)
        assertEquals(50, list.activePercentage)
        assertEquals(3.0, list.averageRepairDays, 0.001)
    }

    @Test
    fun `lista vazia nao divide por zero`() {
        val list = IncidentList()
        assertEquals(0, list.activePercentage)
        assertEquals(0.0, list.averageRepairDays, 0.0)
    }

    @Test
    fun `reabertura so ate 5 dias apos a resolucao`() {
        val resolvedAt = 1_000_000L
        val resolved = incident("r", status = IncidentStatus.RESOLVED, resolvedAt = resolvedAt)
        assertTrue(IncidentList.canReopen(resolved, resolvedAt + BusinessRules.REOPEN_WINDOW_MS))
        assertFalse(IncidentList.canReopen(resolved, resolvedAt + BusinessRules.REOPEN_WINDOW_MS + 1))
        assertFalse(IncidentList.canReopen(incident("aberta"), resolvedAt))
    }

    @Test
    fun `categorias ordenadas respeitando acentos`() {
        val sorted = IncidentList.sortCategories(
            listOf(IncidentCategory(name = "Vias Públicas"), IncidentCategory(name = "Iluminação"), IncidentCategory(name = "Água"))
        ).map { it.name }
        assertEquals(listOf("Água", "Iluminação", "Vias Públicas"), sorted)
    }

    @Test
    fun `distancia haversine aproximada`() {
        // 0,01 grau de latitude ~ 1112 m
        val d = IncidentList.distanceMeters(lat, lon, lat + 0.01, lon)
        assertEquals(1112.0, d, 5.0)
    }
}
