package pt.isec.amov.cityfix.data.cloud

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.messaging.FirebaseMessaging
import android.net.Uri
import pt.isec.amov.cityfix.model.CityFixUser
import pt.isec.amov.cityfix.model.Incident
import pt.isec.amov.cityfix.model.IncidentMessage
import java.util.UUID

class CityFixFirebaseRepository(context: Context) {
    private val app = FirebaseApp.getApps(context).firstOrNull()
    private val auth = app?.let { FirebaseAuth.getInstance(it) }
    private val firestore = app?.let { FirebaseFirestore.getInstance(it) }
    private val storage = app?.let { FirebaseStorage.getInstance(it) }

    val isAvailable: Boolean
        get() = auth != null && firestore != null

    val currentUserId: String?
        get() = auth?.currentUser?.uid

    fun saveUser(user: CityFixUser, onError: (Exception) -> Unit = {}) {
        val database = firestore ?: return
        withAuthenticatedUser(
            onReady = { uid ->
                database.collection(USERS_COLLECTION).document(uid)
                    .set(
                        mapOf(
                            "name" to user.name,
                            "email" to user.email,
                            "nif" to user.nif,
                            "role" to user.role.name,
                            "active" to user.active
                        )
                    )
                    .addOnFailureListener(onError)
            },
            onError = onError
        )
    }

    fun saveIncidents(incidents: List<Incident>, onError: (Exception) -> Unit = {}) {
        val database = firestore ?: return
        withAuthenticatedUser(
            onReady = { uid ->
                val batch = database.batch()
                incidents
                    .filter { it.ownerId.isBlank() || it.ownerId == uid }
                    .forEach { incident ->
                    val ownedIncident = incident.copy(ownerId = incident.ownerId.ifBlank { uid })
                    batch.set(
                        database.collection(INCIDENTS_COLLECTION).document(ownedIncident.id),
                        ownedIncident.toMap()
                    )
                }
                batch.commit().addOnFailureListener(onError)
            },
            onError = onError
        )
    }

    fun signInOrRegister(
        email: String,
        password: String,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        val firebaseAuth = auth ?: run {
            onError(IllegalStateException("Firebase não está configurado."))
            return
        }
        firebaseAuth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener {
                firebaseAuth.createUserWithEmailAndPassword(email, password)
                    .addOnSuccessListener { onSuccess() }
                    .addOnFailureListener(onError)
            }
    }

    fun observeIncidents(
        onChanged: (List<Incident>) -> Unit,
        onError: (Exception) -> Unit = {}
    ): ListenerRegistration? {
        val database = firestore ?: return null
        return database.collection(INCIDENTS_COLLECTION)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onError(error)
                    return@addSnapshotListener
                }

                val incidents = snapshot?.documents?.mapNotNull { it.toIncident() }.orEmpty()
                if (incidents.isNotEmpty()) onChanged(incidents)
            }
    }

    fun uploadMedia(
        uri: Uri,
        incidentId: String,
        onSuccess: (String) -> Unit,
        onError: (Exception) -> Unit
    ) {
        val storageInstance = storage ?: run {
            onError(IllegalStateException("Firebase Storage não está configurado."))
            return
        }
        withAuthenticatedUser(
            onReady = { uid ->
                storageInstance.reference.child("users/$uid/incidents/$incidentId")
                    .putFile(uri)
                    .continueWithTask { task ->
                        if (!task.isSuccessful) throw (task.exception ?: IllegalStateException("Upload falhou."))
                        task.result.storage.downloadUrl
                    }
                    .addOnSuccessListener { onSuccess(it.toString()) }
                    .addOnFailureListener(onError)
            },
            onError = onError
        )
    }

    fun saveMessagingToken(token: String, onError: (Exception) -> Unit = {}) {
        val database = firestore ?: return
        withAuthenticatedUser(
            onReady = { uid ->
                database.collection(USERS_COLLECTION).document(uid)
                    .update("messagingToken", token)
                    .addOnFailureListener(onError)
            },
            onError = onError
        )
    }

    fun registerCurrentMessagingToken(onError: (Exception) -> Unit = {}) {
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener(::saveMessagingToken)
            .addOnFailureListener(onError)
    }

    fun sendMessage(message: IncidentMessage, onError: (Exception) -> Unit = {}) {
        val database = firestore ?: return
        database.collection(INCIDENTS_COLLECTION).document(message.incidentId)
            .collection(MESSAGES_COLLECTION).document(message.id)
            .set(
                mapOf(
                    "incidentId" to message.incidentId,
                    "authorId" to message.authorId,
                    "authorName" to message.authorName,
                    "text" to message.text,
                    "createdAt" to message.createdAt
                )
            )
            .addOnFailureListener(onError)
    }

    fun observeMessages(
        incidentId: String,
        onChanged: (List<IncidentMessage>) -> Unit,
        onError: (Exception) -> Unit = {}
    ): ListenerRegistration? {
        val database = firestore ?: return null
        return database.collection(INCIDENTS_COLLECTION).document(incidentId)
            .collection(MESSAGES_COLLECTION)
            .orderBy("createdAt")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onError(error)
                    return@addSnapshotListener
                }
                onChanged(snapshot?.documents?.mapNotNull { document ->
                    runCatching {
                        IncidentMessage(
                            id = document.id,
                            incidentId = incidentId,
                            authorId = document.getString("authorId").orEmpty(),
                            authorName = document.getString("authorName").orEmpty(),
                            text = document.getString("text").orEmpty(),
                            createdAt = document.getLong("createdAt") ?: 0L
                        )
                    }.getOrNull()
                }.orEmpty())
            }
    }

    private fun withAuthenticatedUser(
        onReady: (String) -> Unit,
        onError: (Exception) -> Unit
    ) {
        val firebaseAuth = auth ?: return
        firebaseAuth.currentUser?.let {
            onReady(it.uid)
            return
        }
        firebaseAuth.signInAnonymously()
            .addOnSuccessListener { result -> onReady(result.user?.uid.orEmpty()) }
            .addOnFailureListener(onError)
    }

    private fun Incident.toMap(): Map<String, Any> = mapOf(
        "title" to title,
        "description" to description,
        "category" to category,
        "situation" to situation.name,
        "status" to status.name,
        "public" to public,
        "author" to author,
        "location" to location,
        "ownerId" to ownerId,
        "hasMedia" to hasMedia,
        "mediaUrl" to mediaUrl,
        "supporters" to supporters
    )

    private fun com.google.firebase.firestore.DocumentSnapshot.toIncident(): Incident? {
        return runCatching {
            Incident(
                id = id,
                title = getString("title").orEmpty(),
                description = getString("description").orEmpty(),
                category = getString("category").orEmpty(),
                situation = enumValueOf(getString("situation").orEmpty()),
                status = enumValueOf(getString("status").orEmpty()),
                public = getBoolean("public") ?: true,
                author = getString("author").orEmpty(),
                location = getString("location").orEmpty(),
                ownerId = getString("ownerId").orEmpty(),
                hasMedia = getBoolean("hasMedia") ?: false,
                mediaUrl = getString("mediaUrl").orEmpty(),
                supporters = getLong("supporters")?.toInt() ?: 1
            )
        }.getOrNull()
    }

    private companion object {
        const val USERS_COLLECTION = "users"
        const val INCIDENTS_COLLECTION = "incidents"
        const val MESSAGES_COLLECTION = "messages"
    }
}
