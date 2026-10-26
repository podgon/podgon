package app.podgon.mobile

import app.podgon.mobile.core.cache.CachedResponse
import app.podgon.mobile.core.network.ApiConfig
import app.podgon.mobile.core.network.ApiEndpoints
import app.podgon.mobile.core.network.AuthApi
import app.podgon.mobile.core.network.CLIENT_PLATFORM_HEADER
import app.podgon.mobile.core.network.CLIENT_VERSION_HEADER
import app.podgon.mobile.core.network.MeApi
import app.podgon.mobile.core.network.UpdateGate
import app.podgon.mobile.core.network.createPublicClient
import app.podgon.mobile.feature.reviews.FeedScope
import app.podgon.mobile.feature.reviews.ReviewFilter
import app.podgon.mobile.testing.GroupsFixture
import app.podgon.mobile.testing.ME_JSON
import app.podgon.mobile.testing.json
import app.podgon.mobile.testing.problem
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Замечания ревью этапа 8.1 на клиенте: версия и обновление (R1), запасные адреса (R2), кэш (R14). */
class Stage81ReviewFixesTest {
    private val config =
        ApiConfig(
            baseUrl = "https://main.test",
            fallbackBaseUrls = listOf("https://reserve.test"),
            platform = "android",
            appVersion = 42,
        )

    @Test
    fun `запросы несут платформу и номер сборки`() =
        runTest {
            val requests = mutableListOf<HttpRequestData>()
            val engine =
                MockEngine { request ->
                    requests += request
                    json(ME_JSON)
                }
            MeApi(createPublicClient(engine, config)).me()
            assertEquals("android", requests.single().headers[CLIENT_PLATFORM_HEADER])
            assertEquals("42", requests.single().headers[CLIENT_VERSION_HEADER])
        }

    @Test
    fun `ответ 426 включает экран обновления`() =
        runTest {
            val gate = UpdateGate()
            val engine = MockEngine { json(problem(426, "UPGRADE_REQUIRED"), HttpStatusCode.UpgradeRequired) }
            val result = AuthApi(createPublicClient(engine, config, gate = gate)).requestOtp("a@example.com", null)
            assertTrue(result.isFailure)
            assertTrue(gate.required.value)
        }

    @Test
    fun `при недоступном основном адресе GET уходит на живой запасной и дальше идёт туда`() =
        runTest {
            val hosts = mutableListOf<String>()
            val engine =
                MockEngine { request ->
                    hosts += "${request.method.value} ${request.url.host}${request.url.encodedPath}"
                    when (request.url.host) {
                        "main.test" -> error("адрес заблокирован")
                        else ->
                            if (request.url.encodedPath ==
                                "/actuator/health"
                            ) {
                                json("""{"status":"UP"}""")
                            } else {
                                json(ME_JSON)
                            }
                    }
                }
            val endpoints = ApiEndpoints(config)
            val me = MeApi(createPublicClient(engine, config, endpoints))
            assertTrue(me.me().isSuccess)
            assertEquals("reserve.test", endpoints.current.host)
            assertTrue(me.me().isSuccess)
            assertEquals(
                listOf(
                    "GET main.test/api/v1/me",
                    "GET reserve.test/actuator/health",
                    "GET reserve.test/api/v1/me",
                    "GET reserve.test/api/v1/me",
                ),
                hosts,
            )
        }

    @Test
    fun `изменяющий запрос не повторяется сам, но следующий идёт на запасной адрес`() =
        runTest {
            val hosts = mutableListOf<String>()
            val engine =
                MockEngine { request ->
                    hosts += "${request.method.value} ${request.url.host}${request.url.encodedPath}"
                    when {
                        request.url.host == "main.test" -> error("адрес заблокирован")
                        request.url.encodedPath == "/actuator/health" -> json("""{"status":"UP"}""")
                        else -> json("", HttpStatusCode.Accepted)
                    }
                }
            val endpoints = ApiEndpoints(config)
            val auth = AuthApi(createPublicClient(engine, config, endpoints))
            assertTrue(auth.requestOtp("a@example.com", null).isFailure)
            assertEquals("reserve.test", endpoints.current.host)
            assertTrue(auth.requestOtp("a@example.com", null).isSuccess)
            assertEquals(
                listOf(
                    "POST main.test/api/v1/auth/otp/request",
                    "GET reserve.test/actuator/health",
                    "POST reserve.test/api/v1/auth/otp/request",
                ),
                hosts,
            )
        }

    @Test
    fun `если запасные тоже недоступны - ошибка сети и адрес не меняется`() =
        runTest {
            val engine = MockEngine { error("нет сети") }
            val endpoints = ApiEndpoints(config)
            assertTrue(MeApi(createPublicClient(engine, config, endpoints)).me().isFailure)
            assertEquals("main.test", endpoints.current.host)
        }

    @Test
    fun `потеря доступа к группе убирает её ленту и фильтр из кэша`() =
        runTest {
            val fixture = GroupsFixture { json(problem(404, "NOT_FOUND"), HttpStatusCode.NotFound) }
            fixture.cacheDao.entries["feed:g1"] = CachedResponse("feed:g1", """{"items":[]}""", 0)
            fixture.cacheDao.entries["filter:g1"] = CachedResponse("filter:g1", """{"query":"x"}""", 0)
            fixture.cacheDao.entries["review:r1"] = CachedResponse("review:r1", "{}", 0)

            assertTrue(fixture.reviewsRepository.feed(FeedScope.Group("g1"), ReviewFilter(), null).isFailure)
            assertNull(fixture.cacheDao.entries["feed:g1"])
            assertNull(fixture.cacheDao.entries["filter:g1"])

            assertTrue(fixture.reviewsRepository.review("r1").isFailure)
            assertNull(fixture.cacheDao.entries["review:r1"])
        }

    @Test
    fun `сетевая ошибка кэш групп не трогает`() =
        runTest {
            val fixture = GroupsFixture { error("нет сети") }
            fixture.cacheDao.entries["feed:g1"] = CachedResponse("feed:g1", """{"items":[]}""", 0)
            val result = fixture.reviewsRepository.feed(FeedScope.Group("g1"), ReviewFilter(), null)
            assertTrue(result.getOrThrow().fromCache)
            assertTrue("feed:g1" in fixture.cacheDao.entries)
        }

    @Test
    fun `после загрузки списка групп остаются только данные своих групп`() =
        runTest {
            val fixture = GroupsFixture { json("[]") }
            listOf("feed:g1", "feed:g2", "filter:g2", "mine", "review:r1").forEach {
                fixture.cacheDao.entries[it] = CachedResponse(it, "{}", 0)
            }
            fixture.reviewsRepository.retainGroups(setOf("g1"))
            assertEquals(setOf("feed:g1", "mine", "review:r1"), fixture.cacheDao.entries.keys)
            assertFalse("filter:g2" in fixture.cacheDao.entries)
        }
}
