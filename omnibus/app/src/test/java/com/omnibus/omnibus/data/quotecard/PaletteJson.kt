package com.omnibus.omnibus.data.quotecard

private fun hex(argb: Int): String = "#%06x".format(argb and 0xFFFFFF)

/** One entry of the server's palette, as the server writes it. */
fun CoverPair.json(): String =
    """{"from":"${hex(fromArgb)}","to":"${hex(toArgb)}","ink":"${if (lightInk) "light" else "dark"}"}"""

/** The server's palette body for [pairs], in order. */
fun paletteJson(pairs: List<CoverPair>): String = """{"pairs":[${pairs.joinToString(",") { it.json() }}]}"""
