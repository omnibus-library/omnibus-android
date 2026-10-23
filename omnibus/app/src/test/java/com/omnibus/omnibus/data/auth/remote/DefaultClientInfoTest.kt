package com.omnibus.omnibus.data.auth.remote

import android.content.Context
import android.content.pm.PackageInfo
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk

class DefaultClientInfoTest : DescribeSpec({

    fun contextWithVersion(versionName: String?): Context {
        val packageInfo = mockk<PackageInfo>().apply { this.versionName = versionName }
        return mockk {
            every { packageName } returns PACKAGE
            every { packageManager.getPackageInfo(PACKAGE, 0) } returns packageInfo
        }
    }

    describe("clientVersion") {

        it("is the installed package's version name") {
            DefaultClientInfo(contextWithVersion("1.2.3")).clientVersion shouldBe "1.2.3"
        }

        it("falls back to 1.0 when the package has no version name") {
            DefaultClientInfo(contextWithVersion(null)).clientVersion shouldBe "1.0"
        }
    }

    describe("deviceName") {

        // Build.MODEL is null in the JVM's android.jar stub, so only the fallback is reachable here.
        it("falls back to Android when the model is unavailable") {
            DefaultClientInfo(contextWithVersion("1.2.3")).deviceName shouldBe "Android"
        }
    }
}) {
    private companion object {
        const val PACKAGE = "com.omnibus.omnibus"
    }
}
