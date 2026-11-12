package com.omnibus.omnibus.feature.shelves.detail.edit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omnibus.omnibus.data.library.Shelf
import com.omnibus.omnibus.data.library.ShelfKind
import com.omnibus.omnibus.data.library.ShelfVisibility
import com.omnibus.omnibus.data.network.ConnectivityMonitor
import com.omnibus.omnibus.data.shelves.RuleMatchCount
import com.omnibus.omnibus.data.shelves.RulePreviewResult
import com.omnibus.omnibus.data.shelves.ShelfDetailResult
import com.omnibus.omnibus.data.shelves.ShelfMatchMode
import com.omnibus.omnibus.data.shelves.ShelfRule
import com.omnibus.omnibus.data.shelves.ShelfRuleField
import com.omnibus.omnibus.data.shelves.ShelfRuleOp
import com.omnibus.omnibus.data.shelves.ShelfRules
import com.omnibus.omnibus.data.shelves.ShelfWriteResult
import com.omnibus.omnibus.data.shelves.ShelvesError
import com.omnibus.omnibus.data.shelves.ShelvesRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** How long the match count waits after the last change to a condition, so typing does not ask the server each key. */
const val PREVIEW_DEBOUNCE_MS = 300L

/**
 * Owns the edit shelf form. It opens on the seed's name and visibility at once, and fills description, match mode and
 * rules from a fresh network read. Save sends the form as [updateOf] builds it.
 */
@HiltViewModel(assistedFactory = EditShelfViewModel.Factory::class)
class EditShelfViewModel @AssistedInject constructor(
    @Assisted private val seed: Shelf,
    private val shelvesRepository: ShelvesRepository,
    private val connectivityMonitor: ConnectivityMonitor,
) : ViewModel() {

    /** Creates the view model for one shelf's edit form. */
    @AssistedFactory
    interface Factory {
        fun create(seed: Shelf): EditShelfViewModel
    }

    private val _uiState = MutableStateFlow(
        EditShelfUIState(
            name = seed.name,
            visibility = seed.visibility,
            isSmart = seed.kind == ShelfKind.Smart,
            online = connectivityMonitor.isOnline.value,
        ),
    )

    /** The form's state. */
    val uiState: StateFlow<EditShelfUIState> = _uiState.asStateFlow()

    private var nextRuleId = 0
    private var previewJob: Job? = null

    init {
        viewModelScope.launch {
            connectivityMonitor.isOnline.collect { online -> _uiState.update { it.copy(online = online) } }
        }
        viewModelScope.launch {
            // Network only: a cached detail must never fill the form, since a save would write it back.
            val result = shelvesRepository.getShelf(seed.id)
            if (result is ShelfDetailResult.Success) applyFresh(result.detail.description, result.detail.rules)
        }
        startPreviewing()
    }

    /** Sets the name. */
    fun nameChanged(name: String) {
        _uiState.update { it.copy(name = name) }
    }

    /** Sets the description. Only editable once the fresh read has landed. */
    fun descriptionChanged(description: String) {
        _uiState.update { if (it.loaded) it.copy(description = description) else it }
    }

    /** Sets the visibility. */
    fun visibilityChanged(visibility: ShelfVisibility) {
        _uiState.update { it.copy(visibility = visibility) }
    }

    /** Sets whether every condition or any must match. Only editable once the fresh read has landed. */
    fun matchModeChanged(matchMode: ShelfMatchMode) {
        _uiState.update { if (it.loaded) it.copy(matchMode = matchMode) else it }
    }

    /** Sets the field of condition [id], resetting its op when the new field doesn't accept it. */
    fun ruleFieldChanged(id: Int, field: ShelfRuleField) = updateRule(id) { it.withField(field) }

    /** Sets the op of condition [id]. */
    fun ruleOpChanged(id: Int, op: ShelfRuleOp) = updateRule(id) { it.withOp(op) }

    /** Sets the value of condition [id]. */
    fun ruleValueChanged(id: Int, value: String) = updateRule(id) { it.copy(value = value) }

    /** Appends a new condition: Tag, is, empty. */
    fun addRule() {
        _uiState.update { if (it.rulesLocked) it else it.copy(rules = it.rules + newRule(nextRuleId++)) }
    }

    /** Removes condition [id]. */
    fun removeRule(id: Int) {
        _uiState.update { if (it.rulesLocked) it else it.copy(rules = it.rules.filterNot { rule -> rule.id == id }) }
    }

    /** Clears the error line once it has been shown. */
    fun errorShown() {
        _uiState.update { it.copy(error = null) }
    }

    /**
     * Saves the form, if [EditShelfUIState.canSave]. The match count stops with the save. On failure it starts again,
     * so the form still shows a count.
     */
    fun save() {
        val state = _uiState.value
        if (!state.canSave) return
        previewJob?.cancel()
        _uiState.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            val update = updateOf(
                name = state.name,
                description = state.description,
                visibility = state.visibility,
                matchMode = state.matchMode,
                rules = state.rules,
                isSmart = state.isSmart,
                loaded = state.loaded,
                rulesLocked = state.rulesLocked,
            )
            when (val result = shelvesRepository.updateShelf(seed.id, update)) {
                ShelfWriteResult.Success -> _uiState.update { it.copy(saving = false, saved = true) }
                is ShelfWriteResult.Failure -> {
                    _uiState.update { it.copy(saving = false, error = editErrorOf(result.error)) }
                    startPreviewing()
                }
                ShelfWriteResult.NoServer -> {
                    _uiState.update { it.copy(saving = false, error = EditShelfError.Generic) }
                    startPreviewing()
                }
            }
        }
    }

    private fun updateRule(id: Int, change: (EditableRule) -> EditableRule) {
        _uiState.update { state ->
            if (state.rulesLocked) {
                state
            } else {
                state.copy(rules = state.rules.map { if (it.id == id) change(it) else it })
            }
        }
    }

    private fun applyFresh(description: String?, rules: ShelfRules) {
        val unknown = rules.rules.any { it.field == null || it.op == null }
        val editable = if (unknown) {
            emptyList()
        } else {
            rules.rules.mapIndexed { index, rule ->
                EditableRule(
                    id = index,
                    field = rule.field!!,
                    op = rule.op!!,
                    // A value a menu offers is shown as the menu spells it; any other is kept as the server has it.
                    value = fixedValuesOf(rule.field!!, rule.op!!)?.let { canonicalValue(it, rule.value) } ?: rule.value,
                )
            }
        }
        nextRuleId = rules.rules.size
        _uiState.update {
            it.copy(
                description = description.orEmpty(),
                matchMode = rules.matchMode ?: ShelfMatchMode.All,
                rules = editable,
                rulesLocked = unknown,
                lockedRules = if (unknown) rules.rules else emptyList(),
                loaded = true,
            )
        }
    }

    /**
     * Counts the books the valid conditions match, once the conditions have been still for [PREVIEW_DEBOUNCE_MS].
     * Each change restarts the wait. The count is shown only for a smart shelf with a valid condition.
     */
    private fun startPreviewing() {
        previewJob = viewModelScope.launch {
            _uiState
                .map { state -> state.loaded to (state.matchMode to state.rules.valid()) }
                .distinctUntilChanged()
                .collectLatest { (loaded, query) ->
                    val (mode, rules) = query
                    if (!loaded || !_uiState.value.isSmart || rules.isEmpty()) {
                        _uiState.update { it.copy(matchCount = null) }
                        return@collectLatest
                    }
                    delay(PREVIEW_DEBOUNCE_MS)
                    val count = (shelvesRepository.previewRules(mode, rules) as? RulePreviewResult.Success)?.count
                    _uiState.update { it.copy(matchCount = count) }
                }
        }
    }

    /** Maps a failed save to the error the form shows. */
    private fun editErrorOf(error: ShelvesError): EditShelfError = when (error) {
        is ShelvesError.Http -> when (error.code) {
            HTTP_CONFLICT -> EditShelfError.NameTaken
            HTTP_UNPROCESSABLE -> EditShelfError.Server(error.serverMessage)
            else -> EditShelfError.Generic
        }
        else -> EditShelfError.Generic
    }

    private companion object {
        const val HTTP_CONFLICT = 409
        const val HTTP_UNPROCESSABLE = 422
    }
}

/**
 * What the edit form shows. [canSave] is the rule the Save button follows. [matchCount] is the last count from the
 * preview, or null when none applies or it failed.
 *
 * @property rulesLocked Whether the shelf has rules the app can't edit. They are shown read-only in [lockedRules].
 */
data class EditShelfUIState(
    val name: String = "",
    val description: String = "",
    val visibility: ShelfVisibility = ShelfVisibility.Private,
    val matchMode: ShelfMatchMode = ShelfMatchMode.All,
    val rules: List<EditableRule> = emptyList(),
    val rulesLocked: Boolean = false,
    val lockedRules: List<ShelfRule> = emptyList(),
    val isSmart: Boolean = false,
    val loaded: Boolean = false,
    val online: Boolean = true,
    val saving: Boolean = false,
    val matchCount: RuleMatchCount? = null,
    val error: EditShelfError? = null,
    val saved: Boolean = false,
) {

    /** Whether Save is enabled. */
    val canSave: Boolean
        get() = canSave(online, saving, name, isSmart, loaded, rules, rulesLocked)
}

/** Why a save failed, as the form words it. */
sealed interface EditShelfError {

    /** The user already has a shelf with that name. */
    data object NameTaken : EditShelfError

    /** The server refused the change and said why, or said nothing. */
    data class Server(val message: String?) : EditShelfError

    /** Anything else. */
    data object Generic : EditShelfError
}
