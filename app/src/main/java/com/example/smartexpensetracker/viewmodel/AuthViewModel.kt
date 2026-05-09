package com.example.smartexpensetracker.viewmodel

import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AuthState(
    val user: FirebaseUser? = null,
    val isLoading: Boolean  = false,
    val error: String?      = null,
    val message: String?    = null   // success messages (e.g. "Reset email sent")
)

class AuthViewModel : ViewModel() {

    private val auth = FirebaseAuth.getInstance()
    private val db   = FirebaseFirestore.getInstance()

    private val _authState = MutableStateFlow(AuthState())
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    init {
        _authState.value = AuthState(user = auth.currentUser)
        auth.addAuthStateListener { fa ->
            _authState.value = _authState.value.copy(user = fa.currentUser)
        }
    }

    // ── Login ─────────────────────────────────────────────────────────────────

    fun login(email: String, pass: String) {
        _authState.value = _authState.value.copy(isLoading = true, error = null)
        auth.signInWithEmailAndPassword(email, pass)
            .addOnCompleteListener { task ->
                _authState.value = if (task.isSuccessful) {
                    _authState.value.copy(isLoading = false, user = auth.currentUser)
                } else {
                    _authState.value.copy(isLoading = false, error = task.exception?.message)
                }
            }
    }

    // ── Sign up (optionally stores recovery contact in Firestore) ─────────────

    fun signup(email: String, pass: String, recoveryContact: String? = null) {
        _authState.value = _authState.value.copy(isLoading = true, error = null)
        auth.createUserWithEmailAndPassword(email, pass)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val uid = auth.currentUser?.uid
                    if (uid != null && recoveryContact != null) {
                        db.collection("users").document(uid).set(
                            mapOf(
                                "primaryEmail"    to email,
                                "recoveryContact" to (recoveryContact ?: "")
                            ), SetOptions.merge()
                        )
                    }
                    _authState.value = _authState.value.copy(isLoading = false, user = auth.currentUser)
                } else {
                    _authState.value = _authState.value.copy(isLoading = false, error = task.exception?.message)
                }
            }
    }

    // ── Forgot password ───────────────────────────────────────────────────────

    fun sendPasswordReset(email: String) {
        _authState.value = _authState.value.copy(isLoading = true, error = null)
        // First check if this is a recovery contact for any user
        db.collection("users")
            .whereEqualTo("recoveryContact", email)
            .limit(1)
            .get()
            .addOnSuccessListener { snapshot ->
                val primaryEmail = snapshot.documents.firstOrNull()
                    ?.getString("primaryEmail")
                val targetEmail = primaryEmail ?: email
                auth.sendPasswordResetEmail(targetEmail)
                    .addOnCompleteListener { task ->
                        _authState.value = _authState.value.copy(
                            isLoading = false,
                            message = if (task.isSuccessful) "Reset link sent to your primary email" else null,
                            error   = if (!task.isSuccessful) task.exception?.message else null
                        )
                    }
            }
            .addOnFailureListener {
                // Fall back to direct reset
                auth.sendPasswordResetEmail(email)
                    .addOnCompleteListener { task ->
                        _authState.value = _authState.value.copy(isLoading = false)
                    }
            }
    }

    // ── Update password (from Profile screen) ─────────────────────────────────

    fun updatePassword(newPassword: String, onResult: (Boolean, String?) -> Unit) {
        auth.currentUser?.updatePassword(newPassword)
            ?.addOnCompleteListener { task ->
                onResult(task.isSuccessful, task.exception?.message)
            } ?: onResult(false, "Not signed in")
    }

    // ── Update recovery contact ───────────────────────────────────────────────

    fun updateRecoveryContact(value: String, onResult: (Boolean) -> Unit) {
        val uid = auth.currentUser?.uid ?: return onResult(false)
        db.collection("users").document(uid)
            .set(mapOf("recoveryContact" to value), com.google.firebase.firestore.SetOptions.merge())
            .addOnCompleteListener { onResult(it.isSuccessful) }
    }

    fun fetchRecoveryContact(onResult: (String?) -> Unit) {
        val uid = auth.currentUser?.uid ?: return onResult(null)
        db.collection("users").document(uid).get()
            .addOnSuccessListener { onResult(it.getString("recoveryContact")) }
            .addOnFailureListener { onResult(null) }
    }

    // ── Logout ────────────────────────────────────────────────────────────────

    fun logout() {
        auth.signOut()
        _authState.value = AuthState(user = null)
    }
}