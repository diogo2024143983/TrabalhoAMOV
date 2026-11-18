package pt.isec.amov.cityfix.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.isec.amov.cityfix.model.BusinessRules

class CityFixUtilsTest {

    @Test
    fun `nif valido com digito de controlo correto`() {
        assertTrue(CityFixUtils.isValidPortugueseNif("287654320"))
        assertTrue(CityFixUtils.isValidPortugueseNif(" 287654320 "))
    }

    @Test
    fun `nif invalido`() {
        assertFalse(CityFixUtils.isValidPortugueseNif("287654321")) // dígito de controlo errado
        assertFalse(CityFixUtils.isValidPortugueseNif("12345678"))  // 8 dígitos
        assertFalse(CityFixUtils.isValidPortugueseNif("487654320")) // primeiro dígito inválido
        assertFalse(CityFixUtils.isValidPortugueseNif("28765432a"))
    }

    @Test
    fun `telefones portugueses`() {
        assertTrue(CityFixUtils.isValidPhone("912345678"))
        assertTrue(CityFixUtils.isValidPhone("239 123 456"))
        assertFalse(CityFixUtils.isValidPhone("812345678"))
        assertFalse(CityFixUtils.isValidPhone("91234567"))
    }

    @Test
    fun `emails`() {
        assertTrue(CityFixUtils.isValidEmail("ana.silva@example.com"))
        assertFalse(CityFixUtils.isValidEmail("ana@"))
        assertFalse(CityFixUtils.isValidEmail("ana silva@example.com"))
    }

    @Test
    fun `email de operador tem de ser do municipio`() {
        assertTrue(CityFixUtils.isValidMunicipalEmail("rui@cm-coimbra.pt"))
        assertTrue(CityFixUtils.isValidMunicipalEmail("RUI@CM-COIMBRA.PT"))
        assertFalse(CityFixUtils.isValidMunicipalEmail("rui@gmail.com"))
    }

    @Test
    fun `perimetro de 10 km da Praca da Republica`() {
        val lat = BusinessRules.PRACA_REPUBLICA_LAT
        val lon = BusinessRules.PRACA_REPUBLICA_LON
        assertTrue(CityFixUtils.isWithinPracaRepublica(lat, lon))
        assertTrue(CityFixUtils.isWithinPracaRepublica(lat + 0.08, lon))  // ~8,9 km
        assertFalse(CityFixUtils.isWithinPracaRepublica(lat + 0.1, lon))  // ~11,1 km
        assertFalse(CityFixUtils.isWithinPracaRepublica(38.7223, -9.1393)) // Lisboa
    }
}
