package com.omnibus.omnibus

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.omnibus.omnibus.feature.auth.ui.LoginScreen
import com.omnibus.omnibus.feature.auth.ui.LoginScreenAction
import com.omnibus.omnibus.feature.auth.ui.LoginScreenRoute
import com.omnibus.omnibus.feature.configuration.ui.ServerConfigurationScreen
import com.omnibus.omnibus.feature.configuration.ui.ServerConfigurationScreenAction
import com.omnibus.omnibus.feature.configuration.ui.ServerConfigurationScreenRoute
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val backstack = rememberNavBackStack(ServerConfigurationScreenRoute)

            OmnibusTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->

                    NavDisplay(
                        modifier = Modifier.padding(innerPadding),
                        backStack = backstack,
                        onBack = {
                            backstack.removeLastOrNull()
                        },
                        entryDecorators = listOf(
                            rememberSaveableStateHolderNavEntryDecorator(),
                            rememberViewModelStoreNavEntryDecorator(),
                        ),
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
                                            // do login shit
                                        }

                                        is LoginScreenAction.ChangeServer -> {
                                            backstack.removeLastOrNull()
                                            backstack.add(ServerConfigurationScreenRoute)
                                        }

                                        is LoginScreenAction.Register -> {
                                            // do registration shit
                                        }
                                    }
                                })
                            }
                        }
                    )
                }
            }
        }
    }
}
