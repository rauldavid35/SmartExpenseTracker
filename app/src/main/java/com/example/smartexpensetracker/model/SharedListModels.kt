package com.example.smartexpensetracker.model

/**
 * A shopping list shared between multiple users.
 *
 * Stored at:  shared_lists/{listId}
 *
 * `members` is a plain array of user UIDs (including the owner) so we can use
 * `whereArrayContains("members", myUid)` to query "all shared lists I belong to".
 */
data class SharedListData(
    val id: String           = "",
    val name: String         = "",
    val itemCount: Int       = 0,
    val createdAt: Long      = 0L,
    val ownerId: String      = "",
    val ownerEmail: String   = "",
    val members: List<String> = emptyList()
)

/**
 * An item inside a shared list.
 *
 * Stored at:  shared_lists/{listId}/items/{itemId}
 */
data class SharedListItem(
    val id: String        = "",
    val text: String      = "",
    val checked: Boolean  = false,
    val addedBy: String   = "",   // uid of the user who added it
    val addedAt: Long     = 0L
)

/**
 * A single-use invite code for joining a shared list.
 *
 * Stored at:  shared_list_invites/{code}   (the code is the document id)
 *
 * `used` is flipped to true inside a transaction the moment a user redeems it,
 * guaranteeing single-use semantics even with concurrent redeems.
 */
data class SharedListInvite(
    val listId: String     = "",
    val ownerId: String    = "",
    val ownerEmail: String = "",
    val listName: String   = "",
    val createdAt: Long    = 0L,
    val expiresAt: Long    = 0L,
    val used: Boolean      = false
)

/**
 * An email-based invitation. Created when the owner enters a recipient's
 * email; the recipient (if they have an account with that email) sees a
 * banner on the lists screen and can accept or decline.
 *
 * Stored at:  shared_list_email_invites/{autoId}
 */
data class SharedListEmailInvite(
    val id: String          = "",
    val inviteeEmail: String = "",   // lowercased
    val listId: String      = "",
    val ownerId: String     = "",
    val ownerEmail: String  = "",
    val listName: String    = "",
    val createdAt: Long     = 0L,
    val status: String      = "pending"   // pending | accepted | declined
)

/** Result types for invite redemption — keeps the UI simple. */
sealed class RedeemResult {
    data class Success(val listId: String, val listName: String) : RedeemResult()
    object NotFound      : RedeemResult()
    object Expired       : RedeemResult()
    object AlreadyUsed   : RedeemResult()
    object AlreadyMember : RedeemResult()
    data class Error(val message: String) : RedeemResult()
}