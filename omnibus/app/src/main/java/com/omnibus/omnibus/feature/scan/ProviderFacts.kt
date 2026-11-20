package com.omnibus.omnibus.feature.scan

import com.omnibus.omnibus.data.scan.ProviderRecord

/** The second fact line's parts, in order: first published, publisher, pages. Missing parts drop out. */
internal fun providerFactParts(record: ProviderRecord, firstPublished: (String) -> String, pages: (Long) -> String): List<String> =
    listOfNotNull(
        (record.firstPublishYear?.toString() ?: record.year?.takeIf { it.isNotBlank() })?.let(firstPublished),
        record.publisher?.takeIf { it.isNotBlank() },
        record.pages?.let(pages),
    )
