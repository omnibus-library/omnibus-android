package com.omnibus.omnibus.feature.search.rows

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import com.omnibus.omnibus.R

/** "1 book" or "N books" for [count]. */
@Composable
fun bookCountLabel(count: Int): String = pluralStringResource(R.plurals.search_book_count, count, count)
