package com.omnibus.omnibus.feature.libraries.switching

import com.omnibus.omnibus.feature.libraries.monogramOf
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of

class IlluminatedInitialTest : DescribeSpec({

    describe("the name beside the illuminated initial") {

        it("#369 TC-33: drops the first letter only when the initial stands for it") {
            checkAll(
                Exhaustive.of(
                    "Home" to "ome",
                    "home" to "ome",
                    "3rd shelf" to "3rd shelf",
                    "✠ 42" to "✠ 42",
                    "Évora" to "vora",
                ),
            ) { (name, expected) ->
                nameAfterInitial(name, monogramOf(name)) shouldBe expected
            }
        }
    }
})
