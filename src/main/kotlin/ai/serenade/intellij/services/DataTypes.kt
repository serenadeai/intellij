package ai.serenade.intellij.services

import kotlinx.serialization.Serializable

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
    val cursorEnd: Int? = null
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
    var tabs: List<String>? = null
)
