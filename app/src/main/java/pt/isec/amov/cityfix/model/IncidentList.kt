package pt.isec.amov.cityfix.model

import java.text.Collator
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Coleção de domínio das ocorrências (equivalente ao ContactsList das aulas):
 * concentra as regras de negócio puras, sem dependências de Android.
 */
class IncidentList(initialIncidents: List<Incident> = emptyList()) {
    private val _incidents = initialIncidents.toList()

    /** Mais recentes primeiro. */
    fun getAllSorted(): List<Incident> = _incidents.sortedByDescending { it.createdAt }

    fun findById(id: String): Incident? = _incidents.find { it.id == id }

    fun rawList(): List<Incident> = _incidents

    /** Ocorrências visíveis para o utilizador: públicas, as suas, ou todas para o operador. */
    fun visibleTo(user: CityFixUser): List<Incident> = getAllSorted().filter {
        it.isPublic || it.authorId == user.id || user.role == UserRole.OPERATOR
    }

    fun active(): List<Incident> = getAllSorted().filter { it.isActive }

    /** Ocorrência de grupo: mesma categoria e situação, ainda não encerrada, a menos de 10 metros. */
    fun findDuplicate(categoryId: String, situation: IncidentSituation, latitude: Double, longitude: Double): Incident? =
        _incidents.firstOrNull {
            it.status != IncidentStatus.CLOSED &&
                it.categoryId == categoryId &&
                it.situation == situation &&
                distanceMeters(it.latitude, it.longitude, latitude, longitude) <= BusinessRules.DUPLICATE_GROUP_RADIUS_METERS
        }

    // ---------------------------------------------------------------------
    // Estatísticas do dashboard
    // ---------------------------------------------------------------------

    val totalCount: Int get() = _incidents.size

    val activeCount: Int get() = _incidents.count { it.isActive }

    val activePercentage: Int get() = if (totalCount == 0) 0 else activeCount * 100 / totalCount

    /** Tempo médio de reparação em dias (abertura -> resolução). */
    val averageRepairDays: Double
        get() {
            val resolved = _incidents.mapNotNull { i -> i.resolvedAt?.takeIf { it > i.createdAt }?.let { it - i.createdAt } }
            if (resolved.isEmpty()) return 0.0
            return resolved.average() / (1000.0 * 60 * 60 * 24)
        }

    companion object {
        /** Distância geodésica (Haversine) em metros. */
        fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val r = 6371000.0
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
            return r * 2 * atan2(sqrt(a), sqrt(1 - a))
        }

        /** Ordenação alfabética sensível a acentos (Collator, como em P07): "Água" antes de "Iluminação". */
        fun sortCategories(categories: List<IncidentCategory>): List<IncidentCategory> {
            val collator = Collator.getInstance(Locale.getDefault()).apply { strength = Collator.PRIMARY }
            return categories.sortedWith { c1, c2 -> collator.compare(c1.name, c2.name) }
        }

        fun canReopen(incident: Incident, now: Long = System.currentTimeMillis()): Boolean {
            if (incident.status != IncidentStatus.RESOLVED) return false
            val resolvedAt = incident.resolvedAt ?: return false
            return now - resolvedAt <= BusinessRules.REOPEN_WINDOW_MS
        }
    }
}

/** Regras e limites definidos no enunciado. */
object BusinessRules {
    const val PRACA_REPUBLICA_LAT = 40.2095
    const val PRACA_REPUBLICA_LON = -8.4197
    const val ANONYMOUS_MAX_RADIUS_METERS = 10_000.0
    const val DUPLICATE_GROUP_RADIUS_METERS = 10.0
    const val OPERATOR_PROXIMITY_RADIUS_METERS = 20.0
    const val REOPEN_WINDOW_MS = 5L * 24 * 60 * 60 * 1000L
    const val MAX_ATTACHMENTS = 3
}
