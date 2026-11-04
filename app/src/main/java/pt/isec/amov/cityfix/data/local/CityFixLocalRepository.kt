package pt.isec.amov.cityfix.data.local

import android.content.Context
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import pt.isec.amov.cityfix.model.CityFixUser
import pt.isec.amov.cityfix.model.Incident

class CityFixLocalRepository(context: Context) {
    private val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun loadUser(): CityFixUser? = preferences.getString(USER_KEY, null)?.let {
        runCatching { json.decodeFromString<CityFixUser>(it) }.getOrNull()
    }

    fun loadIncidents(fallback: List<Incident>): List<Incident> =
        preferences.getString(INCIDENTS_KEY, null)?.let {
            runCatching {
                json.decodeFromString(ListSerializer(Incident.serializer()), it)
            }.getOrNull()
        } ?: fallback

    fun saveUser(user: CityFixUser) {
        preferences.edit().putString(USER_KEY, json.encodeToString(CityFixUser.serializer(), user)).apply()
    }

    fun saveIncidents(incidents: List<Incident>) {
        preferences.edit().putString(
            INCIDENTS_KEY,
            json.encodeToString(ListSerializer(Incident.serializer()), incidents)
        ).apply()
    }

    fun clearUser() {
        preferences.edit().remove(USER_KEY).apply()
    }

    private companion object {
        const val FILE_NAME = "cityfix_local"
        const val USER_KEY = "user"
        const val INCIDENTS_KEY = "incidents"
    }
}
