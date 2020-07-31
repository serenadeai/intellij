package ai.serenade.intellij.listeners

import com.google.gson.Gson
import com.intellij.notification.NotificationDisplayType
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import io.ktor.client.HttpClient
import io.ktor.client.features.websocket.WebSockets
import io.ktor.client.features.websocket.ws
import io.ktor.http.HttpMethod
import io.ktor.http.cio.websocket.Frame
import io.ktor.http.cio.websocket.readText
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch

data class Result(
    val source: String,
    val cursor: Int,
    val filename: String,
    val files: List<String>,
    var roots: List<String>,
    var tabs: List<String>
)

data class Data(
    val app: String,
    val id: String
)

data class Message(
    val message: String,
    val data: Any
)

class MyProjectManagerListener(private val project: Project) : ToolWindowManagerListener {

    override fun toolWindowShown(id: String, toolWindow: ToolWindow) {
        val notificationGroup = NotificationGroup("Serenade", NotificationDisplayType.BALLOON, true)
        val gson = Gson();
        val editor = FileEditorManager.getInstance(project).selectedTextEditor
        val document = editor?.document
        val source = document?.text ?: ""

        GlobalScope.launch {
            val client = HttpClient {
                install(WebSockets)
            }
            client.ws(
                method = HttpMethod.Get,
                host = "localhost",
                port = 17373, path = "/"
            ) {
                // Send text frame.
                send(Frame.Text(gson.toJson(
                    Message("heartbeat", Data(
                        "/users/cheng/.gradle/caches/modules-2/files-2.1/com.jetbrains/jbre/jbr-11_0_7-osx-x64-b944.20/jbr/contents/home/bin/java",
                        java.util.UUID.randomUUID().toString()
                    ))
                )))

                // Receive frames
                incoming.consumeEach { frame ->
                    if (frame is Frame.Text) {
                        val notification = notificationGroup.createNotification(
                            frame.readText(), NotificationType.INFORMATION
                        )
                        Notifications.Bus.notify(notification, project)

                        send(Frame.Text(gson.toJson(
                            Message("editorState", Result(
                                source,
                                0,
                                "",
                                emptyList(),
                                emptyList(),
                                emptyList()
                            ))
                        )))
                    }
                }
            }
        }
    }
}
