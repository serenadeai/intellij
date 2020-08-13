package ai.serenade.intellij.services

import com.intellij.openapi.project.Project
import io.ktor.client.HttpClient
import io.ktor.client.features.websocket.DefaultClientWebSocketSession
import io.ktor.client.features.websocket.WebSockets
import io.ktor.client.features.websocket.ws
import io.ktor.http.cio.websocket.Frame
import io.ktor.http.cio.websocket.readText
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration
import java.net.ConnectException
import java.util.UUID

@io.ktor.util.KtorExperimentalAPI
@kotlinx.serialization.UnstableDefault
class IpcService(private val project: Project) {
    // app name for the client
    private val appName = "intellij"
    private var notifier: Notifier = Notifier(project)
    private var commandHandler: CommandHandler = CommandHandler(project)
    private var toolWindow = ToolWindowService(project)

    private val client = HttpClient {
        install(WebSockets)
    }
    var webSocketSession: DefaultClientWebSocketSession? = null
    private var heartbeatScope: Job? = null
    private var id: String = UUID.randomUUID().toString()

    private val json = Json(
        JsonConfiguration.Default.copy(
            encodeDefaults = false, // don't include all the null values
            ignoreUnknownKeys = true, // don't break on parsing unknown responses
            isLenient = true // empty strings
        )
    )

    fun start() {
        GlobalScope.launch {
            try {
                connect()
                // wait for the session to be closed by the client in another
                // coroutine
                GlobalScope.launch {
                    webSocketSession?.closeReason?.await()
                    notifier.notify("Disconnected")
                    heartbeatScope?.cancel()
                    toolWindow.setContent(false)
                    webSocketSession = null
                }
            } catch (e: ConnectException) {
                notifier.notify("Could not connect")
                heartbeatScope?.cancel()
                toolWindow.setContent(false)
            } catch (e: Exception) {
                notifier.notify("Could not connect: $e")
                heartbeatScope?.cancel()
                toolWindow.setContent(false)
            }
        }
    }

    private suspend fun connect() {
        if (webSocketSession == null) {
            client.ws(
                host = "localhost",
                port = 17373,
                path = "/"
            ) {
                webSocketSession = this
                // send a heartbeat in a separate coroutine
                id = UUID.randomUUID().toString()
                heartbeatScope = GlobalScope.launch {
                    while (isActive) {
                        sendHeartbeat()
                        delay(60 * 1000)
                    }
                }
                notifier.notify("Connected")
                toolWindow.setContent(true)

                // Receive frames
                for (frame in incoming) {
                    if (frame is Frame.Text) {
                        onMessage(frame)
                    }
                }
            }
        }
    }

    private suspend fun sendHeartbeat() {
        // Send text frame of heartbeat
        webSocketSession?.send(
            Frame.Text(
                json.stringify(
                    Response.serializer(),
                    Response(
                        "heartbeat",
                        ResponseData(
                            appName,
                            id
                        )
                    )
                )
            )
        )
    }

    private fun onMessage(frame: Frame.Text) {
        try {
            val request = json.parse(Request.serializer(), frame.readText())
//            notify(request.message)

            val callback = request.data.callback
            if (callback !== null) {
                request.data.response?.execute?.commandsList?.let {
//                    notify(it.toString())
                    for (command in it) {
//                        notify(command.type)
                        when (command.type) {
                            "COMMAND_TYPE_GET_EDITOR_STATE" -> {
                                commandHandler.sendEditorState(callback, webSocketSession!!)
                            }
                            "COMMAND_TYPE_DIFF" -> {
                                commandHandler.diff(command)
                            }
                            "COMMAND_TYPE_CLOSE_TAB" -> {
                                commandHandler.closeTab()
                            }
                            else -> {
                                notifier.notify("Command type not implemented: " + command.type)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            notifier.notify("Failed to parse or execute" + frame.readText())
            notifier.notify(e.toString())
        }
    }
}
