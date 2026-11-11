package pt.isec.amov.cityfix.data.cloud

import android.content.Context
import android.net.Uri
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.storage.FirebaseStorage
import pt.isec.amov.cityfix.model.CityFixUser
import pt.isec.amov.cityfix.model.Incident
import pt.isec.amov.cityfix.model.IncidentCategory
import pt.isec.amov.cityfix.model.IncidentMessage
import pt.isec.amov.cityfix.model.IncidentSituation
import pt.isec.amov.cityfix.model.IncidentStatus
import pt.isec.amov.cityfix.model.UserRole
import java.util.UUID

class CityFixFirebaseRepository(context: Context) {
    private val app: FirebaseApp? = FirebaseApp.getApps(context).firstOrNull()
    private val auth: FirebaseAuth? = app?.let { FirebaseAuth.getInstance(it) }
    private val firestore: FirebaseFirestore? = app?.let { FirebaseFirestore.getInstance(it) }
    private val storage: FirebaseStorage? = app?.let { FirebaseStorage.getInstance(it) }

    val isAvailable: Boolean
        get() = auth != null && firestore != null

    val currentUserId: String?
        get() = auth?.currentUser?.uid

    val currentUserEmail: String?
        get() = auth?.currentUser?.email

    val isUserLoggedIn: Boolean
        get() = auth?.currentUser != null

    // =========================================================================
    // AUTENTICAÇÃO E GESTÃO DE UTILIZADORES
    // =========================================================================

    fun login(
        email: String,
        pass: String,
        onSuccess: (String) -> Unit,
        onError: (Exception) -> Unit
    ) {
        val firebaseAuth = auth ?: run {
            onError(IllegalStateException("Firebase Auth não está disponível."))
            return
        }
        firebaseAuth.signInWithEmailAndPassword(email, pass)
            .addOnSuccessListener { result ->
                val uid = result.user?.uid.orEmpty()
                onSuccess(uid)
            }
            .addOnFailureListener(onError)
    }

    fun register(
        email: String,
        pass: String,
        user: CityFixUser,
        onSuccess: (String) -> Unit,
        onError: (Exception) -> Unit
    ) {
        val firebaseAuth = auth ?: run {
            onError(IllegalStateException("Firebase Auth não está disponível."))
            return
        }
        firebaseAuth.createUserWithEmailAndPassword(email, pass)
            .addOnSuccessListener { result ->
                val uid = result.user?.uid ?: UUID.randomUUID().toString()
                val newUser = user.copy(id = uid, email = email)
                saveUser(newUser, onSuccess = { onSuccess(uid) }, onError = onError)
            }
            .addOnFailureListener(onError)
    }

    fun signInAnonymously(
        onSuccess: (String) -> Unit,
        onError: (Exception) -> Unit
    ) {
        val firebaseAuth = auth ?: run {
            onError(IllegalStateException("Firebase Auth não está disponível."))
            return
        }
        firebaseAuth.signInAnonymously()
            .addOnSuccessListener { result ->
                val uid = result.user?.uid ?: UUID.randomUUID().toString()
                val anonUser = CityFixUser(
                    id = uid,
                    name = "Cidadão Anónimo",
                    role = UserRole.ANONYMOUS,
                    active = true
                )
                saveUser(anonUser, onSuccess = { onSuccess(uid) }, onError = onError)
            }
            .addOnFailureListener(onError)
    }

    fun logout() {
        auth?.signOut()
    }

    fun saveUser(
        user: CityFixUser,
        onSuccess: () -> Unit = {},
        onError: (Exception) -> Unit = {}
    ) {
        val database = firestore ?: return
        val targetUid = user.id.ifBlank { currentUserId.orEmpty() }
        if (targetUid.isBlank()) {
            onError(IllegalStateException("ID de utilizador inválido."))
            return
        }

        database.collection(USERS_COLLECTION).document(targetUid)
            .set(user.toMap())
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener(onError)
    }

    fun getUser(
        userId: String,
        onSuccess: (CityFixUser?) -> Unit,
        onError: (Exception) -> Unit = {}
    ) {
        val database = firestore ?: run {
            onSuccess(null)
            return
        }
        database.collection(USERS_COLLECTION).document(userId).get()
            .addOnSuccessListener { doc ->
                onSuccess(doc.toCityFixUser())
            }
            .addOnFailureListener(onError)
    }

    fun observeCurrentUser(
        userId: String,
        onChanged: (CityFixUser?) -> Unit,
        onError: (Exception) -> Unit = {}
    ): ListenerRegistration? {
        val database = firestore ?: return null
        return database.collection(USERS_COLLECTION).document(userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onError(error)
                    return@addSnapshotListener
                }
                onChanged(snapshot?.toCityFixUser())
            }
    }

    fun isNifAlreadyRegistered(
        nif: String,
        excludeUserId: String? = null,
        onResult: (Boolean) -> Unit
    ) {
        val database = firestore ?: run {
            onResult(false)
            return
        }
        if (nif.isBlank()) {
            onResult(false)
            return
        }
        database.collection(USERS_COLLECTION)
            .whereEqualTo("nif", nif.trim())
            .get()
            .addOnSuccessListener { snapshot ->
                val exists = snapshot.documents.any { it.id != excludeUserId }
                onResult(exists)
            }
            .addOnFailureListener {
                onResult(false)
            }
    }

    fun getPendingCitizens(
        onSuccess: (List<CityFixUser>) -> Unit,
        onError: (Exception) -> Unit = {}
    ) {
        val database = firestore ?: return
        database.collection(USERS_COLLECTION)
            .whereEqualTo("role", UserRole.CITIZEN.name)
            .whereEqualTo("active", false)
            .get()
            .addOnSuccessListener { snapshot ->
                val list = snapshot.documents.mapNotNull { it.toCityFixUser() }
                onSuccess(list)
            }
            .addOnFailureListener(onError)
    }

    fun validateCitizen(
        userId: String,
        active: Boolean,
        onSuccess: () -> Unit = {},
        onError: (Exception) -> Unit = {}
    ) {
        val database = firestore ?: return
        database.collection(USERS_COLLECTION).document(userId)
            .update("active", active)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener(onError)
    }

    // =========================================================================
    // GESTÃO DE OCORRÊNCIAS / TICKETS (Coleção "tickets")
    // =========================================================================

    fun createTicket(
        incident: Incident,
        onSuccess: (String) -> Unit = {},
        onError: (Exception) -> Unit = {}
    ) {
        val database = firestore ?: return
        val currentUid = currentUserId.orEmpty()
        val ticketId = incident.id.ifBlank { UUID.randomUUID().toString() }
        val toSave = incident.copy(
            id = ticketId,
            authorId = incident.authorId.ifBlank { currentUid }
        )

        database.collection(TICKETS_COLLECTION).document(ticketId)
            .set(toSave.toMap())
            .addOnSuccessListener { onSuccess(ticketId) }
            .addOnFailureListener(onError)
    }

    fun observeTickets(
        onChanged: (List<Incident>) -> Unit,
        onError: (Exception) -> Unit = {}
    ): ListenerRegistration? {
        val database = firestore ?: return null
        return database.collection(TICKETS_COLLECTION)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onError(error)
                    return@addSnapshotListener
                }
                val tickets = snapshot?.documents?.mapNotNull { it.toIncident() }.orEmpty()
                onChanged(tickets)
            }
    }

    // Alias para compatibilidade com o código anterior
    fun observeIncidents(
        onChanged: (List<Incident>) -> Unit,
        onError: (Exception) -> Unit = {}
    ): ListenerRegistration? = observeTickets(onChanged, onError)

    fun getTicket(
        ticketId: String,
        onSuccess: (Incident?) -> Unit,
        onError: (Exception) -> Unit = {}
    ) {
        val database = firestore ?: return
        database.collection(TICKETS_COLLECTION).document(ticketId).get()
            .addOnSuccessListener { doc -> onSuccess(doc.toIncident()) }
            .addOnFailureListener(onError)
    }

    fun updateTicketStatus(
        ticketId: String,
        newStatus: IncidentStatus,
        proofUrl: String = "",
        onSuccess: () -> Unit = {},
        onError: (Exception) -> Unit = {}
    ) {
        val database = firestore ?: return
        val now = System.currentTimeMillis()
        val updates = mutableMapOf<String, Any>(
            "status" to newStatus.name
        )

        when (newStatus) {
            IncidentStatus.IN_RESOLUTION -> {
                updates["inResolutionAt"] = now
            }
            IncidentStatus.RESOLVED -> {
                updates["resolvedAt"] = now
                if (proofUrl.isNotBlank()) {
                    updates["resolutionProofUrl"] = proofUrl
                }
            }
            IncidentStatus.CLOSED -> {
                updates["closedAt"] = now
            }
            else -> {}
        }

        database.collection(TICKETS_COLLECTION).document(ticketId)
            .update(updates)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener(onError)
    }

    fun reopenTicket(
        ticketId: String,
        reason: String,
        onSuccess: () -> Unit = {},
        onError: (Exception) -> Unit = {}
    ) {
        val database = firestore ?: return
        val updates = mapOf(
            "status" to IncidentStatus.OPEN.name,
            "reopenReason" to reason,
            "resolvedAt" to FieldValue.delete()
        )
        database.collection(TICKETS_COLLECTION).document(ticketId)
            .update(updates)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener(onError)
    }

    fun addSupporter(
        ticketId: String,
        userId: String = currentUserId.orEmpty(),
        onSuccess: () -> Unit = {},
        onError: (Exception) -> Unit = {}
    ) {
        if (userId.isBlank()) {
            onError(IllegalStateException("Utilizador não autenticado."))
            return
        }
        val database = firestore ?: return
        database.collection(TICKETS_COLLECTION).document(ticketId)
            .update("supporterIds", FieldValue.arrayUnion(userId))
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener(onError)
    }

    fun removeSupporter(
        ticketId: String,
        userId: String = currentUserId.orEmpty(),
        onSuccess: () -> Unit = {},
        onError: (Exception) -> Unit = {}
    ) {
        if (userId.isBlank()) return
        val database = firestore ?: return
        database.collection(TICKETS_COLLECTION).document(ticketId)
            .update("supporterIds", FieldValue.arrayRemove(userId))
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener(onError)
    }

    fun deleteTicket(
        ticketId: String,
        onSuccess: () -> Unit = {},
        onError: (Exception) -> Unit = {}
    ) {
        val database = firestore ?: return
        database.collection(TICKETS_COLLECTION).document(ticketId)
            .delete()
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener(onError)
    }

    // =========================================================================
    // GESTÃO DE CATEGORIAS (Coleção "categories")
    // =========================================================================

    fun observeCategories(
        onChanged: (List<IncidentCategory>) -> Unit,
        onError: (Exception) -> Unit = {}
    ): ListenerRegistration? {
        val database = firestore ?: return null
        return database.collection(CATEGORIES_COLLECTION)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onError(error)
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { doc ->
                    IncidentCategory(
                        id = doc.id,
                        name = doc.getString("name").orEmpty(),
                        active = doc.getBoolean("active") ?: true
                    )
                }.orEmpty()
                onChanged(list)
            }
    }

    fun saveCategory(
        category: IncidentCategory,
        onSuccess: () -> Unit = {},
        onError: (Exception) -> Unit = {}
    ) {
        val database = firestore ?: return
        val id = category.id.ifBlank { UUID.randomUUID().toString() }
        val data = mapOf(
            "name" to category.name,
            "active" to category.active
        )
        database.collection(CATEGORIES_COLLECTION).document(id)
            .set(data)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener(onError)
    }

    // =========================================================================
    // MENSAGENS E COMUNICAÇÃO NO TICKET (Subcoleção "messages")
    // =========================================================================

    fun sendMessage(
        message: IncidentMessage,
        onSuccess: () -> Unit = {},
        onError: (Exception) -> Unit = {}
    ) {
        val database = firestore ?: return
        val messageId = message.id.ifBlank { UUID.randomUUID().toString() }
        val toSave = message.copy(id = messageId)

        database.collection(TICKETS_COLLECTION).document(toSave.ticketId)
            .collection(MESSAGES_COLLECTION).document(messageId)
            .set(toSave.toMap())
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener(onError)
    }

    fun observeMessages(
        ticketId: String,
        onChanged: (List<IncidentMessage>) -> Unit,
        onError: (Exception) -> Unit = {}
    ): ListenerRegistration? {
        val database = firestore ?: return null
        return database.collection(TICKETS_COLLECTION).document(ticketId)
            .collection(MESSAGES_COLLECTION)
            .orderBy("createdAt")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onError(error)
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { it.toIncidentMessage() }.orEmpty()
                onChanged(list)
            }
    }

    // =========================================================================
    // UPLOAD DE MULTIMÉDIA (Firebase Storage)
    // =========================================================================

    fun uploadMedia(
        uri: Uri,
        storagePath: String,
        onSuccess: (String) -> Unit,
        onError: (Exception) -> Unit
    ) {
        val storageInstance = storage ?: run {
            onError(IllegalStateException("Firebase Storage não está configurado."))
            return
        }
        val ref = storageInstance.reference.child(storagePath)
        ref.putFile(uri)
            .continueWithTask { task ->
                if (!task.isSuccessful) {
                    throw (task.exception ?: IllegalStateException("Erro ao enviar ficheiro."))
                }
                ref.downloadUrl
            }
            .addOnSuccessListener { downloadUri ->
                onSuccess(downloadUri.toString())
            }
            .addOnFailureListener(onError)
    }

    // =========================================================================
    // NOTIFICAÇÕES (Firebase Cloud Messaging)
    // =========================================================================

    fun saveMessagingToken(token: String, onError: (Exception) -> Unit = {}) {
        val uid = currentUserId ?: return
        val database = firestore ?: return
        database.collection(USERS_COLLECTION).document(uid)
            .update("messagingToken", token)
            .addOnFailureListener(onError)
    }

    fun registerCurrentMessagingToken(onError: (Exception) -> Unit = {}) {
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener(::saveMessagingToken)
            .addOnFailureListener(onError)
    }

    // =========================================================================
    // CONVERSORES INTERNOS (Mapeamento entre Firestore e Data Classes)
    // =========================================================================

    private fun Incident.toMap(): Map<String, Any?> = mapOf(
        "title" to title,
        "description" to description,
        "categoryId" to categoryId,
        "categoryName" to categoryName,
        "situation" to situation.name,
        "status" to status.name,
        "isPublic" to isPublic,
        "authorId" to authorId,
        "authorName" to authorName,
        "authorPhone" to authorPhone,
        "latitude" to latitude,
        "longitude" to longitude,
        "locationAddress" to locationAddress,
        "mediaUrl" to mediaUrl,
        "thumbnailUrl" to thumbnailUrl,
        "isVideo" to isVideo,
        "resolutionProofUrl" to resolutionProofUrl,
        "reopenReason" to reopenReason,
        "createdAt" to createdAt,
        "inResolutionAt" to inResolutionAt,
        "resolvedAt" to resolvedAt,
        "closedAt" to closedAt,
        "supporterIds" to supporterIds
    )

    private fun DocumentSnapshot.toIncident(): Incident? {
        return runCatching {
            Incident(
                id = id,
                title = getString("title").orEmpty(),
                description = getString("description").orEmpty(),
                categoryId = getString("categoryId").orEmpty(),
                categoryName = getString("categoryName").orEmpty(),
                situation = runCatching {
                    IncidentSituation.valueOf(getString("situation").orEmpty())
                }.getOrDefault(IncidentSituation.DAMAGE),
                status = runCatching {
                    IncidentStatus.valueOf(getString("status").orEmpty())
                }.getOrDefault(IncidentStatus.OPEN),
                isPublic = getBoolean("isPublic") ?: true,
                authorId = getString("authorId").orEmpty(),
                authorName = getString("authorName").orEmpty(),
                authorPhone = getString("authorPhone").orEmpty(),
                latitude = getDouble("latitude") ?: 0.0,
                longitude = getDouble("longitude") ?: 0.0,
                locationAddress = getString("locationAddress").orEmpty(),
                mediaUrl = getString("mediaUrl").orEmpty(),
                thumbnailUrl = getString("thumbnailUrl").orEmpty(),
                isVideo = getBoolean("isVideo") ?: false,
                resolutionProofUrl = getString("resolutionProofUrl").orEmpty(),
                reopenReason = getString("reopenReason").orEmpty(),
                createdAt = getLong("createdAt") ?: 0L,
                inResolutionAt = getLong("inResolutionAt"),
                resolvedAt = getLong("resolvedAt"),
                closedAt = getLong("closedAt"),
                supporterIds = (get("supporterIds") as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
            )
        }.getOrNull()
    }

    private fun CityFixUser.toMap(): Map<String, Any?> = mapOf(
        "name" to name,
        "email" to email,
        "phone" to phone,
        "nif" to nif,
        "role" to role.name,
        "active" to active,
        "georeference" to georeference,
        "photoUrl" to photoUrl,
        "impactDetectionEnabled" to impactDetectionEnabled,
        "messagingToken" to messagingToken
    )

    private fun DocumentSnapshot.toCityFixUser(): CityFixUser? {
        return runCatching {
            CityFixUser(
                id = id,
                name = getString("name").orEmpty(),
                email = getString("email").orEmpty(),
                phone = getString("phone").orEmpty(),
                nif = getString("nif").orEmpty(),
                role = runCatching {
                    UserRole.valueOf(getString("role").orEmpty())
                }.getOrDefault(UserRole.ANONYMOUS),
                active = getBoolean("active") ?: true,
                georeference = getString("georeference").orEmpty(),
                photoUrl = getString("photoUrl"),
                impactDetectionEnabled = getBoolean("impactDetectionEnabled") ?: false,
                messagingToken = getString("messagingToken").orEmpty()
            )
        }.getOrNull()
    }

    private fun IncidentMessage.toMap(): Map<String, Any?> = mapOf(
        "ticketId" to ticketId,
        "authorId" to authorId,
        "authorName" to authorName,
        "authorRole" to authorRole.name,
        "text" to text,
        "createdAt" to createdAt
    )

    private fun DocumentSnapshot.toIncidentMessage(): IncidentMessage? {
        return runCatching {
            IncidentMessage(
                id = id,
                ticketId = getString("ticketId").orEmpty(),
                authorId = getString("authorId").orEmpty(),
                authorName = getString("authorName").orEmpty(),
                authorRole = runCatching {
                    UserRole.valueOf(getString("authorRole").orEmpty())
                }.getOrDefault(UserRole.CITIZEN),
                text = getString("text").orEmpty(),
                createdAt = getLong("createdAt") ?: 0L
            )
        }.getOrNull()
    }

    private companion object {
        const val USERS_COLLECTION = "users"
        const val TICKETS_COLLECTION = "tickets"
        const val CATEGORIES_COLLECTION = "categories"
        const val MESSAGES_COLLECTION = "messages"
    }
}
