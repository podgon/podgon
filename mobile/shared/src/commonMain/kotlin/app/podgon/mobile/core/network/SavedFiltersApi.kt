package app.podgon.mobile.core.network

import app.podgon.contract.CreateSavedFilterRequest
import app.podgon.contract.SavedFilter
import app.podgon.contract.SavedFilterOrderRequest
import app.podgon.contract.UpdateSavedFilterRequest
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody

/** Сохранённые фильтры и закреплённые категории (`api/openapi.yaml`, тег filters; клиент с авторизацией). */
class SavedFiltersApi(
    private val client: HttpClient,
) {
    suspend fun list(): Result<List<SavedFilter>> = apiCall { client.get("api/v1/me/filters").bodyOrThrow() }

    suspend fun create(request: CreateSavedFilterRequest): Result<SavedFilter> =
        apiCall { client.post("api/v1/me/filters") { setBody(request) }.bodyOrThrow() }

    suspend fun update(
        id: String,
        request: UpdateSavedFilterRequest,
    ): Result<SavedFilter> = apiCall { client.patch("api/v1/me/filters/$id") { setBody(request) }.bodyOrThrow() }

    suspend fun delete(id: String): Result<Unit> =
        apiCall { client.delete("api/v1/me/filters/$id").bodyOrThrow<Unit>() }

    suspend fun order(request: SavedFilterOrderRequest): Result<List<SavedFilter>> =
        apiCall { client.put("api/v1/me/filters/order") { setBody(request) }.bodyOrThrow() }
}
