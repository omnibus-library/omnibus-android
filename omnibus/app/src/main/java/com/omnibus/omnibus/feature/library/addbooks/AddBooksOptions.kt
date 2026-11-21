package com.omnibus.omnibus.feature.library.addbooks

/** The ways to add books that the Add books sheet offers. */
enum class AddBooksOptionKind { Upload, Scan, Wishlist }

/** One row of the Add books sheet. [enabled] is false for a row shown dimmed that can't be tapped. */
data class AddBooksOption(val kind: AddBooksOptionKind, val enabled: Boolean)

/**
 * The sheet's rows, in order: Upload (only for a user who can upload), then Scan, then Wishlist. Upload and Scan
 * are disabled offline; Wishlist is always enabled.
 */
fun addBooksOptions(canUpload: Boolean, isOffline: Boolean): List<AddBooksOption> = buildList {
    if (canUpload) add(AddBooksOption(AddBooksOptionKind.Upload, enabled = !isOffline))
    add(AddBooksOption(AddBooksOptionKind.Scan, enabled = !isOffline))
    add(AddBooksOption(AddBooksOptionKind.Wishlist, enabled = true))
}
