package app.podgon.mobile

import app.cash.turbine.test
import app.podgon.contract.FieldErrorReason
import app.podgon.mobile.core.auth.AuthState
import app.podgon.mobile.core.error.AppError
import app.podgon.mobile.feature.auth.ui.CodeEffect
import app.podgon.mobile.feature.auth.ui.CodeViewModel
import app.podgon.mobile.feature.auth.ui.EmailEffect
import app.podgon.mobile.feature.auth.ui.EmailViewModel
import app.podgon.mobile.feature.auth.ui.RESEND_INTERVAL_SECONDS
import app.podgon.mobile.feature.auth.ui.RegisterViewModel
import app.podgon.mobile.testing.AuthFixture
import app.podgon.mobile.testing.TOKENS_JSON
import app.podgon.mobile.testing.json
import app.podgon.mobile.testing.problem
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelsTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.settle() = testScheduler.runCurrent()

    @Test
    fun `некорректный email не отправляется на сервер`() =
        runTest(dispatcher) {
            val fixture = AuthFixture { json("") }
            val vm = EmailViewModel(fixture.repository)
            vm.onEmailChange("not-an-email")
            vm.submit()
            settle()
            assertEquals(FieldErrorReason.INVALID_FORMAT, vm.state.value.emailInvalid)
            assertEquals(0, fixture.requests.size)
        }

    @Test
    fun `успешный запрос кода ведёт на экран кода с нормализованным email`() =
        runTest(dispatcher) {
            val fixture = AuthFixture { respondAccepted() }
            val vm = EmailViewModel(fixture.repository)
            vm.onEmailChange("  User@Example.COM ")
            vm.effects.test {
                vm.submit()
                settle()
                assertEquals(EmailEffect.CodeRequested("user@example.com"), awaitItem())
            }
            assertEquals(false, vm.state.value.loading)
            assertNull(vm.state.value.error)
        }

    @Test
    fun `сетевая ошибка при запросе кода показывается пользователю и не уводит с экрана`() =
        runTest(dispatcher) {
            val fixture = AuthFixture { error("нет сети") }
            val vm = EmailViewModel(fixture.repository)
            vm.onEmailChange("a@example.com")
            vm.effects.test {
                vm.submit()
                // MockEngine отвечает на своём потоке, поэтому ждём результат по состоянию, а не по планировщику
                vm.state.first { it.error != null }
                expectNoEvents()
            }
            assertEquals(AppError.Network, vm.state.value.error)
        }

    @Test
    fun `код из шести цифр проверяется автоматически и для нового пользователя ведёт к регистрации`() =
        runTest(dispatcher) {
            val fixture = AuthFixture { json("""{"registrationToken":"reg-1"}""") }
            val vm = CodeViewModel("a@example.com", fixture.repository)
            vm.effects.test {
                vm.onCodeChange("12a3-45")
                settle()
                expectNoEvents() // всего 5 цифр
                vm.onCodeChange("123456")
                settle()
                assertEquals(CodeEffect.RegistrationRequired("reg-1"), awaitItem())
            }
        }

    @Test
    fun `верный код существующего пользователя открывает сессию`() =
        runTest(dispatcher) {
            val fixture = AuthFixture { json("""{"tokens":$TOKENS_JSON}""") }
            val vm = CodeViewModel("a@example.com", fixture.repository)
            vm.onCodeChange("123456")
            fixture.session.state.first { it == AuthState.SignedIn }
        }

    @Test
    fun `неверный код очищает поле и показывает ошибку`() =
        runTest(dispatcher) {
            val fixture = AuthFixture { json(problem(400, "INVALID_CODE"), HttpStatusCode.BadRequest) }
            val vm = CodeViewModel("a@example.com", fixture.repository)
            vm.onCodeChange("123456")
            val failed = vm.state.first { it.error != null }
            assertEquals("", failed.code)
            assertIs<AppError.Api>(failed.error)
        }

    @Test
    fun `повторная отправка доступна только после паузы`() =
        runTest(dispatcher) {
            val fixture = AuthFixture { respondAccepted() }
            val vm = CodeViewModel("a@example.com", fixture.repository)
            settle()
            assertEquals(RESEND_INTERVAL_SECONDS, vm.state.value.resendSecondsLeft)
            vm.resend()
            settle()
            assertEquals(0, fixture.requests.size) // пауза ещё не прошла

            testScheduler.advanceTimeBy(RESEND_INTERVAL_SECONDS * 1000L + 100)
            settle()
            assertEquals(0, vm.state.value.resendSecondsLeft)
            vm.resend()
            vm.state.first { it.resent }
            assertEquals(1, fixture.requests.size)
            assertEquals(RESEND_INTERVAL_SECONDS, vm.state.value.resendSecondsLeft)
        }

    @Test
    fun `регистрация проверяет поля локально`() =
        runTest(dispatcher) {
            val fixture = AuthFixture { json(TOKENS_JSON, HttpStatusCode.Created) }
            val vm = RegisterViewModel("reg-1", fixture.repository)
            vm.onUsernameChange("ab")
            vm.onDisplayNameChange("  ")
            vm.submit()
            settle()
            assertEquals(FieldErrorReason.TOO_SHORT, vm.state.value.usernameError)
            assertEquals(FieldErrorReason.REQUIRED, vm.state.value.displayNameError)
            assertEquals(0, fixture.requests.size)
        }

    @Test
    fun `успешная регистрация открывает сессию`() =
        runTest(dispatcher) {
            val fixture = AuthFixture { json(TOKENS_JSON, HttpStatusCode.Created) }
            val vm = RegisterViewModel("reg-1", fixture.repository)
            vm.onUsernameChange("new_user")
            vm.onDisplayNameChange("Новый Пользователь")
            vm.submit()
            fixture.session.state.first { it == AuthState.SignedIn }
            assertNull(vm.state.value.error)
        }

    @Test
    fun `занятое имя пользователя показывается как ошибка сервера`() =
        runTest(dispatcher) {
            val fixture = AuthFixture { json(problem(409, "USERNAME_TAKEN"), HttpStatusCode.Conflict) }
            val vm = RegisterViewModel("reg-1", fixture.repository)
            vm.onUsernameChange("taken_name")
            vm.onDisplayNameChange("Имя")
            vm.submit()
            val error = vm.state.first { it.error != null }.error
            assertIs<AppError.Api>(error)
            assertEquals(app.podgon.contract.ErrorCode.USERNAME_TAKEN, error.code)
        }
}

private fun io.ktor.client.engine.mock.MockRequestHandleScope.respondAccepted() = respond("", HttpStatusCode.Accepted)
