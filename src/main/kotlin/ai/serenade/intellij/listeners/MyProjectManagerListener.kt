package ai.serenade.intellij.listeners

import com.intellij.notification.NotificationDisplayType
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import io.ktor.client.HttpClient
import io.ktor.client.features.websocket.DefaultClientWebSocketSession
import io.ktor.client.features.websocket.WebSockets
import io.ktor.client.features.websocket.ws
import io.ktor.http.cio.websocket.Frame
import io.ktor.http.cio.websocket.readText
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration

@Serializable
data class Command(
    val type: String,
    val source: String? = null,
    val cursor: Int? = null,
    val cursorEnd: Int? = null
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
data class ClientResponse(
    val execute: Execute? = null,
    val alternativesList: List<Alternatives>? = null
)

@Serializable
data class RequestData(
    val callback: String? = null,
    val response: ClientResponse? = null
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

@Serializable
data class CallbackData(
    val message: String? = null,
    val data: NestedData? = null
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
data class Request(
    val message: String,
    val data: RequestData
)

@Serializable
data class Response(
    val message: String,
    val data: ResponseData
)

class MyProjectManagerListener(private val project: Project) : ToolWindowManagerListener {
    private val json = Json(JsonConfiguration.Default.copy(
        encodeDefaults = false, // don't include all the null values
        ignoreUnknownKeys = true, // don't break on parsing unknown responses
        isLenient = true // empty strings
    ))
    private val notificationGroup = NotificationGroup("Serenade", NotificationDisplayType.BALLOON, true)
    // TODO: pick first editor if none are selected
    private val editor = FileEditorManager.getInstance(project).selectedTextEditor
    private var webSocketSession: DefaultClientWebSocketSession? = null

    private fun notify(message: String) {
        val notification = notificationGroup.createNotification(
            message, NotificationType.INFORMATION
        )
        Notifications.Bus.notify(notification, project)
    }

    @ExperimentalCoroutinesApi
    override fun toolWindowShown(id: String, toolWindow: ToolWindow) {
        notify("id: $id")

        GlobalScope.launch {
            val client = HttpClient {
                install(WebSockets)
            }

            client.ws(
                host = "localhost",
                port = 17373,
                path = "/"
            ) {
                webSocketSession = this

                // Send text frame of heartbeat
                send(Frame.Text(json.stringify(
                    Response.serializer(),
                    Response(
                        "heartbeat",
                        ResponseData(
                            "/users/cheng/.gradle/caches/modules-2/files-2.1/com.jetbrains/jbre/jbr-11_0_7-osx-x64-b944.20/jbr/contents/home/bin/java",
                            id
                        )
                    )
                )))

                // Receive frames
                incoming.consumeEach { frame ->
                    if (frame is Frame.Text) {
                        onMessage(frame)
                    }
                }
            }
        }
    }

    private fun diff(command: Command) {
        val write: () -> Unit = {
            command.source?.let { editor?.document?.replaceString(0, editor.document.textLength, it) }
            val cursor = command.cursor ?: 0
            editor?.caretModel?.moveToOffset(cursor)
        }
        WriteCommandAction.runWriteCommandAction(project, write)
    }

    private fun sendEditorState(callback: String) {
        val read: () -> Unit = {
            val document = editor?.document
            val source = document?.text ?: ""
            val cursor = editor?.selectionModel?.selectionStart ?: 0
            val filename = document?.let { FileDocumentManager.getInstance().getFile(it)?.name }
            val files: List<String> = emptyList() // TODO
            val roots: List<String> = emptyList() // TODO
            val tabs: List<String> = emptyList() // TODO

            val frame = Frame.Text(json.stringify(
                Response.serializer(),
                Response("callback", ResponseData(
                    null, null,
                    callback,
                    CallbackData(
                        "editorState",
                        NestedData(
                            source,
                            cursor,
                            filename,
                            files,
                            roots,
                            tabs
                        )
                    )
                ))
            ))

            GlobalScope.launch {
                webSocketSession?.send(frame)
            }
        }

        ApplicationManager.getApplication().runReadAction(read)
    }

    private fun onMessage(frame: Frame.Text) {
        try {
            val request = json.parse(Request.serializer(), frame.readText())

            // might not be "response"
            notify(request.message)
//            notify(request.data.toString())

            val callback = request.data.callback

            if (callback !== null) {
                request.data.response?.execute?.commandsList?.let {
                    notify(it.toString())
                    for (command in it) {
                        notify(command.type)

                        when (command.type) {
                            "COMMAND_TYPE_GET_EDITOR_STATE" -> {
                                sendEditorState(callback)
                            }
                            "COMMAND_TYPE_DIFF" -> {
                                diff(command)
                            }
                            else -> {
                                notify("Command type not implemented: " + command.type)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            notify("Failed to parse or execute" + frame.readText())
            notify(e.toString())
        }
    }
}
