package com.omnibus.omnibus.feature.bookdetail.more.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.content.FileProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.omnibus.omnibus.data.export.ExportedFile
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import java.io.File

class ExportShareTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val authority get() = context.packageName + ".bookexport"
    private val folder get() = File(context.cacheDir, "book-exports/test")

    @After
    fun deleteFiles() {
        folder.deleteRecursively()
    }

    /** Writes [bytes] to a file named [name] in the test folder and describes it as [mime]. */
    private fun exportedFile(name: String, mime: String, bytes: ByteArray = byteArrayOf(1, 2, 3)): ExportedFile {
        folder.mkdirs()
        return ExportedFile(File(folder, name).apply { writeBytes(bytes) }, mime)
    }

    /** The share intent the chooser wraps. */
    @Suppress("DEPRECATION")
    private fun innerOf(chooser: Intent): Intent = requireNotNull(chooser.getParcelableExtra(Intent.EXTRA_INTENT))

    // TC-78
    @Test
    fun issue118_tc78_singleFileSharesOneContentUriWithAReadGrant() {
        val chooser = exportChooserIntent(context, listOf(exportedFile("Dune.epub", "application/epub+zip")))
        val inner = innerOf(chooser)

        assertEquals(Intent.ACTION_SEND, inner.action)
        assertEquals("application/epub+zip", inner.type)
        val stream = uriExtra(inner, Intent.EXTRA_STREAM)
        assertNotNull(stream)
        assertTrue("uri was $stream", stream.toString().startsWith("content://$authority/"))
        assertTrue("grant flag missing", (inner.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0)
        assertEquals(stream, inner.clipData?.getItemAt(0)?.uri)
    }

    // TC-79
    @Test
    fun issue118_tc79_severalFilesShareOneMultipleSendWithTheRightType() {
        val threeParts = listOf(
            exportedFile("p1.m4b", "audio/mp4"),
            exportedFile("p2.m4b", "audio/mp4"),
            exportedFile("p3.m4b", "audio/mp4"),
        )
        val sameType = innerOf(exportChooserIntent(context, threeParts))
        assertEquals(Intent.ACTION_SEND_MULTIPLE, sameType.action)
        assertEquals("audio/mp4", sameType.type)
        assertEquals(listOf("p1.m4b", "p2.m4b", "p3.m4b"), uriListExtra(sameType).map { it.lastPathSegment })
        assertEquals(3, sameType.clipData?.itemCount)
        assertTrue("grant flag missing", (sameType.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0)

        val mixedAudio = innerOf(
            exportChooserIntent(
                context,
                listOf(exportedFile("a1.m4b", "audio/mp4"), exportedFile("a2.mp3", "audio/mpeg")),
            ),
        )
        assertEquals(Intent.ACTION_SEND_MULTIPLE, mixedAudio.action)
        assertEquals("audio/*", mixedAudio.type)

        val mixedKinds = innerOf(
            exportChooserIntent(
                context,
                listOf(exportedFile("Dune.epub", "application/epub+zip"), exportedFile("b.m4b", "audio/mp4")),
            ),
        )
        assertEquals(Intent.ACTION_SEND_MULTIPLE, mixedKinds.action)
        assertEquals("*/*", mixedKinds.type)
    }

    // TC-80
    @Test
    fun issue118_tc80_newTaskFlagIsSetOnlyForANonActivityContext() {
        val file = listOf(exportedFile("Dune.epub", "application/epub+zip"))

        val fromActivity = exportChooserIntent(rule.activity, file)
        val fromApplication = exportChooserIntent(context, file)

        assertEquals(0, fromActivity.flags and Intent.FLAG_ACTIVITY_NEW_TASK)
        assertTrue("application context needs NEW_TASK", (fromApplication.flags and Intent.FLAG_ACTIVITY_NEW_TASK) != 0)
    }

    // TC-81
    @Test
    fun issue118_tc81_providerServesTheCacheFolderAndRefusesOtherFiles() {
        val bytes = byteArrayOf(9, 8, 7, 6)
        val file = exportedFile("Dune.epub", "application/epub+zip", bytes)
        val uri = FileProvider.getUriForFile(context, authority, file.file)

        val read = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
        assertArrayEquals(bytes, read)

        val outside = File(context.filesDir, "export-outside.epub").apply { writeBytes(bytes) }
        try {
            FileProvider.getUriForFile(context, authority, outside)
            fail("a file outside the cache folder was shared")
        } catch (expected: IllegalArgumentException) {
            // Expected: the provider only serves its configured cache folder.
        } finally {
            outside.delete()
        }

        @Suppress("DEPRECATION")
        val provider = context.packageManager.resolveContentProvider(authority, 0)
        assertNotNull("provider is not declared", provider)
        assertFalse("provider is exported", provider!!.exported)
        assertTrue("provider does not grant URI permissions", provider.grantUriPermissions)
    }

    @Suppress("DEPRECATION")
    private fun uriExtra(intent: Intent, name: String): Uri? = intent.getParcelableExtra(name)

    @Suppress("DEPRECATION")
    private fun uriListExtra(intent: Intent): List<Uri> =
        requireNotNull(intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)) { "no stream list" }
}
