package com.omnibus.omnibus.feature.bookdetail

import com.omnibus.omnibus.data.library.BookResult
import com.omnibus.omnibus.data.library.BooksPageQuery
import com.omnibus.omnibus.data.library.LibraryPageResult
import com.omnibus.omnibus.data.library.LibraryRepository
import com.omnibus.omnibus.data.library.RecentProgressResult
import com.omnibus.omnibus.data.library.ShelfPreviewsResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import java.util.concurrent.CopyOnWriteArrayList

/**
 * A library whose only live read is [getBookFlow]: it records each uuid requested and emits [bookResult]. The other
 * reads report no server, as the Book Detail page never makes them.
 */
class TestBookDetailRepository(
    var bookResult: BookResult = BookResult.Success(testDetailBook()),
) : LibraryRepository {

    /** Written on the main thread and read on the test thread. */
    val bookRequests: MutableList<String> = CopyOnWriteArrayList()

    override fun getBooksPageFlow(query: BooksPageQuery, cursor: String?): Flow<LibraryPageResult> =
        flowOf(LibraryPageResult.NoServer)

    override fun getRecentProgressFlow(): Flow<RecentProgressResult> = flowOf(RecentProgressResult.NoServer)

    override fun getShelfPreviewsFlow(): Flow<ShelfPreviewsResult> = flowOf(ShelfPreviewsResult.NoServer)

    override fun getBookFlow(uuid: String): Flow<BookResult> = flow {
        bookRequests += uuid
        emit(bookResult)
    }
}
