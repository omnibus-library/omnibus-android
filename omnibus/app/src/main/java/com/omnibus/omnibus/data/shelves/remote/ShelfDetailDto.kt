package com.omnibus.omnibus.data.shelves.remote

import com.omnibus.omnibus.data.library.Shelf
import com.omnibus.omnibus.data.library.ShelfKind
import com.omnibus.omnibus.data.library.ShelfVisibility
import com.omnibus.omnibus.data.shelves.ShelfDetail
import com.omnibus.omnibus.data.shelves.ShelfMatchMode
import com.omnibus.omnibus.data.shelves.ShelfRule
import com.omnibus.omnibus.data.shelves.ShelfRuleField
import com.omnibus.omnibus.data.shelves.ShelfRuleOp
import com.omnibus.omnibus.data.shelves.ShelfRules
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** `GET api/shelves/{id}`: the shelf's identity, its match mode and its rules. */
@Serializable
internal data class ShelfDetailDto(
    val id: Long = 0,
    @SerialName("owner_user_id")
    val ownerUserId: Long = 0,
    @SerialName("owner_username")
    val ownerUsername: String = "",
    @SerialName("owner_has_avatar")
    val ownerHasAvatar: Boolean = false,
    val kind: String = "",
    val name: String = "",
    val description: String? = null,
    val visibility: String = "private",
    val accent: String? = null,
    @SerialName("book_count")
    val bookCount: Long = 0,
    @SerialName("match_mode")
    val matchMode: String? = null,
    val rules: List<ShelfRuleDto> = emptyList(),
) {
    /** The match mode and rules alone, as the rule text reads them. */
    fun toDomain(): ShelfRules = ShelfRules(
        matchMode = when (matchMode) {
            "all" -> ShelfMatchMode.All
            "any" -> ShelfMatchMode.Any
            else -> null
        },
        rules = rules.map { it.toDomain() },
    )

    /** The whole shelf, or null for a kind this app doesn't know, so an unknown kind never offers edit actions. */
    fun toDetail(): ShelfDetail? {
        val shelfKind = when (kind) {
            "smart" -> ShelfKind.Smart
            "manual" -> ShelfKind.Manual
            "wishlist" -> ShelfKind.Wishlist
            else -> return null
        }
        return ShelfDetail(
            shelf = Shelf(
                id = id,
                ownerUserId = ownerUserId,
                ownerUsername = ownerUsername,
                kind = shelfKind,
                name = name,
                visibility = if (visibility == "public") ShelfVisibility.Public else ShelfVisibility.Private,
                accent = accent,
                bookCount = bookCount,
                ownerHasAvatar = ownerHasAvatar,
            ),
            description = description?.takeIf { it.isNotBlank() },
            rules = toDomain(),
        )
    }
}

/**
 * One rule of a smart shelf. Tokens are kept as text, so an unknown one becomes a null enum.
 * Also the request shape the editor sends.
 */
@Serializable
internal data class ShelfRuleDto(
    val field: String = "",
    val op: String = "",
    val value: String = "",
) {
    fun toDomain(): ShelfRule = ShelfRule(
        field = ShelfRuleField.fromWire(field),
        op = ShelfRuleOp.fromWire(op),
        value = value,
    )

    companion object {

        /** The wire form of [rule], or null when its field or op is one this app doesn't know. */
        fun of(rule: ShelfRule): ShelfRuleDto? {
            val field = rule.field ?: return null
            val op = rule.op ?: return null
            return ShelfRuleDto(field = field.wire, op = op.wire, value = rule.value)
        }
    }
}
