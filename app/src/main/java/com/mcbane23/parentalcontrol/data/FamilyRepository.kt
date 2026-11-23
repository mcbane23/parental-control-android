package com.mcbane23.parentalcontrol.data

import com.google.firebase.Timestamp
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.Date

class PairingException(message: String) : Exception(message)

/**
 * All Firebase access for accounts, families and pairing.
 *
 * Firestore layout:
 *  - users/{uid}                    -> familyId, role
 *  - families/{familyId}            -> name, ownerUid, createdAt
 *  - families/{familyId}/members/{uid} -> name, role, owner, fullControl, code
 *  - pairingCodes/{code}            -> familyId, familyName, role, createdBy, expiresAt
 *
 * The matching security rules are in firestore.rules at the repository root.
 */
class FamilyRepository(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore,
) {
    val currentUid: String? get() = auth.currentUser?.uid
    val isAnonymous: Boolean get() = auth.currentUser?.isAnonymous == true
    val accountName: String? get() = auth.currentUser?.displayName

    suspend fun signInWithGoogle(credential: AuthCredential) {
        auth.signInWithCredential(credential).await()
    }

    suspend fun signInChildDevice() {
        if (auth.currentUser == null) auth.signInAnonymously().await()
    }

    fun signOut() = auth.signOut()

    suspend fun loadProfile(): UserProfile? {
        val uid = currentUid ?: return null
        val doc = db.collection(USERS).document(uid).get().await()
        val familyId = doc.getString("familyId") ?: return null
        val role = Role.fromId(doc.getString("role")) ?: return null
        return UserProfile(uid, familyId, role)
    }

    suspend fun loadFamily(familyId: String): Family? {
        val doc = db.collection(FAMILIES).document(familyId).get().await()
        if (!doc.exists()) return null
        return Family(
            id = doc.id,
            name = doc.getString("name").orEmpty(),
            ownerUid = doc.getString("ownerUid").orEmpty(),
        )
    }

    /** Creates a family with the signed-in parent as its owner. */
    suspend fun createFamily(familyName: String, parentName: String): UserProfile {
        val uid = requireNotNull(currentUid) { "Not signed in" }
        val familyRef = db.collection(FAMILIES).document()
        val batch = db.batch()
        batch.set(
            familyRef,
            mapOf(
                "name" to familyName,
                "ownerUid" to uid,
                "createdAt" to Timestamp.now(),
            ),
        )
        batch.set(
            familyRef.collection(MEMBERS).document(uid),
            mapOf(
                "name" to parentName,
                "role" to Role.PARENT.id,
                "owner" to true,
                "fullControl" to true,
            ),
        )
        batch.set(
            db.collection(USERS).document(uid),
            mapOf("familyId" to familyRef.id, "role" to Role.PARENT.id),
        )
        batch.commit().await()
        return UserProfile(uid, familyRef.id, Role.PARENT)
    }

    /** Creates a short-lived code that lets a child phone or a second parent join the family. */
    suspend fun createPairingCode(family: Family, role: Role): PairingCode {
        val uid = requireNotNull(currentUid) { "Not signed in" }
        val code = PairingCodes.generate()
        val expiresAt = System.currentTimeMillis() + PairingCodes.VALID_MINUTES * 60_000
        db.collection(CODES).document(code).set(
            mapOf(
                "familyId" to family.id,
                "familyName" to family.name,
                "role" to role.id,
                "createdBy" to uid,
                "expiresAt" to Timestamp(Date(expiresAt)),
            ),
        ).await()
        return PairingCode(code, role, expiresAt)
    }

    /** Joins the family a pairing code belongs to. The caller must already be signed in. */
    suspend fun joinWithCode(rawCode: String, name: String, expectedRole: Role): UserProfile {
        val uid = requireNotNull(currentUid) { "Not signed in" }
        val code = PairingCodes.normalize(rawCode)
        if (!PairingCodes.isWellFormed(code)) {
            throw PairingException("The code should be ${PairingCodes.LENGTH} letters and digits.")
        }
        val codeRef = db.collection(CODES).document(code)
        val codeDoc = codeRef.get().await()
        if (!codeDoc.exists()) throw PairingException("That code was not found. Check it and try again.")
        val expiresAt = codeDoc.getTimestamp("expiresAt")?.toDate()?.time ?: 0L
        if (expiresAt < System.currentTimeMillis()) {
            throw PairingException("That code has expired. Ask for a new one.")
        }
        val role = Role.fromId(codeDoc.getString("role"))
        if (role != expectedRole) {
            throw PairingException(
                if (expectedRole == Role.CHILD) "That code is for inviting a parent, not for a child's phone."
                else "That code is for a child's phone, not for inviting a parent.",
            )
        }
        val familyId = codeDoc.getString("familyId")
            ?: throw PairingException("That code is not valid.")

        val batch = db.batch()
        batch.set(
            db.collection(FAMILIES).document(familyId).collection(MEMBERS).document(uid),
            mapOf(
                "name" to name,
                "role" to expectedRole.id,
                "owner" to false,
                "fullControl" to false,
                "code" to code,
            ),
        )
        batch.set(
            db.collection(USERS).document(uid),
            mapOf("familyId" to familyId, "role" to expectedRole.id),
        )
        batch.commit().await()

        // A code is single use. Failing to remove it is harmless because it expires anyway.
        runCatching { codeRef.delete().await() }
        return UserProfile(uid, familyId, expectedRole)
    }

    fun members(familyId: String): Flow<List<Member>> = callbackFlow {
        val registration: ListenerRegistration = db.collection(FAMILIES).document(familyId)
            .collection(MEMBERS)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val list = snapshot?.documents.orEmpty().mapNotNull { it.toMember() }
                    .sortedWith(compareByDescending<Member> { it.owner }.thenBy { it.role }.thenBy { it.name })
                trySend(list)
            }
        awaitClose { registration.remove() }
    }

    /** Only the family owner may do this; the security rules enforce it. */
    suspend fun setFullControl(familyId: String, memberUid: String, fullControl: Boolean) {
        db.collection(FAMILIES).document(familyId).collection(MEMBERS).document(memberUid)
            .update("fullControl", fullControl).await()
    }

    private fun DocumentSnapshot.toMember(): Member? {
        val role = Role.fromId(getString("role")) ?: return null
        return Member(
            uid = id,
            name = getString("name").orEmpty(),
            role = role,
            owner = getBoolean("owner") == true,
            fullControl = getBoolean("fullControl") == true,
        )
    }

    private companion object {
        const val USERS = "users"
        const val FAMILIES = "families"
        const val MEMBERS = "members"
        const val CODES = "pairingCodes"
    }
}
