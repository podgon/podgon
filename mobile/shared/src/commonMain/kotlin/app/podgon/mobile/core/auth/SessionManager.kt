package app.podgon.mobile.core.auth

import app.podgon.contract.TokenPair
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface AuthState {
    /** Токены ещё читаются из хранилища. */
    data object Loading : AuthState

    data object SignedOut : AuthState

    data object SignedIn : AuthState
}

/**
 * Единая точка правды о сессии: состояние входа и токены. Обновление токенов и выход
 * сериализуются мьютексом, чтобы параллельные запросы не затирали друг друга.
 */
class SessionManager(
    private val storage: TokenStorage,
) {
    private val mutex = Mutex()
    private val _state = MutableStateFlow<AuthState>(AuthState.Loading)
    val state: StateFlow<AuthState> = _state.asStateFlow()

    /** Читает сохранённые токены при старте приложения. */
    suspend fun restore() {
        mutex.withLock {
            _state.value = if (storage.read() != null) AuthState.SignedIn else AuthState.SignedOut
        }
    }

    suspend fun tokens(): StoredTokens? = mutex.withLock { storage.read() }

    suspend fun signIn(pair: TokenPair) {
        mutex.withLock {
            storage.write(StoredTokens(pair.accessToken, pair.refreshToken))
            _state.value = AuthState.SignedIn
        }
    }

    /** Сохраняет обновлённую пару, не меняя состояние входа. */
    suspend fun update(pair: TokenPair): StoredTokens =
        mutex.withLock {
            StoredTokens(pair.accessToken, pair.refreshToken).also { storage.write(it) }
        }

    suspend fun signOut() {
        mutex.withLock {
            storage.clear()
            _state.value = AuthState.SignedOut
        }
    }
}
