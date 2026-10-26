package app.podgon.mobile

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import app.podgon.mobile.core.auth.AuthState
import app.podgon.mobile.core.auth.SessionManager
import app.podgon.mobile.designsystem.PodgonTheme
import app.podgon.mobile.feature.auth.ui.CodeScreen
import app.podgon.mobile.feature.auth.ui.EmailScreen
import app.podgon.mobile.feature.auth.ui.RegisterScreen
import app.podgon.mobile.feature.profile.ProfileScreen
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Serializable
private data object EmailRoute

@Serializable
private data class CodeRoute(
    val email: String,
)

@Serializable
private data class RegisterRoute(
    val registrationToken: String,
)

@Serializable
private data object ProfileRoute

/** Корневой экран: по состоянию сессии показывает вход или основное содержимое. */
@Composable
fun App() {
    val session = koinInject<SessionManager>()
    val state by session.state.collectAsStateWithLifecycle()
    LaunchedEffect(session) { session.restore() }
    PodgonTheme {
        when (state) {
            AuthState.Loading ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            AuthState.SignedOut -> SignedOutNavigation()
            AuthState.SignedIn -> SignedInNavigation()
        }
    }
}

@Composable
private fun SignedOutNavigation() {
    val nav = rememberNavController()
    NavHost(nav, startDestination = EmailRoute) {
        composable<EmailRoute> {
            EmailScreen(koinViewModel(), onCodeRequested = { email -> nav.navigate(CodeRoute(email)) })
        }
        composable<CodeRoute> { entry ->
            val route = entry.toRoute<CodeRoute>()
            CodeScreen(
                koinViewModel(parameters = { parametersOf(route.email) }),
                onRegistrationRequired = { token -> nav.navigate(RegisterRoute(token)) },
            )
        }
        composable<RegisterRoute> { entry ->
            val route = entry.toRoute<RegisterRoute>()
            RegisterScreen(koinViewModel(parameters = { parametersOf(route.registrationToken) }))
        }
    }
}

@Composable
private fun SignedInNavigation() {
    val nav = rememberNavController()
    NavHost(nav, startDestination = ProfileRoute) {
        composable<ProfileRoute> { ProfileScreen(koinViewModel()) }
    }
}
