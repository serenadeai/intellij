package ai.serenade.intellij.services

import kotlinx.serialization.* // ktlint-disable no-wildcard-imports
import kotlinx.serialization.json.Json

// From client app
@Serializable
data class Request(
    val message: String,
    val data: RequestData
)

@Serializable
data class RequestData(
    val callback: String? = null,
    val response: ClientRequest? = null
)

@Serializable
data class ClientRequest(
    val execute: Execute? = null,
    val alternativesList: List<Alternatives>? = null
)

@Serializable
data class Alternatives(
    val commandsList: List<Command>? = null
)

@Serializable
data class Execute(
    val commandsList: List<Command>? = null
)

@Serializable
data class Command(
    val type: String,
    val source: String? = null,
    val cursor: Int? = null,
    val cursorEnd: Int? = null,
    val index: Int? = null,
    val text: String? = null,
    val direction: String? = null,
    val path: String? = null
)

// To client app
@Serializable
data class Response(
    val message: String,
    val data: ResponseData
)

@Serializable
data class ResponseData(
    // Heartbeat
    val app: String? = null,
    val id: String? = null,
    // Callback
    val callback: String? = null,
    val data: CallbackData? = null
)

@Serializable
data class CallbackData(
    val message: String? = null,
    val data: NestedData? = null
)

@Serializable
data class NestedData(
    // EditorState
    val source: String? = null,
    val cursor: Int? = null,
    val filename: String? = null,
    val files: List<String>? = null,
    var roots: List<String>? = null,
    var tabs: List<String>? = null,

    // OPEN_FILE_LIST
    val text: String? = null,
)

val json = Json {
    encodeDefaults = false; // don't include all the null values
    ignoreUnknownKeys = true; // don't break on parsing unknown responses
    isLenient = true // empty strings
}
