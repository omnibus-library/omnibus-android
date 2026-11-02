package com.omnibus.omnibus.data.shelves.remote

import com.omnibus.omnibus.data.shelves.ShelfMatchMode
import com.omnibus.omnibus.data.shelves.ShelfRule
import com.omnibus.omnibus.data.shelves.ShelfRuleField
import com.omnibus.omnibus.data.shelves.ShelfRuleOp
import com.omnibus.omnibus.data.shelves.ShelfRules
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** `GET api/shelves/{id}`, reduced to the match mode and rules the sheet reads. */
@Serializable
internal data class ShelfDetailDto(
    @SerialName("match_mode")
    val matchMode: String? = null,
    val rules: List<ShelfRuleDto> = emptyList(),
) {
    fun toDomain(): ShelfRules = ShelfRules(
        matchMode = when (matchMode) {
            "all" -> ShelfMatchMode.All
            "any" -> ShelfMatchMode.Any
            else -> null
        },
        rules = rules.map { it.toDomain() },
    )
}

/** One rule of a smart shelf. Tokens are kept as text, so an unknown one becomes a null enum. */
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
}
