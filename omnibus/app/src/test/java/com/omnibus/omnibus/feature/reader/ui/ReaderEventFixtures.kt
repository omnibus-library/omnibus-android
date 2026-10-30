package com.omnibus.omnibus.feature.reader.ui

/**
 * Payloads shaped like the ones `epub-reader-glue.js` hands to `reader.html`, which posts them as
 * the `payload` of the envelope (JSON in a string, except where the glue passes a bare string).
 */
internal object ReaderEventFixtures {

    /** `buildRelocateData` on a movement, mid-chapter. */
    const val RELOCATE =
        """{"cfi":"epubcfi(/6/14!/4/2/1:0)","page":12,"totalPages":310,"pct":4,"atEnd":false,""" +
            """"chapter":2,"totalChapters":24,"chapterTitle":"Loomings","chapterPagesLeft":7,""" +
            """"settledSeek":null,"echo":false}"""

    /** The bare first `toc` emission: label, href and level only. */
    const val TOC_BARE =
        """[{"label":"Cover","href":"cover.xhtml","level":0},""" +
            """{"label":"Loomings","href":"chapter1.xhtml","level":0},""" +
            """{"label":"The Carpet-Bag","href":"chapter2.xhtml#carpet","level":1}]"""

    /** The second `toc` emission, with page and pct per entry. */
    const val TOC_ENRICHED =
        """[{"label":"Cover","href":"cover.xhtml","level":0,"page":1,"pct":0},""" +
            """{"label":"Loomings","href":"chapter1.xhtml","level":0,"page":12,"pct":4},""" +
            """{"label":"The Carpet-Bag","href":"chapter2.xhtml#carpet","level":1,"page":31,"pct":10}]"""

    /** `selection` with a range, its rects and both handles, and no annotation under it. */
    const val SELECTION =
        """{"cfiRange":"epubcfi(/6/14!/4/2/1:0,/4/2/1:24)","text":"Call me Ishmael.",""" +
            """"rects":[{"x":24.5,"y":310.0,"width":180.25,"height":22.0}],""" +
            """"start":{"x":24.5,"y":310.0,"height":22.0},""" +
            """"end":{"x":204.75,"y":310.0,"height":22.0},"existing":null,"dragging":false}"""

    /** `selection` over an existing highlight, with `start` and `end` left out. */
    const val SELECTION_EXISTING =
        """{"cfiRange":"epubcfi(/6/14!/4/2/1:0,/4/2/1:24)","text":"Call me Ishmael.",""" +
            """"rects":[],"existing":{"cfiRange":"epubcfi(/6/14!/4/2/1:0,/4/2/1:24)","color":"yellow"},""" +
            """"dragging":true}"""

    /** `annotationTap`, `{cfiRange, rects}`. */
    const val ANNOTATION_TAP =
        """{"cfiRange":"epubcfi(/6/14!/4/2/1:0,/4/2/1:24)",""" +
            """"rects":[{"x":24.5,"y":310.0,"width":180.25,"height":22.0}]}"""

    /** `shareImage`, `{name, dataUrl}` with a base64 PNG data URL. */
    const val SHARE_IMAGE =
        """{"name":"quote-card.png","dataUrl":"data:image/png;base64,iVBORw0KGgo="}"""
}
