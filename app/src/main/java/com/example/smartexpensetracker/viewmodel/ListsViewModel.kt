package com.example.smartexpensetracker.viewmodel

import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// Data model for the List (Header)
data class ShoppingListData(
    val id: String = "",
    val name: String = "",
    val itemCount: Int = 0,
    val date: Long = 0L
)

// UPDATED: Renamed isChecked -> checked to fix Firestore mapping
data class ShoppingItem(
    val id: String = "",
    val text: String = "",
    val checked: Boolean = false
)

class ListsViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    // Lists
    private val _shoppingLists = MutableStateFlow<List<ShoppingListData>>(emptyList())
    val shoppingLists: StateFlow<List<ShoppingListData>> = _shoppingLists.asStateFlow()

    // Current List Items (Sub-collection)
    private val _currentListItems = MutableStateFlow<List<ShoppingItem>>(emptyList())
    val currentListItems: StateFlow<List<ShoppingItem>> = _currentListItems.asStateFlow()

    init {
        fetchLists()
    }

    // --- List Management ---
    private fun fetchLists() {
        val userId = auth.currentUser?.uid ?: return
        db.collection("users").document(userId).collection("shopping_lists")
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    val list = snapshot.documents.mapNotNull { doc ->
                        doc.toObject(ShoppingListData::class.java)?.copy(id = doc.id)
                    }
                    _shoppingLists.value = list
                }
            }
    }

    // UPDATED: Added callback to return the new List ID
    fun addList(name: String, onComplete: ((String) -> Unit)? = null) {
        val userId = auth.currentUser?.uid ?: return
        val list = ShoppingListData(name = name, itemCount = 0, date = System.currentTimeMillis())

        db.collection("users").document(userId).collection("shopping_lists").add(list)
            .addOnSuccessListener { documentReference ->
                onComplete?.invoke(documentReference.id)
            }
    }

    fun renameList(id: String, newName: String) {
        val userId = auth.currentUser?.uid ?: return
        db.collection("users").document(userId).collection("shopping_lists").document(id).update("name", newName)
    }

    fun deleteList(id: String) {
        val userId = auth.currentUser?.uid ?: return
        db.collection("users").document(userId).collection("shopping_lists").document(id).delete()
    }

    // --- Item Management (Inside a List) ---
    fun fetchListItems(listId: String) {
        val userId = auth.currentUser?.uid ?: return
        db.collection("users").document(userId)
            .collection("shopping_lists").document(listId).collection("items")
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    val items = snapshot.documents.mapNotNull { doc ->
                        doc.toObject(ShoppingItem::class.java)?.copy(id = doc.id)
                    }
                    _currentListItems.value = items

                    // Update item count in the parent list
                    updateItemCount(listId, items.size)
                }
            }
    }

    fun addListItem(listId: String, text: String) {
        val userId = auth.currentUser?.uid ?: return
        // UPDATED: Use 'checked'
        val item = ShoppingItem(text = text, checked = false)
        db.collection("users").document(userId)
            .collection("shopping_lists").document(listId).collection("items").add(item)
    }

    fun toggleListItem(listId: String, itemId: String, isChecked: Boolean) {
        val userId = auth.currentUser?.uid ?: return
        db.collection("users").document(userId)
            .collection("shopping_lists").document(listId).collection("items").document(itemId)
            .update("checked", isChecked) // UPDATED: Field name is now "checked"
    }

    fun deleteListItem(listId: String, itemId: String) {
        val userId = auth.currentUser?.uid ?: return
        db.collection("users").document(userId)
            .collection("shopping_lists").document(listId).collection("items").document(itemId).delete()
    }

    private fun updateItemCount(listId: String, count: Int) {
        val userId = auth.currentUser?.uid ?: return
        db.collection("users").document(userId).collection("shopping_lists").document(listId)
            .update("itemCount", count)
    }
}