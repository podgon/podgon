package app.podgon.mobile

import app.podgon.contract.TokenPair
import app.podgon.mobile.core.auth.AuthState
import app.podgon.mobile.core.auth.SessionManager
import app.podgon.mobile.testing.FakeTokenStorage
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SessionSignOutTest {
    @Test
    fun `при выходе вызывается очистка личных данных`() =
        runTest {
            var cleared = 0
            val session = SessionManager(FakeTokenStorage()) { cleared++ }
            session.signIn(TokenPair("a", "r", 900))
            assertEquals(0, cleared)
            session.signOut()
            assertEquals(1, cleared)
            assertEquals(AuthState.SignedOut, session.state.value)
        }
}
