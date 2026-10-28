package com.omnibus.omnibus.feature.reader.ui

import com.omnibus.omnibus.data.reader.ReaderAssets
import com.omnibus.omnibus.data.reader.ReaderSettings
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** The JavaScript the host evaluates in the reader page. Pure, so what gets sent can be asserted. */
internal object ReaderScripts {

    /** The stage width, in CSS px, at which `spread: auto` pairs columns. */
    const val MIN_SPREAD_WIDTH = 800

    /**
     * The `OmnibusReader.init` call that boots the page at [cfi] (when there is one) in [settings].
     * Read at boot time, so a reboot lands where the reader actually is.
     */
    fun boot(bookUuid: String, settings: ReaderSettings, cfi: String?): String {
        val options = buildJsonObject {
            put("theme", settings.theme.token)
            put("fontSize", settings.fontSize)
            put("fontsHref", ReaderAssets.FONTS_URL)
            put("lineHeight", settings.lineHeight)
            put("maxWidth", settings.maxWidth)
            put("justify", settings.justify)
            put("spread", settings.spread)
            put("minSpreadWidth", MIN_SPREAD_WIDTH)
            // Without it the section iframe dispatches no selection or gesture events.
            put("allowScriptedContent", true)
            put("locationsKey", bookUuid)
            if (!cfi.isNullOrBlank()) put("cfi", cfi)
            // Absent, like cfi: the glue reads a missing fontFamily as "keep the book's face".
            settings.fontFamily?.let { put("fontFamily", it) }
        }
        return "OmnibusReader.init('stage', ${quote(ReaderAssets.bookUrl(bookUuid))}, $options)"
    }

    /** The calls that bring a page showing [shown] up to [wanted]. */
    fun settingsChange(shown: ReaderSettings, wanted: ReaderSettings): List<String> = buildList {
        if (wanted.fontSize != shown.fontSize) add("OmnibusReader.setFontSize(${wanted.fontSize})")
        if (wanted.theme != shown.theme) add("OmnibusReader.setTheme(${quote(wanted.theme.token)})")
        if (wanted.fontFamily != shown.fontFamily) {
            // Null clears the override rather than setting one.
            add("OmnibusReader.setFont(${wanted.fontFamily?.let(::quote) ?: "null"})")
        }
        if (wanted.lineHeight != shown.lineHeight) add("OmnibusReader.setLineHeight(${wanted.lineHeight})")
        if (wanted.maxWidth != shown.maxWidth) add("OmnibusReader.setMargins(${quote(wanted.maxWidth)})")
        if (wanted.justify != shown.justify) add("OmnibusReader.setJustify(${wanted.justify})")
        if (wanted.spread != shown.spread) add("OmnibusReader.setSpread(${quote(wanted.spread)})")
    }

    /**
     * Hands the page the system-bar insets as `--inset-*` and the theme ground as `--page`, which
     * `reader.html` reads. Waits for the document element, so it also runs as a document-start
     * script and the first paint already has the right ground and bands.
     *
     * @param insets in CSS px, which are density-independent pixels in an Android WebView.
     */
    fun pageStyle(groundHex: String, insets: PageInsets): String =
        """
        (function () {
          function apply() {
            var root = document.documentElement;
            if (!root) return false;
            root.style.setProperty('--page', ${quote(groundHex)});
            root.style.setProperty('--inset-top', '${insets.top}px');
            root.style.setProperty('--inset-bottom', '${insets.bottom}px');
            root.style.setProperty('--inset-left', '${insets.left}px');
            root.style.setProperty('--inset-right', '${insets.right}px');
            return true;
          }
          if (!apply()) {
            new MutationObserver(function (_, observer) {
              if (apply()) observer.disconnect();
            }).observe(document, { childList: true });
          }
        })();
        """.trimIndent()

    const val NEXT = "OmnibusReader.next()"
    const val PREVIOUS = "OmnibusReader.prev()"
    const val DESTROY = "OmnibusReader.destroy()"

    fun display(target: String) = "OmnibusReader.display(${quote(target)})"

    private fun quote(value: String) = JsonPrimitive(value).toString()

}

/** System-bar insets in CSS px. */
internal data class PageInsets(val top: Int, val bottom: Int, val left: Int, val right: Int) {
    companion object {
        val None = PageInsets(0, 0, 0, 0)
    }
}
