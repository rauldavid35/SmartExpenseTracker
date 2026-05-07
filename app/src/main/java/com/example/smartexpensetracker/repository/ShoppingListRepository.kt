package com.example.smartexpensetracker.repository

import com.example.smartexpensetracker.model.ShoppingItem
import com.example.smartexpensetracker.model.ShoppingListData
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class ShoppingListRepository {
    private val db = FirebaseFirestore.getInstance()

    private fun getListsCollection(userId: String) =
        db.collection("users").document(userId).collection("shopping_lists")

    private fun getItemsCollection(userId: String, listId: String) =
        getListsCollection(userId).document(listId).collection("items")

    // --- Lists ---

    fun getLists(userId: String): Flow<List<ShoppingListData>> = callbackFlow {
        val subscription = getListsCollection(userId)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    val lists = snapshot.documents.mapNotNull { doc ->
                        doc.toObject(ShoppingListData::class.java)?.copy(id = doc.id)
                    }
                    trySend(lists)
                }
            }
        awaitClose { subscription.remove() }
    }

    suspend fun addList(userId: String, name: String): String {
        val list = ShoppingListData(
            name = name,
            itemCount = 0,
            date = System.currentTimeMillis()
        )
        val ref = getListsCollection(userId).add(list).await()
        return ref.id
    }

    suspend fun renameList(userId: String, listId: String, newName: String) {
        getListsCollection(userId).document(listId).update("name", newName).await()
    }

    suspend fun deleteList(userId: String, listId: String) {
        getListsCollection(userId).document(listId).delete().await()
    }

    suspend fun updateItemCount(userId: String, listId: String, count: Int) {
        getListsCollection(userId).document(listId).update("itemCount", count).await()
    }

    // --- Items ---

    fun getItems(userId: String, listId: String): Flow<List<ShoppingItem>> = callbackFlow {
        val subscription = getItemsCollection(userId, listId)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    val items = snapshot.documents.mapNotNull { doc ->
                        doc.toObject(ShoppingItem::class.java)?.copy(id = doc.id)
                    }
                    trySend(items)
                }
            }
        awaitClose { subscription.remove() }
    }

    suspend fun addItem(userId: String, listId: String, text: String) {
        val item = ShoppingItem(text = text, checked = false)
        getItemsCollection(userId, listId).add(item).await()
    }

    suspend fun toggleItem(userId: String, listId: String, itemId: String, checked: Boolean) {
        getItemsCollection(userId, listId).document(itemId)
            .update("checked", checked).await()
    }

    suspend fun deleteItem(userId: String, listId: String, itemId: String) {
        getItemsCollection(userId, listId).document(itemId).delete().await()
    }
}