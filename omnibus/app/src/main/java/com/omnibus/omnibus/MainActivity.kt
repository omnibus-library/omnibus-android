package com.omnibus.omnibus

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import com.omnibus.omnibus.feature.auth.ui.LoginScreen
import com.omnibus.omnibus.feature.auth.ui.LoginScreenAction
import com.omnibus.omnibus.feature.auth.ui.LoginScreenRoute
import com.omnibus.omnibus.feature.configuration.ui.ServerConfigurationScreen
import com.omnibus.omnibus.feature.configuration.ui.ServerConfigurationScreenAction
import com.omnibus.omnibus.feature.configuration.ui.ServerConfigurationScreenRoute
import com.omnibus.omnibus.feature.reader.ui.ReaderRoute
import com.omnibus.omnibus.feature.reader.ui.ReaderScreen
import com.omnibus.omnibus.feature.shell.ui.SignedInShellRoute
import com.omnibus.omnibus.feature.shell.ui.SignedInShellScreen
import com.omnibus.omnibus.ui.components.LaunchScreen
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must run before super.onCreate. The system splash is only the launch ground; LaunchScreen
        // takes over from it (see LaunchScreen), so it is not held while loading.
        installSplashScreen()
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContent {
            val startDestination by viewModel.startDestination.collectAsState()
            val isSignedIn by viewModel.isSignedIn.collectAsState()

            val startRoute: NavKey? = when (startDestination) {
                StartDestination.Loading -> null
                StartDestination.ServerConfiguration -> ServerConfigurationScreenRoute
                StartDestination.Login -> LoginScreenRoute
                StartDestination.SignedIn -> SignedInShellRoute
            }

            OmnibusTheme {
                if (startRoute == null) {
                    LaunchScreen()
                } else {
                    OmnibusNavigation(startRoute = startRoute, isSignedIn = isSignedIn)
                }
            }
        }
    }
}

@Composable
private fun OmnibusNavigation(startRoute: NavKey, isSignedIn: Boolean?) {
    // Saveable, so after rotation or process death the restored stack wins over startRoute.
    val backstack = rememberNavBackStack(startRoute)

    RootNavDisplay(
        backstack = backstack,
        isSignedIn = isSignedIn,
        entryProvider = entryProvider {
            entry<ServerConfigurationScreenRoute> {
                ServerConfigurationScreen(onAction = { action ->
                    when (action) {
                        is ServerConfigurationScreenAction.ServerConfigurationSaved -> {
                            backstack.removeLastOrNull()
                            backstack.add(LoginScreenRoute)
                        }
                    }
                })
            }
            entry<LoginScreenRoute> {
                LoginScreen(onAction = { action ->
                    when (action) {
                        is LoginScreenAction.Login -> {
                            // Add before removing so the back stack is never empty.
                            backstack.add(SignedInShellRoute)
                            backstack.removeAll { it != SignedInShellRoute }
                        }

                        is LoginScreenAction.ChangeServer -> {
                            backstack.removeLastOrNull()
                            backstack.add(ServerConfigurationScreenRoute)
                        }
                    }
                })
            }
            entry<SignedInShellRoute> {
                SignedInShellScreen(onOpenReader = { backstack.openReader(it) })
            }
            entry<ReaderRoute> { key ->
                ReaderScreen(bookUuid = key.bookUuid, onClose = { backstack.closeReader(key) })
            }
        },
    )
}
