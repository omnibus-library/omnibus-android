package com.omnibus.omnibus.data.libraries

/** An [ActiveLibrary] whose id tests set by hand. */
class TestActiveLibrary(var current: String? = null) : ActiveLibrary {

    override suspend fun id(): String? = current
}
