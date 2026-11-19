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

    /** `annotationTap`, `{cfiRange, rects}`. */
    const val ANNOTATION_TAP =
        """{"cfiRange":"epubcfi(/6/14!/4/2/1:0,/4/2/1:24)",""" +
            """"rects":[{"x":24.5,"y":310.0,"width":180.25,"height":22.0}]}"""

    /**
     * `annotationTap` with `hits`: three highlights under the tap, in book order, and the top-level pair equal to the
     * first.
     */
    const val ANNOTATION_TAP_HITS =
        """{"cfiRange":"epubcfi(/6/14!/4/2/1:0,/4/2/1:24)",""" +
            """"rects":[{"x":24.5,"y":310.0,"width":180.25,"height":22.0}],""" +
            """"hits":[""" +
            """{"cfiRange":"epubcfi(/6/14!/4/2/1:0,/4/2/1:24)",""" +
            """"rects":[{"x":24.5,"y":310.0,"width":180.25,"height":22.0}]},""" +
            """{"cfiRange":"epubcfi(/6/14!/4/2/1:12,/4/2/1:40)",""" +
            """"rects":[{"x":120.0,"y":310.0,"width":210.5,"height":22.0}]},""" +
            """{"cfiRange":"epubcfi(/6/14!/4/2/1:30,/4/2/1:60)",""" +
            """"rects":[{"x":240.0,"y":310.0,"width":96.0,"height":22.0}]}""" +
            """]}"""

    /** `shareImage`, `{name, dataUrl}` with a base64 PNG data URL. */
    const val SHARE_IMAGE =
        """{"name":"quote-card.png","dataUrl":"data:image/png;base64,iVBORw0KGgo="}"""
}
