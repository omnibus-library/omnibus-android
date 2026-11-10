package com.omnibus.omnibus.feature.audiobook.mini

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import com.omnibus.omnibus.feature.bookdetail.stub.BookDetailPlaceholderRoute
import com.omnibus.omnibus.feature.bookdetail.ui.BookDetailRoute
import com.omnibus.omnibus.feature.shell.ShellTab
import com.omnibus.omnibus.testing.randomString
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.Exhaustive
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.collection

class MiniPlayerPlacementTest : DescribeSpec({

    describe("hiding the bar in the shell") {
        // P-MINI-1
        it("#172 TC-13: hides it under Book Detail") {
            shellHidesMiniPlayer(BookDetailRoute(randomString())) shouldBe true
        }

        // P-MINI-1
        it("#172 TC-14: shows it over every tab root, the Book Detail placeholder or an empty stack") {
            val tops: List<NavKey?> = ShellTab.entries.map { it.root } + BookDetailPlaceholderRoute(randomString()) + null

            checkAll(Exhaustive.collection(tops)) { top ->
                shellHidesMiniPlayer(top) shouldBe false
            }
        }
    }

    describe("sizing the floating card") {
        // #271 TC-1 (R1, R2)
        it("#271 TC-1: the bar placement is 12dp a side, uncapped, with an 8dp gap") {
            MiniPlayerFloatPlacement.Bar shouldBe MiniPlayerFloatPlacement(sideMargin = 12.dp, maxWidth = Dp.Infinity, bottomGap = 8.dp)
        }

        // #271 TC-1 (R2)
        it("#271 TC-1: the rail placement is 16dp a side, capped at 560dp, with a 16dp gap") {
            MiniPlayerFloatPlacement.Rail shouldBe MiniPlayerFloatPlacement(sideMargin = 16.dp, maxWidth = 560.dp, bottomGap = 16.dp)
        }

        // #271 TC-1 (R1)
        it("#271 TC-1: the bar card is the available width less 12dp a side, never below zero, for any width") {
            checkAll(Arb.int(0..2000)) { available ->
                MiniPlayerFloatPlacement.Bar.cardWidth(available.dp) shouldBe maxOf(0f, available - 24f).dp
            }
        }

        // #271 TC-1 (R2)
        it("#271 TC-1: the rail card is the available width less 16dp a side, capped at 560dp, never below zero, for any width") {
            checkAll(Arb.int(0..2000)) { available ->
                MiniPlayerFloatPlacement.Rail.cardWidth(available.dp) shouldBe minOf(560f, maxOf(0f, available - 32f)).dp
            }
        }

        // #271 TC-1 (R1, R2)
        it("#271 TC-1: the card width matches the worked examples, including the cap and the floor") {
            MiniPlayerFloatPlacement.Bar.cardWidth(400.dp) shouldBe 376.dp
            MiniPlayerFloatPlacement.Rail.cardWidth(500.dp) shouldBe 468.dp
            MiniPlayerFloatPlacement.Rail.cardWidth(592.dp) shouldBe 560.dp
            MiniPlayerFloatPlacement.Rail.cardWidth(900.dp) shouldBe 560.dp
            MiniPlayerFloatPlacement.Bar.cardWidth(20.dp) shouldBe 0.dp
        }
    }
})
