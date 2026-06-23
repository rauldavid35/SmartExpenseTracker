package com.example.smartexpensetracker.model


data class SharedListData(
    val id: String           = "",
    val name: String         = "",
    val itemCount: Int       = 0,
    val createdAt: Long      = 0L,
    val ownerId: String      = "",
    val ownerEmail: String   = "",
    val members: List<String> = emptyList()
)

data class SharedListItem(
    val id: String        = "",
    val text: String      = "",
    val checked: Boolean  = false,
    val addedBy: String   = "",   // uid of the user who added it
    val addedAt: Long     = 0L
)

data class SharedListInvite(
    val listId: String     = "",
    val ownerId: String    = "",
    val ownerEmail: String = "",
    val listName: String   = "",
    val createdAt: Long    = 0L,
    val expiresAt: Long    = 0L,
    val used: Boolean      = false
)

data class SharedListEmailInvite(
    val id: String          = "",
    val inviteeEmail: String = "",
    val listId: String      = "",
    val ownerId: String     = "",
    val ownerEmail: String  = "",
    val listName: String    = "",
    val createdAt: Long     = 0L,
    val status: String      = "pending"
)

sealed class RedeemResult {
    data class Success(val listId: String, val listName: String) : RedeemResult()
    object NotFound      : RedeemResult()
    object Expired       : RedeemResult()
    object AlreadyUsed   : RedeemResult()
    object AlreadyMember : RedeemResult()
    data class Error(val message: String) : RedeemResult()
}