package com.omnibus.omnibus.feature.bookdetail.overview.chips.edit

import com.omnibus.omnibus.data.auth.CurrentUser

/** Whether [user] may add or remove genres and tags: signed in, and an editor or an administrator. */
internal fun canEditChips(user: CurrentUser?): Boolean = user != null && (user.canEdit || user.isAdmin)
