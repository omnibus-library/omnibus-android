package com.omnibus.omnibus.data.network

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of

class NetworkStatusTest : DescribeSpec({

    describe("deciding whether the device is online") {

        context("when the network has validated internet") {
            it("#21 TC-1: is online") {
                NetworkStatus.isOnlineAfter(NetworkEvent.CapabilitiesChanged(hasInternet = true, validated = true)) shouldBe true
            }
        }

        context("when any other event arrives") {
            it("#21 TC-1: is offline") {
                checkAll(
                    Exhaustive.of(
                        NetworkEvent.Available,
                        NetworkEvent.CapabilitiesChanged(hasInternet = true, validated = false),
                        NetworkEvent.CapabilitiesChanged(hasInternet = false, validated = true),
                        NetworkEvent.Lost,
                        NetworkEvent.Unavailable,
                    ),
                ) { event ->
                    NetworkStatus.isOnlineAfter(event) shouldBe false
                }
            }
        }
    }
})
