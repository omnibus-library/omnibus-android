package com.omnibus.omnibus.feature.bookdetail.tabs

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.InspectorInfo
import com.omnibus.omnibus.feature.bookdetail.BookDetailAnchor

/** Receives the positions of the anchored elements in a tab panel. */
internal interface BookDetailAnchorRegistry {
    fun register(anchor: BookDetailAnchor, coordinates: LayoutCoordinates)
    fun unregister(anchor: BookDetailAnchor, coordinates: LayoutCoordinates)
}

/** The registry a tab panel provides to its sections. Null outside a panel, where anchors are inert. */
internal val LocalBookDetailAnchorRegistry = staticCompositionLocalOf<BookDetailAnchorRegistry?> { null }

/** The element behind `Modifier.bookDetailAnchor`. Equal for the same anchor, so recomposition reuses its node. */
internal data class BookDetailAnchorElement(val anchor: BookDetailAnchor) : ModifierNodeElement<BookDetailAnchorNode>() {
    override fun create() = BookDetailAnchorNode(anchor)

    override fun update(node: BookDetailAnchorNode) {
        node.update(anchor)
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "bookDetailAnchor"
        properties["anchor"] = anchor
    }
}

/**
 * Registers its layout with the enclosing panel's registry while it is placed, and unregisters when it leaves
 * composition or its anchor changes. Registers only when the coordinates change, so a layout pass writes nothing.
 */
internal class BookDetailAnchorNode(private var anchor: BookDetailAnchor) :
    Modifier.Node(), CompositionLocalConsumerModifierNode, GlobalPositionAwareModifierNode {

    private var registry: BookDetailAnchorRegistry? = null
    private var coordinates: LayoutCoordinates? = null

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        val registry = currentValueOf(LocalBookDetailAnchorRegistry) ?: return // inert without a host
        if (registry === this.registry && coordinates === this.coordinates) return
        release()
        registry.register(anchor, coordinates)
        this.registry = registry
        this.coordinates = coordinates
    }

    fun update(anchor: BookDetailAnchor) {
        if (anchor != this.anchor) {
            release()
            this.anchor = anchor
        }
    }

    override fun onDetach() = release()

    private fun release() {
        val r = registry
        val c = coordinates
        if (r != null && c != null) r.unregister(anchor, c)
        registry = null
        coordinates = null
    }
}
