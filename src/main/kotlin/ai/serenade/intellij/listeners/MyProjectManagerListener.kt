package ai.serenade.intellij.listeners

import kotlinx.serialization.*
import kotlinx.serialization.json.*
import com.intellij.notification.NotificationDisplayType
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import io.ktor.client.HttpClient
import io.ktor.client.features.websocket.DefaultClientWebSocketSession
import io.ktor.client.features.websocket.WebSockets
import io.ktor.client.features.websocket.webSocketSession
import io.ktor.http.cio.websocket.Frame
import io.ktor.http.cio.websocket.readText
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch

@Serializable
data class Execute(
    val commandsList: List<Map<String, String>>
)

@Serializable
data class ClientResponse(
    val execute: Execute?
)

@Serializable
data class RequestData(
    val callback: String? = null,
    val response: ClientResponse? = null
)

@Serializable
data class ResponseData(
    // Heartbeat
    val app: String? = null,
    val id: String? = null,
    // EditorState
    val source: String? = null,
    val cursor: Int? = null,
    val filename: String? = null,
    val files: List<String>? = null,
    var roots: List<String>? = null,
    var tabs: List<String>? = null
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
    private val editor = FileEditorManager.getInstance(project).selectedTextEditor
    private var webSocketSession: DefaultClientWebSocketSession? = null

    private fun notify(message: String) {
        val notification = notificationGroup.createNotification(
            message, NotificationType.INFORMATION
        )
        Notifications.Bus.notify(notification, project)
    }

    override fun toolWindowShown(id: String, toolWindow: ToolWindow) {
        notify("id: $id")

        GlobalScope.launch {
            val client = HttpClient {
                install(WebSockets)
            }

            webSocketSession = client.webSocketSession(
                host = "localhost",
                port = 17373,
                path = "/"
            )

            // Send text frame of heartbeat
            webSocketSession!!.send(Frame.Text(json.stringify(
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
            webSocketSession!!.incoming.consumeEach { frame ->
                if (frame is Frame.Text) {
                    onMessage(frame)
                }
            }
        }
    }

    private suspend fun sendEditorState(callback: String) {
        val document = editor?.document
        val source = document?.text ?: ""

        webSocketSession?.send(Frame.Text(json.stringify(
            Response.serializer(),
            Response("callback", ResponseData(
                null,
                null,
                source,
                0,
                "",
                emptyList(),
                emptyList(),
                emptyList()
            ))
        )))
    }

    private suspend fun onMessage(frame: Frame.Text) {
        val request = json.parse(Request.serializer(), frame.readText())

        // might not be "response"
        notify(request.message)
//        notify(request.data.toString())

        val callback = request.data.callback

        if (callback !== null) {
            request.data.response?.execute?.commandsList?.let {
                notify(it.toString())
                for (command in it) {
                    for (value in command.values) {
                        notify(value)

                        if (value == "COMMAND_TYPE_GET_EDITOR_STATE") {
                            sendEditorState(callback)
                        }
                    }
                }
            }
        }
    }
}
