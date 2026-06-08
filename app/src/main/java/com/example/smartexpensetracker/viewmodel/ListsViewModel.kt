package com.example.smartexpensetracker.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartexpensetracker.model.ShoppingItem
import com.example.smartexpensetracker.model.ShoppingListData
import com.example.smartexpensetracker.repository.ShoppingListRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ListsViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()
    private val repository = ShoppingListRepository()

    private val _shoppingLists = MutableStateFlow<List<ShoppingListData>>(emptyList())
    val shoppingLists: StateFlow<List<ShoppingListData>> = _shoppingLists.asStateFlow()

    private val _currentListItems = MutableStateFlow<List<ShoppingItem>>(emptyList())

    private var currentListJob: kotlinx.coroutines.Job? = null
    val currentListItems: StateFlow<List<ShoppingItem>> = _currentListItems.asStateFlow()

    init {
        auth.addAuthStateListener { firebaseAuth ->
            val uid = firebaseAuth.currentUser?.uid
            if (uid == null) {
                _shoppingLists.value = emptyList()
                _currentListItems.value = emptyList()
            } else {
                fetchLists()
            }
        }
    }

    private fun fetchLists() {
        val userId = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                repository.getLists(userId).collect { lists ->
                    _shoppingLists.value = lists
                }
            } catch (_: Exception) {}
        }
    }

    fun addList(name: String, onComplete: ((String) -> Unit)? = null) {
        val userId = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            val newId = repository.addList(userId, name)
            onComplete?.invoke(newId)
        }
    }

    fun renameList(id: String, newName: String) {
        val userId = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            repository.renameList(userId, id, newName)
        }
    }

    fun deleteList(id: String) {
        val userId = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            repository.deleteList(userId, id)
        }
    }

    fun fetchListItems(listId: String) {
        val userId = auth.currentUser?.uid ?: return
        currentListJob?.cancel()
        currentListJob = viewModelScope.launch {
            try {
                repository.getItems(userId, listId).collect { items ->
                    _currentListItems.value = items
                    repository.updateItemCount(userId, listId, items.size)
                }
            } catch (_: Exception) {}
        }
    }

    fun addListItem(listId: String, text: String) {
        val userId = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            repository.addItem(userId, listId, text)
        }
    }

    fun toggleListItem(listId: String, itemId: String, isChecked: Boolean) {
        val userId = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            repository.toggleItem(userId, listId, itemId, isChecked)
        }
    }

    fun deleteListItem(listId: String, itemId: String) {
        val userId = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            repository.deleteItem(userId, listId, itemId)
        }
    }
}