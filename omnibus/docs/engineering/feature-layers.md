# Feature Layers

Features are built as four layers. Each layer only talks to the one directly below it. State and results flow up; user events flow down. The login feature is the reference implementation.

```
┌──────────────────────────────┐
│ Screen (Compose)             │  feature/<feature>/<Name>Screen.kt
│   collects UI state          │
│   forwards user events       │
└──────────────┬───────────────┘
               │ uiState: StateFlow<...>  ▲   events: fun updateX(), fun login()  ▼
┌──────────────┴───────────────┐
│ ViewModel                    │  feature/<feature>/<Name>ScreenViewModel.kt
│   owns UI state              │
│   launches business calls    │
└──────────────┬───────────────┘
               │ suspend fun / Flow (domain results)
┌──────────────┴───────────────┐
│ Repository                   │  data/<domain>/<Domain>Repository.kt
│   business logic             │
│   coordinates data sources   │
│   picks the dispatcher       │
└──────────────┬───────────────┘
               │ DTOs, raw values
┌──────────────┴───────────────┐
│ Data sources                 │  data/<domain>/remote/, data/<domain>/local/
│   network (OkHttp)           │
│   persistence (DataStore)    │
└──────────────────────────────┘
```

Wiring is done with Hilt. Bindings for a domain live in `data/<domain>/di/`.

## Package layout

```
com.omnibus.omnibus/
├── feature/auth/
│   ├── LoginScreen.kt                    # Screen + stateless content + previews
│   └── LoginScreenViewModel.kt           # ViewModel + UI state types
└── data/auth/
    ├── AuthRepository.kt                 # Interface, default impl, result types
    ├── di/AuthModule.kt                  # Hilt bindings and qualifiers
    ├── local/AuthTokenStore.kt           # Encrypted DataStore token persistence
    └── remote/
        ├── ClientInfo.kt                 # Device name + app version sent on sign-in
        ├── AuthClientMetadataInterceptor.kt
        ├── AuthSignInRequestDto.kt
        └── AuthSignInResponseDto.kt
```

* `feature/<feature>/` holds UI code grouped by user-facing feature.
* `data/<domain>/` holds everything below the ViewModel, grouped by business domain. A domain can serve many features.
* `data/network/` holds the HTTP client and interceptors shared by every domain (`OmnibusHttpClient`, `OmnibusNetworkInterceptor`, `ServerBaseUrlProvider`).
* Within a domain, `remote/` and `local/` separate data sources by where the data lives.

## Screen (Compose)

The screen is split into a stateful entry point and a stateless content composable.

```kotlin
@Composable
fun LoginScreen(viewModel: LoginScreenViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()

    LoginScreenContent(
        uiState = uiState,
        onUsernameChanged = viewModel::updateUsername,
        onPasswordChanged = viewModel::updatePassword,
        onLoginClicked = viewModel::login,
    )
}

@Composable
private fun LoginScreenContent(
    uiState: LoginScreenUIState,
    onUsernameChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onLoginClicked: () -> Unit,
    modifier: Modifier = Modifier,
)
```

* The public entry point (`LoginScreen`) is the only composable that knows about the ViewModel. It obtains the ViewModel with `hiltViewModel()` as a default parameter, collects `uiState`, and passes ViewModel functions down as lambdas.
* The content composable (`LoginScreenContent`) and its children are `private`, take plain values and `(…) -> Unit` callbacks, and never reference the ViewModel. This keeps them previewable and testable in isolation.
* Composables don't hold business state. Field values come from `uiState` and changes are sent back up through callbacks (`onValueChanged = onUsernameChanged`), so the ViewModel is the single source of truth.
* Previews (`@PreviewLightDark`) render the content composable directly with a hand-built UI state wrapped in `OmnibusTheme`.
* Child composables accept a `modifier: Modifier = Modifier` as their last parameter.

## ViewModel

```kotlin
@HiltViewModel
class LoginScreenViewModel @Inject constructor(
    private val authRepo: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginScreenUIState())
    val uiState: StateFlow<LoginScreenUIState> = _uiState.asStateFlow()

    fun updateUsername(username: String) {
        _uiState.update { it.copy(usernameField = username) }
    }

    fun login() {
        viewModelScope.launch {
            authRepo.login(uiState.value.usernameField, uiState.value.passwordField)
        }
    }
}
```

* Annotate with `@HiltViewModel` and constructor-inject repository **interfaces** (`AuthRepository`), never implementations or data sources.
* Expose a single immutable `StateFlow<…UIState>` backed by a private `MutableStateFlow`. Mutate it only with `update { it.copy(...) }` so concurrent updates don't race.
* Public functions are user intents (`updateUsername`, `login`). They're named for what the user did, not how it's handled, and return `Unit`.
* Launch suspend work in `viewModelScope` so it's cancelled when the ViewModel is cleared. Don't switch dispatchers here; that's the repository's job.
* The UI state is a `data class` with default values so the initial state is just `LoginScreenUIState()`. It lives in the same file as the ViewModel.
* Transient operation status is modeled as a sealed type nested in the UI state (`LoginAttemptState`: `Rest`, `Loading`, `Success`, `Failure`). The ViewModel translates repository results into this type; the UI never sees repository result types directly.

## Repository

```kotlin
interface AuthRepository {
    suspend fun login(username: String, password: String): AuthLoginResult
}

class DefaultAuthRepository @Inject constructor(
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val omnibusHttpClient: OmnibusHttpClient,
    private val authTokenStore: AuthTokenStore,
) : AuthRepository {

    override suspend fun login(username: String, password: String): AuthLoginResult =
        withContext(ioDispatcher) { /* call API, store token, map result */ }
}

sealed interface AuthLoginResult {
    data object Authenticated : AuthLoginResult
    data object Unauthenticated : AuthLoginResult
    data object Error : AuthLoginResult
}
```

* Each repository is an interface plus a `Default<Name>` implementation, bound together in the domain's Hilt module with `@Binds @Singleton`. Consumers depend on the interface so they can be given a fake in tests.
* Repositories own business rules: which endpoint to call, what to persist on success (`authTokenStore.save(token)`), and how to interpret responses.
* Repository functions are main-safe. They take an injected dispatcher (`@IoDispatcher`) and wrap blocking work in `withContext(ioDispatcher)`, so callers can invoke them from any coroutine. The dispatcher is injected rather than hard-coded so tests can pass `Dispatchers.Unconfined`.
* Results are returned as a domain `sealed interface` (`AuthLoginResult`) rather than thrown. Expected failures (non-2xx responses) and unexpected ones (network errors, parse failures, storage failures) are each mapped to a distinct case at this boundary. Exceptions don't escape the repository.
* DTOs stop at the repository. Callers receive domain results, not `AuthSignInResponseDto`.
* Path constants (`LOGIN_PATH`) and log tags live in the implementation's `companion object`.

## Data sources

Data sources do one kind of I/O and contain no business decisions.

### Remote

* `OmnibusHttpClient` is a `@Singleton` wrapper around a single `OkHttpClient`. Repositories build requests through it instead of creating their own clients.
  * `request(path)` resolves against the saved server from `ServerBaseUrlProvider` and returns null when none is saved; repositories map that to a domain result (`AuthSignInResult.NoServer`).
  * `request(baseUrl, path)` targets an explicit server, for talking to one before it's saved (the health check).
  * Paths are relative (`api/auth/login`, no leading slash) so they resolve beneath the server's base URL and keep any path prefix.
* Cross-cutting request behavior is done in OkHttp interceptors injected into the client:
  * `OmnibusNetworkInterceptor` adds headers every request needs (`Accept: application/json`).
  * `AuthClientMetadataInterceptor` adds client metadata (`client_kind`, `device_name`, `client_version`) to auth request bodies.
* Request and response bodies are `@Serializable` DTOs in `remote/`, named `<Domain><Action>RequestDto` / `ResponseDto`. DTOs own their own serialization (`toJsonRequest()`, `fromJsonString(...)`) and use `@SerialName` to map snake_case API fields to camelCase properties. Optional API fields get defaults.

### Local

* Persistence is exposed through an interface (`AuthTokenStore`) with a `Default<Name>` implementation bound in the Hilt module.
* Reads are exposed as `Flow` (`val token: Flow<String?>`) so consumers observe changes instead of polling. Writes are `suspend` functions (`save`, `clear`) that run on the injected IO dispatcher.
* Sensitive values are stored in a DataStore encrypted with Tink (`AeadSerializer`) using a key held in the Android Keystore. The DataStore is created lazily so key setup doesn't happen on injection.

## Dependency injection

`data/<domain>/di/<Domain>Module.kt` is an `abstract class` module installed in `SingletonComponent`:

* `@Binds @Singleton` functions map interfaces to `Default*` implementations.
* A `companion object` holds `@Provides` functions for values that aren't classes you own (dispatchers, DataStores).
* Qualifier annotations (`@IoDispatcher`, `@ServerConfigPreferences`) are declared in the same file. Use `@param:` when applying a qualifier to a constructor property.

`OmnibusApplication` is annotated with `@HiltAndroidApp` and `MainActivity` with `@AndroidEntryPoint`, which is what makes `hiltViewModel()` work in the screen.

## Testing each layer

Follow [Testing](testing.md). Per layer:

* **Repository**: Kotest `DescribeSpec` JVM test. Construct `Default<Name>Repository` directly (no Hilt), pass `Dispatchers.Unconfined`, point `OmnibusHttpClient` at a `MockWebServer` with `FakeServerBaseUrlProvider`, and use fakes for local data sources. See `DefaultAuthRepositoryTest`.
* **Local data sources**: hand-written in-memory fakes in `app/src/test/` that implement the interface (`FakeAuthTokenStore`). Fakes can expose hooks such as `saveFailure` to simulate errors. The real implementations need a `Context` and are tested with instrumented tests.
* **Android-only collaborators** (anything touching `Build`, `PackageManager`, `Log`): stub with MockK on the JVM, e.g. a pass-through `AuthClientMetadataInterceptor` or `mockkStatic(Log::class)`.
* **ViewModel**: inject a fake repository interface and assert on `uiState` emissions.

## Adding a new feature

1. Add DTOs and any new local store under `data/<domain>/remote/` and `data/<domain>/local/`.
2. Add or extend the `<Domain>Repository` interface, implement it in `Default<Domain>Repository`, and return a domain `sealed interface` result.
3. Bind new interfaces in `data/<domain>/di/<Domain>Module.kt`.
4. Create `feature/<feature>/<Name>ScreenViewModel.kt` with a `UIState` data class and intent functions that call the repository and map its result into UI state.
5. Create `feature/<feature>/<Name>Screen.kt` with a stateful entry point, a private stateless content composable, and a `@PreviewLightDark` preview.
6. Add a repository test and a fake for any new data source interface.

## Known gaps in the login reference

The login flow doesn't yet fully follow the pattern above. Don't copy these parts:

* `LoginScreenViewModel.login()` discards the `AuthLoginResult` and never sets `loginAttempt` (e.g. `Loading` before the call, `Success`/`Failure` after).
* `LoginAttemptState` is declared as `abstract interface`; it should be a `sealed interface` so `when` checks are exhaustive.
* `DefaultAuthRepository` catches `Exception`, which also swallows `CancellationException`. Rethrow cancellation so `viewModelScope` cancellation propagates.
* The user info in `AuthSignInResponseDto` is dropped.
* There is no ViewModel test yet.
