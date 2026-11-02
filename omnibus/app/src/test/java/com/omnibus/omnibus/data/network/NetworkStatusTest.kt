package com.omnibus.omnibus.data.network

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class NetworkStatusTest : DescribeSpec({

    describe("isOnlineAfter") {
        listOf(
            NetworkEvent.Available to false,
            NetworkEvent.CapabilitiesChanged(hasInternet = true, validated = true) to true,
            NetworkEvent.CapabilitiesChanged(hasInternet = true, validated = false) to false,
            NetworkEvent.CapabilitiesChanged(hasInternet = false, validated = true) to false,
            NetworkEvent.Lost to false,
            NetworkEvent.Unavailable to false,
        ).forEach { (event, expected) ->
            it("#21 TC-1: $event is ${if (expected) "online" else "offline"}") {
                NetworkStatus.isOnlineAfter(event) shouldBe expected
            }
        }
    }
})
