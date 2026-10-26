package com.omnibus.omnibus.data.cover

/**
 * The image-loading model for one book cover: what the UI hands to the image loader to ask for the
 * [size] thumbnail of the [uuid] book.
 */
data class CoverThumbnail(val uuid: String, val size: CoverSize)
