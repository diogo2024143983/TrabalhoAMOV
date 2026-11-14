package pt.isec.amov.cityfix.utils

import pt.isec.amov.cityfix.model.BusinessRules
import pt.isec.amov.cityfix.model.IncidentList

/** Validações reutilizáveis (Regex), usadas pelos UiState dos formulários. */
object CityFixUtils {

    private val emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$".toRegex()
    private val phoneRegex = "^(9[1236][0-9]{7}|2[0-9]{8})$".toRegex()
    private val nifFormatRegex = "^[1235689][0-9]{8}$".toRegex()

    fun isWithinPracaRepublica(lat: Double, lon: Double): Boolean =
        IncidentList.distanceMeters(BusinessRules.PRACA_REPUBLICA_LAT, BusinessRules.PRACA_REPUBLICA_LON, lat, lon) <=
            BusinessRules.ANONYMOUS_MAX_RADIUS_METERS

    fun isValidEmail(email: String): Boolean = emailRegex.matches(email.trim())

    /** NIF português: formato e dígito de controlo. */
    fun isValidPortugueseNif(nif: String): Boolean {
        val clean = nif.trim()
        if (!nifFormatRegex.matches(clean)) return false
        var sum = 0
        for (i in 0 until 8) sum += clean[i].digitToInt() * (9 - i)
        val remainder = sum % 11
        val checkDigit = if (remainder == 0 || remainder == 1) 0 else 11 - remainder
        return checkDigit == clean[8].digitToInt()
    }

    fun isValidPhone(phone: String): Boolean = phoneRegex.matches(phone.replace(Regex("[\\s-]"), ""))

    fun isValidMunicipalEmail(email: String): Boolean {
        val clean = email.trim().lowercase()
        return isValidEmail(clean) &&
            (clean.endsWith("@cm-coimbra.pt") || clean.endsWith("@municipio.pt") || clean.endsWith("@isec.pt"))
    }
}
