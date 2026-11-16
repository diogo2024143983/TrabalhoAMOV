package pt.isec.amov.cityfix.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import pt.isec.amov.cityfix.model.CityFixUser
import pt.isec.amov.cityfix.model.Incident
import java.io.IOException

/**
 * Instância única do DataStore (regra 1 de 09_Storage). Os dados antigos guardados em
 * SharedPreferences são migrados automaticamente na primeira leitura (regra 4).
 */
private val Context.cityFixDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "cityfix_datastore",
    produceMigrations = { context ->
        listOf(
            SharedPreferencesMigration(context, "cityfix_local"),
            SharedPreferencesMigration(context, "cityfix_notifications")
        )
    }
)

/** Persistência local com Jetpack DataStore (Preferences): sessão, cache de tickets e avisos já enviados. */
class CityFixLocalRepository(context: Context) {
    private val dataStore = context.applicationContext.cityFixDataStore
    private val json = Json { ignoreUnknownKeys = true }

    /** Leitura reativa; um IOException devolve preferências vazias (regra 3). */
    private val preferences: Flow<Preferences> = dataStore.data.catch { e ->
        if (e is IOException) emit(emptyPreferences()) else throw e
    }

    val user: Flow<CityFixUser?> = preferences.map { prefs ->
        prefs[USER_KEY]?.let { runCatching { json.decodeFromString<CityFixUser>(it) }.getOrNull() }
    }

    suspend fun loadUser(): CityFixUser? = user.first()

    suspend fun saveUser(user: CityFixUser) {
        dataStore.edit { it[USER_KEY] = json.encodeToString(CityFixUser.serializer(), user) }
    }

    suspend fun clearUser() {
        dataStore.edit { it.remove(USER_KEY) }
    }

    suspend fun loadIncidents(): List<Incident> = preferences.map { prefs ->
        prefs[INCIDENTS_KEY]?.let {
            runCatching { json.decodeFromString(ListSerializer(Incident.serializer()), it) }.getOrNull()
        }.orEmpty()
    }.first()

    suspend fun saveIncidents(incidents: List<Incident>) {
        dataStore.edit { it[INCIDENTS_KEY] = json.encodeToString(ListSerializer(Incident.serializer()), incidents) }
    }

    /** Avisos de "último dia para reabrir" já mostrados (evita repetir a notificação). */
    suspend fun loadClosingNotified(): Set<String> = preferences.map { it[CLOSING_NOTIFIED_KEY].orEmpty() }.first()

    suspend fun saveClosingNotified(keys: Set<String>) {
        dataStore.edit { it[CLOSING_NOTIFIED_KEY] = keys }
    }

    private companion object {
        // Mesmos nomes das chaves antigas, para a SharedPreferencesMigration as aproveitar
        val USER_KEY = stringPreferencesKey("user")
        val INCIDENTS_KEY = stringPreferencesKey("incidents")
        val CLOSING_NOTIFIED_KEY = stringSetPreferencesKey("closing_notified")
    }
}
