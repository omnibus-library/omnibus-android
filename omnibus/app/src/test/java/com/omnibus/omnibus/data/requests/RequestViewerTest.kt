package com.omnibus.omnibus.data.requests

import com.omnibus.omnibus.data.auth.randomCurrentUser
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of

class RequestViewerTest : DescribeSpec({

    describe("the viewer a session makes") {

        it("is nobody when no user is known") {
            (null as com.omnibus.omnibus.data.auth.CurrentUser?).toRequestViewer() shouldBe NobodyViewer
        }

        it("gives the user's own permissions when book requests are on") {
            checkAll(Exhaustive.of(true, false), Exhaustive.of(true, false)) { start, choose ->
                val user = randomCurrentUser().copy(bookRequests = true, canStartRequests = start, canChooseRequestVersions = choose)

                user.toRequestViewer() shouldBe RequestViewer(user.id, canStart = start, canChoose = choose)
            }
        }

        it("may do nothing when book requests are off, whatever the stored permissions say") {
            val user = randomCurrentUser().copy(bookRequests = false, canStartRequests = true, canChooseRequestVersions = true)

            user.toRequestViewer() shouldBe RequestViewer(user.id, canStart = false, canChoose = false)
        }
    }
})
