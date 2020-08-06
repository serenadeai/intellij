package ai.serenade.intellij.services

import com.intellij.notification.NotificationDisplayType
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import io.ktor.client.HttpClient
import io.ktor.client.features.websocket.DefaultClientWebSocketSession
import io.ktor.client.features.websocket.WebSockets
import io.ktor.client.features.websocket.ws
import io.ktor.http.cio.websocket.Frame
import io.ktor.http.cio.websocket.readText
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration

@kotlinx.serialization.UnstableDefault
class MyProjectService {
    private var project: Project? = null

    private val json = Json(JsonConfiguration.Default.copy(
        encodeDefaults = false, // don't include all the null values
        ignoreUnknownKeys = true, // don't break on parsing unknown responses
        isLenient = true // empty strings
    ))
    private var webSocketSession: DefaultClientWebSocketSession? = null

    private val notificationGroup = NotificationGroup("Serenade", NotificationDisplayType.BALLOON, true)
    private fun notify(message: String) {
        val notification = notificationGroup.createNotification(
            message, NotificationType.INFORMATION
        )
        Notifications.Bus.notify(notification, project)
    }

    @ExperimentalCoroutinesApi
    fun start(p: Project) {
        project = p
        notify("id: $project.name")

        startWebsocket()
    }

    @ExperimentalCoroutinesApi
    private fun startWebsocket() {
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
                            UUID.randomUUID().toString()
                        )
                    )
                )))

                // Receive frames
                incoming.consumeEach { frame ->
                    if (frame is Frame.Text) {
                        notify("ok")
                        onMessage(frame)
                    }
                }
            }
        }
    }

    private fun diff(command: Command) {
        val write: () -> Unit = write@{
            val editor = FileEditorManager.getInstance(project!!).selectedTextEditor
            if (editor == null) {
                notify("no selected text editor")
                return@write
            }
            command.source?.let { editor.document.replaceString(0, editor.document.textLength, it) }
            val cursor = command.cursor ?: 0
            editor.caretModel.moveToOffset(cursor)
        }
        WriteCommandAction.runWriteCommandAction(project, write)
    }

    private fun sendEditorState(callback: String) {
        val read: () -> Unit = read@{
            val editor = FileEditorManager.getInstance(project!!).selectedTextEditor
            if (editor == null) {
                notify("no selected text editor")
                return@read
            }

            val document = editor.document
            val source = document.text ?: ""
            val cursor = editor.selectionModel.selectionStart ?: 0
            val filename = document.let { FileDocumentManager.getInstance().getFile(it)?.name }
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

        ApplicationManager.getApplication().invokeLater(read)
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
