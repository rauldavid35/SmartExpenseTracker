package com.example.smartexpensetracker.repository

import com.example.smartexpensetracker.model.RedeemResult
import com.example.smartexpensetracker.model.SharedListData
import com.example.smartexpensetracker.model.SharedListEmailInvite
import com.example.smartexpensetracker.model.SharedListInvite
import com.example.smartexpensetracker.model.SharedListItem
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlin.random.Random

class SharedListRepository {

    private val db = FirebaseFirestore.getInstance()

    private val listsCol         = db.collection("shared_lists")
    private val invitesCol       = db.collection("shared_list_invites")
    private val emailInvitesCol  = db.collection("shared_list_email_invites")

    private val codeAlphabet = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
    private val codeLength   = 6
    private val inviteTtlMs  = 24L * 60L * 60L * 1000L   // 24 hours

    fun getSharedLists(userId: String): Flow<List<SharedListData>> = callbackFlow {
        val subscription = listsCol
            .whereArrayContains("members", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                if (snapshot != null) {
                    val lists = snapshot.documents.mapNotNull { doc ->
                        doc.toObject(SharedListData::class.java)?.copy(id = doc.id)
                    }.sortedByDescending { it.createdAt }
                    trySend(lists)
                }
            }
        awaitClose { subscription.remove() }
    }

    fun getItems(listId: String): Flow<List<SharedListItem>> = callbackFlow {
        android.util.Log.d("SharedList", "getItems subscribing to listId=$listId")
        val subscription = listsCol.document(listId).collection("items")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.e("SharedList", "Snapshot listener error", error)
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    android.util.Log.d("SharedList", "Snapshot received: ${snapshot.documents.size} docs")
                    val items = snapshot.documents.mapNotNull { doc ->
                        doc.toObject(SharedListItem::class.java)?.copy(id = doc.id)
                    }.sortedBy { it.addedAt }
                    trySend(items)
                }
            }
        awaitClose { subscription.remove() }
    }


    suspend fun createList(ownerId: String, ownerEmail: String, name: String): String {
        val list = SharedListData(
            name      = name,
            itemCount = 0,
            createdAt = System.currentTimeMillis(),
            ownerId   = ownerId,
            ownerEmail = ownerEmail,
            members   = listOf(ownerId)
        )
        val ref = listsCol.add(list).await()
        return ref.id
    }

    suspend fun renameList(listId: String, newName: String) {
        listsCol.document(listId).update("name", newName).await()
    }

    suspend fun deleteList(listId: String) {
        val items = listsCol.document(listId).collection("items").get().await()
        items.documents.forEach { it.reference.delete().await() }
        listsCol.document(listId).delete().await()
    }

    suspend fun leaveList(listId: String, userId: String) {
        // Skip if user is the owner
        val snap = listsCol.document(listId).get().await()
        val ownerId = snap.getString("ownerId") ?: return
        if (ownerId == userId) return
        listsCol.document(listId)
            .update("members", FieldValue.arrayRemove(userId))
            .await()
    }


    suspend fun addItem(listId: String, text: String, addedBy: String) {
        val item = SharedListItem(
            text    = text,
            checked = false,
            addedBy = addedBy,
            addedAt = System.currentTimeMillis()
        )
        listsCol.document(listId).collection("items").add(item).await()
        recountItems(listId)
    }

    suspend fun toggleItem(listId: String, itemId: String, checked: Boolean) {
        listsCol.document(listId).collection("items").document(itemId)
            .update("checked", checked)
            .await()
    }

    suspend fun deleteItem(listId: String, itemId: String) {
        listsCol.document(listId).collection("items").document(itemId).delete().await()
        recountItems(listId)
    }

    private suspend fun recountItems(listId: String) {
        val count = listsCol.document(listId).collection("items").get().await().size()
        listsCol.document(listId).update("itemCount", count).await()
    }

    suspend fun generateInviteCode(
        listId: String,
        ownerId: String,
        ownerEmail: String,
        listName: String
    ): String {
        val now = System.currentTimeMillis()
        repeat(5) {
            val code = randomCode()
            val docRef = invitesCol.document(code)
            val success = db.runTransaction { txn ->
                val existing = txn.get(docRef)
                if (existing.exists()) return@runTransaction false
                txn.set(docRef, SharedListInvite(
                    listId     = listId,
                    ownerId    = ownerId,
                    ownerEmail = ownerEmail,
                    listName   = listName,
                    createdAt  = now,
                    expiresAt  = now + inviteTtlMs,
                    used       = false
                ))
                true
            }.await()
            if (success == true) return code
        }
        throw IllegalStateException("Could not generate a unique invite code")
    }

    suspend fun redeemInviteCode(code: String, userId: String): RedeemResult {
        val normalized = code.trim().uppercase()
        if (normalized.length != codeLength) return RedeemResult.NotFound

        val inviteRef = invitesCol.document(normalized)
        return try {
            db.runTransaction { txn ->
                val inviteSnap = txn.get(inviteRef)
                if (!inviteSnap.exists()) return@runTransaction RedeemResult.NotFound

                val invite = inviteSnap.toObject(SharedListInvite::class.java)
                    ?: return@runTransaction RedeemResult.NotFound

                if (invite.used) return@runTransaction RedeemResult.AlreadyUsed
                if (System.currentTimeMillis() > invite.expiresAt)
                    return@runTransaction RedeemResult.Expired

                val listRef = listsCol.document(invite.listId)

                txn.update(inviteRef, "used", true)
                txn.update(listRef, "members", FieldValue.arrayUnion(userId))

                RedeemResult.Success(invite.listId, invite.listName)
            }.await()
        } catch (e: Exception) {
            RedeemResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun createEmailInvite(
        listId: String,
        ownerId: String,
        ownerEmail: String,
        listName: String,
        inviteeEmail: String
    ) {
        val invite = SharedListEmailInvite(
            inviteeEmail = inviteeEmail.trim().lowercase(),
            listId       = listId,
            ownerId      = ownerId,
            ownerEmail   = ownerEmail,
            listName     = listName,
            createdAt    = System.currentTimeMillis(),
            status       = "pending"
        )
        emailInvitesCol.add(invite).await()
    }

    fun getPendingInvitesForEmail(email: String): Flow<List<SharedListEmailInvite>> = callbackFlow {
        val lower = email.trim().lowercase()
        val subscription = emailInvitesCol
            .whereEqualTo("inviteeEmail", lower)
            .whereEqualTo("status", "pending")
            .addSnapshotListener { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                if (snapshot != null) {
                    val invites = snapshot.documents.mapNotNull { doc ->
                        doc.toObject(SharedListEmailInvite::class.java)?.copy(id = doc.id)
                    }
                    trySend(invites)
                }
            }
        awaitClose { subscription.remove() }
    }

    suspend fun acceptEmailInvite(inviteId: String, userId: String): RedeemResult {
        val inviteRef = emailInvitesCol.document(inviteId)
        return try {
            db.runTransaction { txn ->
                val inviteSnap = txn.get(inviteRef)
                if (!inviteSnap.exists()) return@runTransaction RedeemResult.NotFound

                val invite = inviteSnap.toObject(SharedListEmailInvite::class.java)
                    ?: return@runTransaction RedeemResult.NotFound

                if (invite.status != "pending") return@runTransaction RedeemResult.AlreadyUsed

                val listRef = listsCol.document(invite.listId)

                txn.update(inviteRef, "status", "accepted")
                txn.update(listRef, "members", FieldValue.arrayUnion(userId))

                RedeemResult.Success(invite.listId, invite.listName)
            }.await()
        } catch (e: Exception) {
            RedeemResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun declineEmailInvite(inviteId: String) {
        emailInvitesCol.document(inviteId).update("status", "declined").await()
    }


    private fun randomCode(): String =
        (1..codeLength).map { codeAlphabet[Random.nextInt(codeAlphabet.length)] }.joinToString("")
}