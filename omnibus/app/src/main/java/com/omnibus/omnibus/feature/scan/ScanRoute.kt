package com.omnibus.omnibus.feature.scan

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** The full-screen barcode scanner, a root destination that covers the signed-in shell. */
@Serializable
data object ScanRoute : NavKey
