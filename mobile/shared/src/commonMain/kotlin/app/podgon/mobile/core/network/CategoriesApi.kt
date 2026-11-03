package app.podgon.mobile.core.network

import app.podgon.contract.CategorySet
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode

/** Общий набор категорий (`api/openapi.yaml`, тег categories; клиент с авторизацией). */
class CategoriesApi(
    private val client: HttpClient,
) {
    /**
     * Набор на языке профиля. [version] — версия сохранённого набора: если она не изменилась, сервер
     * отвечает 304 и результат — `null`.
     */
    suspend fun categorySet(version: String?): Result<CategorySet?> =
        apiCall {
            val response =
                client.get("api/v1/categories") {
                    version?.let { header(HttpHeaders.IfNoneMatch, "\"$it\"") }
                }
            if (response.status == HttpStatusCode.NotModified) null else response.bodyOrThrow<CategorySet>()
        }
}
