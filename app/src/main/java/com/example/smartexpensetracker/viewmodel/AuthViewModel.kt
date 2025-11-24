package com.example.smartexpensetracker.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AuthState(
    val user: FirebaseUser? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

class AuthViewModel : ViewModel() {
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    private val _authState = MutableStateFlow(AuthState())
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    init {
        // Check current login status immediately
        _authState.value = AuthState(user = auth.currentUser)

        // Listen for changes (e.g. if session expires)
        auth.addAuthStateListener { firebaseAuth ->
            _authState.value = _authState.value.copy(user = firebaseAuth.currentUser)
        }
    }

    fun login(email: String, pass: String) {
        _authState.value = _authState.value.copy(isLoading = true, error = null)
        auth.signInWithEmailAndPassword(email, pass)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    _authState.value = _authState.value.copy(isLoading = false, user = auth.currentUser)
                } else {
                    _authState.value = _authState.value.copy(isLoading = false, error = task.exception?.message)
                }
            }
    }

    fun signup(email: String, pass: String) {
        _authState.value = _authState.value.copy(isLoading = true, error = null)
        auth.createUserWithEmailAndPassword(email, pass)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    _authState.value = _authState.value.copy(isLoading = false, user = auth.currentUser)
                } else {
                    _authState.value = _authState.value.copy(isLoading = false, error = task.exception?.message)
                }
            }
    }

    fun logout() {
        auth.signOut()
        _authState.value = AuthState(user = null)
    }
}