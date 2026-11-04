package com.omnibus.omnibus.feature.audiobook.player

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class PlayerMountPointsTest : DescribeSpec({

    describe("C11 shape") {

        // #165 TC-87, P-SCR-11
        it("#165 TC-87: defaults and members match the contract") {
            val actions = PlayerActions()
            actions.onOpenChapters.shouldBeNull()
            actions.onOpenSpeed.shouldBeNull()
            actions.onOpenSleep.shouldBeNull()
            actions.sleepPill.shouldBeNull()
            actions.onOpenCarMode.shouldBeNull()
            actions.onAddBookmark.shouldBeNull()
            actions.onOpenBookmarks.shouldBeNull()
            actions.syncHere.shouldBeNull()
            actions.onOpenBook.shouldBeNull()

            val button = PlayerToolButton(label = "Sync here", enabled = false, onClick = {})
            button.label shouldBe "Sync here"
            button.enabled shouldBe false
            button.disabledDescription.shouldBeNull()

            PlayerOverlay.entries shouldBe listOf(
                PlayerOverlay.Chapters,
                PlayerOverlay.Speed,
                PlayerOverlay.Sleep,
                PlayerOverlay.Bookmarks,
                PlayerOverlay.CarMode,
            )
        }
    }
})
