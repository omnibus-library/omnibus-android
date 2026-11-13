package com.omnibus.omnibus.feature.shelves.detail.edit

import com.omnibus.omnibus.data.library.Shelf
import com.omnibus.omnibus.data.library.ShelfKind
import com.omnibus.omnibus.data.library.ShelfVisibility
import com.omnibus.omnibus.data.library.randomShelf
import com.omnibus.omnibus.data.network.TestConnectivityMonitor
import com.omnibus.omnibus.data.shelves.RuleMatchCount
import com.omnibus.omnibus.data.shelves.RulePreviewResult
import com.omnibus.omnibus.data.shelves.ShelfDetail
import com.omnibus.omnibus.data.shelves.ShelfDetailResult
import com.omnibus.omnibus.data.shelves.ShelfMatchMode
import com.omnibus.omnibus.data.shelves.ShelfRule
import com.omnibus.omnibus.data.shelves.ShelfRuleField
import com.omnibus.omnibus.data.shelves.ShelfRuleOp
import com.omnibus.omnibus.data.shelves.ShelfRules
import com.omnibus.omnibus.data.shelves.ShelfUpdate
import com.omnibus.omnibus.data.shelves.ShelfWriteResult
import com.omnibus.omnibus.data.shelves.ShelvesError
import com.omnibus.omnibus.feature.bookdetail.more.shelves.ScriptedShelvesRepository
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class EditShelfViewModelTest : DescribeSpec({

    val dispatcher = StandardTestDispatcher()

    beforeSpec { Dispatchers.setMain(dispatcher) }
    afterSpec { Dispatchers.resetMain() }

    /** Lets every pending coroutine, including the debounce, run on virtual time. */
    fun settle() = dispatcher.scheduler.advanceUntilIdle()

    fun shelf(kind: ShelfKind): Shelf = randomShelf().copy(kind = kind)

    fun sut(
        seed: Shelf,
        shelves: ScriptedShelvesRepository,
        online: Boolean = true,
    ): Pair<EditShelfViewModel, TestConnectivityMonitor> {
        val connectivity = TestConnectivityMonitor(online)
        return EditShelfViewModel(seed, shelves, connectivity) to connectivity
    }

    fun detailOf(seed: Shelf, description: String? = null, matchMode: ShelfMatchMode? = null, rules: List<ShelfRule> = emptyList()) =
        ShelfDetailResult.Success(ShelfDetail(seed, description, ShelfRules(matchMode, rules)))

    describe("opening the form") {

        it("#232 TC-31: shows the seed's name and visibility before the fresh read lands") {
            val seed = shelf(ShelfKind.Smart).copy(visibility = ShelfVisibility.Public)
            val shelves = ScriptedShelvesRepository().apply { freshShelf = detailOf(seed, description = "Long") }
            val (viewModel, _) = sut(seed, shelves)

            viewModel.uiState.value.name shouldBe seed.name
            viewModel.uiState.value.visibility shouldBe ShelfVisibility.Public
            viewModel.uiState.value.loaded shouldBe false
            viewModel.uiState.value.description shouldBe ""
        }

        it("#232 TC-31: fills the description, match mode and rules from the fresh read") {
            val seed = shelf(ShelfKind.Smart)
            val rule = ShelfRule(ShelfRuleField.Genre, ShelfRuleOp.Is, "Poetry")
            val shelves = ScriptedShelvesRepository().apply {
                freshShelf = detailOf(seed, description = "Long", matchMode = ShelfMatchMode.Any, rules = listOf(rule))
            }
            val (viewModel, _) = sut(seed, shelves)

            settle()

            with(viewModel.uiState.value) {
                loaded shouldBe true
                description shouldBe "Long"
                matchMode shouldBe ShelfMatchMode.Any
                rules shouldBe listOf(EditableRule(id = 0, field = ShelfRuleField.Genre, op = ShelfRuleOp.Is, value = "Poetry"))
            }
        }

        describe("when saving is allowed") {

            it("#232 TC-31: is on for a hand-picked shelf with a name, while online") {
                val (viewModel, _) = sut(shelf(ShelfKind.Manual), ScriptedShelvesRepository())

                viewModel.uiState.value.canSave shouldBe true
            }

            it("#232 TC-31: is off while offline") {
                val (viewModel, _) = sut(shelf(ShelfKind.Manual), ScriptedShelvesRepository(), online = false)

                viewModel.uiState.value.canSave shouldBe false
            }

            it("#232 TC-31: is off with a blank name") {
                val (viewModel, _) = sut(shelf(ShelfKind.Manual), ScriptedShelvesRepository())

                viewModel.nameChanged("   ")

                viewModel.uiState.value.canSave shouldBe false
            }

            it("#232 TC-31: is off for a smart shelf until its fresh read has landed") {
                val seed = shelf(ShelfKind.Smart)
                val (viewModel, _) = sut(seed, ScriptedShelvesRepository().apply { freshShelf = ShelfDetailResult.NoServer })

                settle()

                viewModel.uiState.value.canSave shouldBe false
            }

            it("#232 TC-31: is off for a smart shelf with no valid rule") {
                checkAll(Exhaustive.of("", "  ")) { value ->
                    val seed = shelf(ShelfKind.Smart)
                    val rule = ShelfRule(ShelfRuleField.Tag, ShelfRuleOp.Is, "x")
                    val shelves = ScriptedShelvesRepository().apply { freshShelf = detailOf(seed, matchMode = ShelfMatchMode.All, rules = listOf(rule)) }
                    val (viewModel, _) = sut(seed, shelves)
                    settle()

                    viewModel.ruleValueChanged(0, value)

                    viewModel.uiState.value.canSave shouldBe false
                }
            }

            it("#232 TC-31: is off while a save is in flight") {
                val shelves = ScriptedShelvesRepository()
                val hold = shelves.holdUpdate()
                val (viewModel, _) = sut(shelf(ShelfKind.Manual), shelves)

                viewModel.save()

                viewModel.uiState.value.canSave shouldBe false
                hold.complete(ShelfWriteResult.Success)
            }
        }
    }

    describe("saving") {

        it("#232 TC-32: sends the trimmed name, the visibility and the description once loaded") {
            val seed = shelf(ShelfKind.Manual)
            val shelves = ScriptedShelvesRepository().apply { freshShelf = detailOf(seed, description = " Long ") }
            val (viewModel, _) = sut(seed, shelves)
            settle()

            viewModel.nameChanged("  Weekend  ")
            viewModel.visibilityChanged(ShelfVisibility.Public)
            viewModel.save()
            settle()

            shelves.updates.last() shouldBe (seed.id to ShelfUpdate(name = "Weekend", description = "Long", visibility = ShelfVisibility.Public))
        }

        it("#232 TC-32: sends the match mode and valid rules for a smart shelf only") {
            val seed = shelf(ShelfKind.Smart)
            val rule = ShelfRule(ShelfRuleField.Tag, ShelfRuleOp.Is, "x")
            val shelves = ScriptedShelvesRepository().apply { freshShelf = detailOf(seed, matchMode = ShelfMatchMode.All, rules = listOf(rule)) }
            val (viewModel, _) = sut(seed, shelves)
            settle()

            viewModel.addRule()
            viewModel.save()
            settle()

            shelves.updates.last().second shouldBe ShelfUpdate(
                name = seed.name,
                description = "",
                visibility = seed.visibility,
                matchMode = ShelfMatchMode.All,
                rules = listOf(rule),
            )
        }

        it("#232 TC-32: sends neither match mode nor rules when a rule the app can't edit is on the shelf") {
            val seed = shelf(ShelfKind.Smart)
            val unknown = ShelfRule(field = null, op = ShelfRuleOp.Is, value = "mood")
            val shelves = ScriptedShelvesRepository().apply { freshShelf = detailOf(seed, matchMode = ShelfMatchMode.Any, rules = listOf(unknown)) }
            val (viewModel, _) = sut(seed, shelves)
            settle()

            viewModel.save()
            settle()

            shelves.updates.last().second.matchMode shouldBe null
            shelves.updates.last().second.rules shouldBe null
        }

        context("when the server refuses the change") {

            it("#232 TC-32: shows that the name is taken for a 409 and keeps the form open") {
                val shelves = ScriptedShelvesRepository().apply {
                    updateResult = ShelfWriteResult.Failure(ShelvesError.Http(409, "taken"))
                }
                val (viewModel, _) = sut(shelf(ShelfKind.Manual), shelves)

                viewModel.save()
                settle()

                viewModel.uiState.value.error shouldBe EditShelfError.NameTaken
                viewModel.uiState.value.saved shouldBe false
            }

            it("#232 TC-32: shows the server's message for a 422") {
                val shelves = ScriptedShelvesRepository().apply {
                    updateResult = ShelfWriteResult.Failure(ShelvesError.Http(422, "name too long"))
                }
                val (viewModel, _) = sut(shelf(ShelfKind.Manual), shelves)

                viewModel.save()
                settle()

                viewModel.uiState.value.error shouldBe EditShelfError.Server("name too long")
            }

            it("#232 TC-32: shows a generic line for any other failure") {
                val shelves = ScriptedShelvesRepository().apply {
                    updateResult = ShelfWriteResult.Failure(ShelvesError.Unreachable)
                }
                val (viewModel, _) = sut(shelf(ShelfKind.Manual), shelves)

                viewModel.save()
                settle()

                viewModel.uiState.value.error shouldBe EditShelfError.Generic
            }
        }

        it("#232 TC-32: is saved once the server accepts the change") {
            val (viewModel, _) = sut(shelf(ShelfKind.Manual), ScriptedShelvesRepository())

            viewModel.save()
            settle()

            viewModel.uiState.value.saved shouldBe true
        }
    }

    describe("counting matching books") {

        it("#232 TC-33: counts once after the quiet period, with the last conditions") {
            val seed = shelf(ShelfKind.Smart)
            val shelves = ScriptedShelvesRepository().apply {
                freshShelf = detailOf(seed, matchMode = ShelfMatchMode.All, rules = listOf(ShelfRule(ShelfRuleField.Tag, ShelfRuleOp.Is, "a")))
                previewResult = RulePreviewResult.Success(RuleMatchCount(matched = 1, total = 10))
            }
            val (viewModel, _) = sut(seed, shelves)
            settle()
            val before = shelves.previews.size

            viewModel.ruleValueChanged(0, "ab")
            dispatcher.scheduler.advanceTimeBy(100)
            viewModel.ruleValueChanged(0, "abc")
            dispatcher.scheduler.advanceTimeBy(100)
            viewModel.ruleValueChanged(0, "abcd")
            settle()

            shelves.previews.drop(before) shouldBe listOf(ShelfMatchMode.All to listOf(ShelfRule(ShelfRuleField.Tag, ShelfRuleOp.Is, "abcd")))
            viewModel.uiState.value.matchCount shouldBe RuleMatchCount(matched = 1, total = 10)
        }

        it("#232 TC-33: asks for no count with no valid rules") {
            val seed = shelf(ShelfKind.Smart)
            val shelves = ScriptedShelvesRepository().apply { freshShelf = detailOf(seed, matchMode = ShelfMatchMode.All) }
            val (viewModel, _) = sut(seed, shelves)
            settle()

            shelves.previews.isEmpty() shouldBe true
            viewModel.uiState.value.matchCount shouldBe null
        }

        it("#232 TC-33: hides the count when the preview fails") {
            val seed = shelf(ShelfKind.Smart)
            val shelves = ScriptedShelvesRepository().apply {
                freshShelf = detailOf(seed, matchMode = ShelfMatchMode.All, rules = listOf(ShelfRule(ShelfRuleField.Tag, ShelfRuleOp.Is, "a")))
                previewResult = RulePreviewResult.Failure(ShelvesError.Unreachable)
            }
            val (viewModel, _) = sut(seed, shelves)

            settle()

            viewModel.uiState.value.matchCount shouldBe null
        }

        it("#232 TC-33: stops counting when the form is saved") {
            val seed = shelf(ShelfKind.Smart)
            val shelves = ScriptedShelvesRepository().apply {
                freshShelf = detailOf(seed, matchMode = ShelfMatchMode.All, rules = listOf(ShelfRule(ShelfRuleField.Tag, ShelfRuleOp.Is, "a")))
            }
            val (viewModel, _) = sut(seed, shelves)
            settle()
            val before = shelves.previews.size

            viewModel.ruleValueChanged(0, "b")
            viewModel.save()
            settle()

            shelves.previews.drop(before).isEmpty() shouldBe true
        }
    }
})
