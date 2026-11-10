package com.omnibus.omnibus.feature.shelves

import com.omnibus.omnibus.data.auth.TestAuthRepository
import com.omnibus.omnibus.data.auth.randomCurrentUser
import com.omnibus.omnibus.data.library.LibraryError
import com.omnibus.omnibus.data.library.ShelfPreview
import com.omnibus.omnibus.data.library.ShelfPreviewsResult
import com.omnibus.omnibus.data.library.ShelfVisibility
import com.omnibus.omnibus.data.library.TestLibraryRepository
import com.omnibus.omnibus.data.library.randomShelf
import com.omnibus.omnibus.data.library.randomShelfPreview
import com.omnibus.omnibus.feature.library.LibraryScreenState
import com.omnibus.omnibus.feature.library.LibraryUIError
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

private val unreachable = ShelfPreviewsResult.Failure(LibraryError.Unreachable)
private val unreachableError = LibraryUIError.Failed(LibraryError.Unreachable)

@OptIn(ExperimentalCoroutinesApi::class)
class ShelvesViewModelTest : DescribeSpec({

    beforeSpec { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterSpec { Dispatchers.resetMain() }

    fun sut(
        repository: TestLibraryRepository = TestLibraryRepository(),
        auth: TestAuthRepository = TestAuthRepository(),
    ) = ShelvesViewModel(repository, auth)

    /** A shelf owned by [ownerId] and [visibility], with its other facts random. */
    fun shelfOwnedBy(ownerId: Long, visibility: ShelfVisibility): ShelfPreview = ShelfPreview(
        shelf = randomShelf().copy(ownerUserId = ownerId, visibility = visibility),
        coverUuids = emptyList(),
    )

    describe("loading the shelves") {

        it("#257 TC-10: loads on every screenShown and on refresh") {
            val repository = TestLibraryRepository()
            val viewModel = sut(repository)
            val first = List(2) { randomShelfPreview() }
            val second = List(3) { randomShelfPreview() }
            repository.enqueueShelves(ShelfPreviewsResult.Success(first))
            repository.enqueueShelves(ShelfPreviewsResult.Success(second))
            repository.enqueueShelves(ShelfPreviewsResult.Success(emptyList()))

            viewModel.screenShown()
            viewModel.uiState.value.previews shouldBe first
            viewModel.screenShown()
            viewModel.uiState.value.previews shouldBe second
            viewModel.refresh()

            viewModel.uiState.value.previews shouldBe emptyList()
            repository.shelfPreviewRequests shouldBe 3
        }

        it("#257 TC-10: shows the cached list before the network one") {
            val repository = TestLibraryRepository()
            val viewModel = sut(repository)
            val cached = List(2) { randomShelfPreview() }
            val fresh = List(3) { randomShelfPreview() }
            val network = CompletableDeferred<ShelfPreviewsResult>()
            repository.enqueueShelvesFlow {
                emit(ShelfPreviewsResult.Success(cached))
                emit(network.await())
            }

            viewModel.screenShown()
            viewModel.uiState.value.previews shouldBe cached

            network.complete(ShelfPreviewsResult.Success(fresh))
            viewModel.uiState.value.previews shouldBe fresh
        }

        it("#257 TC-10: is refreshing until the refresh completes") {
            val repository = TestLibraryRepository()
            val viewModel = sut(repository)
            val network = CompletableDeferred<ShelfPreviewsResult>()
            repository.enqueueShelves(network)

            viewModel.refresh()
            viewModel.uiState.value.isRefreshing shouldBe true

            network.complete(ShelfPreviewsResult.Success(List(2) { randomShelfPreview() }))
            viewModel.uiState.value.isRefreshing shouldBe false
        }
    }

    describe("the screen") {
        context("while the first load is in flight") {
            it("#257 TC-11: is Loading") {
                val repository = TestLibraryRepository()
                repository.enqueueShelves(CompletableDeferred<ShelfPreviewsResult>())
                val viewModel = sut(repository)

                viewModel.screenShown()

                viewModel.uiState.value.screen shouldBe LibraryScreenState.Loading
            }
        }

        context("when the first load fails") {
            it("#257 TC-11: is Error with the mapped failure") {
                checkAll(
                    Exhaustive.of(
                        ShelfPreviewsResult.Failure(LibraryError.Unreachable) to unreachableError,
                        ShelfPreviewsResult.NoServer to LibraryUIError.NoServer,
                    ),
                ) { (result, uiError) ->
                    val repository = TestLibraryRepository()
                    repository.enqueueShelves(result)
                    val viewModel = sut(repository)

                    viewModel.screenShown()

                    viewModel.uiState.value.screen shouldBe LibraryScreenState.Error(uiError)
                }
            }
        }

        context("when the load has no shelves") {
            it("#257 TC-11: is Empty") {
                val repository = TestLibraryRepository()
                repository.enqueueShelves(ShelfPreviewsResult.Success(emptyList()))
                val viewModel = sut(repository)

                viewModel.screenShown()

                viewModel.uiState.value.screen shouldBe LibraryScreenState.Empty
            }
        }

        context("when the load has shelves") {
            it("#257 TC-11: is Content") {
                val repository = TestLibraryRepository()
                repository.enqueueShelves(ShelfPreviewsResult.Success(List(2) { randomShelfPreview() }))
                val viewModel = sut(repository)

                viewModel.screenShown()

                viewModel.uiState.value.screen shouldBe LibraryScreenState.Content
            }
        }
    }

    describe("a failed load") {
        context("when a refresh fails with shelves held") {
            it("#257 TC-11: keeps the shelves on screen") {
                val repository = TestLibraryRepository()
                val held = List(3) { randomShelfPreview() }
                repository.enqueueShelves(ShelfPreviewsResult.Success(held))
                val viewModel = sut(repository).also { it.screenShown() }
                repository.enqueueShelves(unreachable)

                viewModel.refresh()

                viewModel.uiState.value.previews shouldBe held
                viewModel.uiState.value.screen shouldBe LibraryScreenState.Content
            }

            it("#257 TC-11: shows the failure as a transient error, not a full-screen one") {
                val repository = TestLibraryRepository()
                repository.enqueueShelves(ShelfPreviewsResult.Success(List(3) { randomShelfPreview() }))
                val viewModel = sut(repository).also { it.screenShown() }
                repository.enqueueShelves(unreachable)

                viewModel.refresh()

                viewModel.uiState.value.transientError shouldBe unreachableError
                viewModel.uiState.value.error shouldBe null
            }
        }

        context("when a load emits the cache and then a failure") {
            it("#257 TC-11: keeps the cached shelves") {
                val repository = TestLibraryRepository()
                val cached = List(2) { randomShelfPreview() }
                repository.enqueueShelvesFlow {
                    emit(ShelfPreviewsResult.Success(cached))
                    emit(unreachable)
                }
                val viewModel = sut(repository)

                viewModel.screenShown()

                viewModel.uiState.value.previews shouldBe cached
                viewModel.uiState.value.transientError shouldBe unreachableError
            }
        }

        it("#257 TC-11: clears the transient error once it is shown") {
            val repository = TestLibraryRepository()
            repository.enqueueShelves(ShelfPreviewsResult.Success(List(3) { randomShelfPreview() }))
            val viewModel = sut(repository).also { it.screenShown() }
            repository.enqueueShelves(unreachable)
            viewModel.refresh()

            viewModel.transientErrorShown()

            viewModel.uiState.value.transientError shouldBe null
        }
    }

    describe("refreshing") {
        context("with an older load that completes last") {
            it("#257 TC-12: ignores a superseded load") {
                val repository = TestLibraryRepository()
                val viewModel = sut(repository)
                val older = CompletableDeferred<ShelfPreviewsResult>()
                val newer = List(3) { randomShelfPreview() }
                repository.enqueueShelves(older)
                repository.enqueueShelves(ShelfPreviewsResult.Success(newer))

                viewModel.screenShown()
                viewModel.refresh()
                older.complete(ShelfPreviewsResult.Success(List(2) { randomShelfPreview() }))

                viewModel.uiState.value.previews shouldBe newer
            }
        }
    }

    describe("the viewer") {
        it("#257 TC-13: follows the signed-in user without a reload") {
            val user = randomCurrentUser().copy(isAdmin = true)
            val repository = TestLibraryRepository()
            val auth = TestAuthRepository()
            val own = shelfOwnedBy(user.id, ShelfVisibility.Private)
            val others = shelfOwnedBy(user.id + 1, ShelfVisibility.Private)
            repository.enqueueShelves(ShelfPreviewsResult.Success(listOf(others, own)))
            val viewModel = sut(repository, auth)

            viewModel.screenShown()

            viewModel.uiState.value.groups.none { it.isViewer } shouldBe true
            viewModel.uiState.value.showsNotice shouldBe false

            auth.currentUserState.value = user

            viewModel.uiState.value.viewerId shouldBe user.id
            viewModel.uiState.value.groups.first().isViewer shouldBe true
            viewModel.uiState.value.census.yours shouldBe 1
            viewModel.uiState.value.showsNotice shouldBe true
            repository.shelfPreviewRequests shouldBe 1
        }
    }
})
