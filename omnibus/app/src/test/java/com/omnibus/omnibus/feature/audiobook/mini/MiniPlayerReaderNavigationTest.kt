package com.omnibus.omnibus.feature.audiobook.mini

import androidx.navigation3.runtime.NavKey
import com.omnibus.omnibus.closePlayer
import com.omnibus.omnibus.feature.audiobook.player.PlayerRoute
import com.omnibus.omnibus.feature.reader.ui.ReaderRoute
import com.omnibus.omnibus.feature.shell.ui.SignedInShellRoute
import com.omnibus.omnibus.openPlayer
import com.omnibus.omnibus.openReader
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactly

class MiniPlayerReaderNavigationTest : DescribeSpec({

    describe("reader dock and book button") {

        // P-MINI-4, P-MINI-5
        it("#172 TC-46: the dock opens the player over the reader, collapse returns to it, and the book button lands on the reader") {
            val reader = ReaderRoute("u")
            val player = PlayerRoute("u", null, null)
            val stack = mutableListOf<NavKey>(SignedInShellRoute, reader)

            // The dock's expand, as MainActivity passes it.
            stack.openPlayer("u", null)
            stack shouldContainExactly listOf(SignedInShellRoute, reader, player)

            // Collapse pops back to the same reader entry.
            stack.closePlayer(player)
            stack shouldContainExactly listOf(SignedInShellRoute, reader)

            // The book button: close the player, then open the reader on the same book. The reader is already on top.
            stack.openPlayer("u", null)
            stack.closePlayer(player)
            stack.openReader("u", null)
            stack shouldContainExactly listOf(SignedInShellRoute, reader)
        }
    }
})
