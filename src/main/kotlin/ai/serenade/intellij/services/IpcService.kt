package ai.serenade.intellij.services

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ex.WindowManagerEx
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
import kotlinx.serialization.* // ktlint-disable no-wildcard-imports
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.net.ConnectException
import java.util.UUID

const val RECONNECT_TIMEOUT_MS: Long = 3000

@io.ktor.util.KtorExperimentalAPI
class IpcService(private val project: Project) {
    private var notifier: Notifier = Notifier(project)
    private var connectScope: Job? = null
    private var shouldNotify: Boolean = true
    private var commandHandler: CommandHandler = CommandHandler(project)
    private var toolWindow = ToolWindowService(project)

    private val client = HttpClient {
        install(WebSockets)
    }

    // app name for the client
    private val appName = "intellij"
    private var id: String = UUID.randomUUID().toString()
    var webSocketSession: DefaultClientWebSocketSession? = null
    private var heartbeatScope: Job? = null

    fun start() {
        // ensure that reconnection loop is only started once
        if (connectScope == null) {
            connectScope = GlobalScope.launch {
                while (true) {
                    // No-op if connected already
                    connect()

                    // Automatically retry after a delay
                    delay(RECONNECT_TIMEOUT_MS)
                }
            }

            // listen to focus, and update plugin active state
            WindowManagerEx.getInstance().getFrame(project)
                ?.addWindowListener(
                    object : WindowAdapter() {
                        override fun windowActivated(e: WindowEvent?) {
                            GlobalScope.launch {
                                sendAppStatus("active")
                            }
                        }
                    }
                )
        }
    }

    private suspend fun connect() {
        try {
            if (webSocketSession == null) {
                tryConnect()
            }
        } catch (e: ConnectException) {
            if (shouldNotify) {
                notifier.notify("Could not connect")
                shouldNotify = false
            }
            heartbeatScope?.cancel()
            toolWindow.setContent(false)
        } catch (e: Exception) {
            notifier.notify("Could not connect: $e")
            heartbeatScope?.cancel()
            toolWindow.setContent(false)
        }
    }

    private suspend fun tryConnect() {
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
                    sendAppStatus("heartbeat")
                    delay(60 * 1000)
                }
            }

            notifier.notify("Connected")
            toolWindow.setContent(true)

            // receive frames
            for (frame in incoming) {
                if (frame is Frame.Text) {
                    onMessage(frame)
                }
            }
        }

        // wait for the session to be closed by the client:
        GlobalScope.launch {
            webSocketSession?.closeReason?.await()
            notifier.notify("Disconnected")
            shouldNotify = true
            heartbeatScope?.cancel()
            toolWindow.setContent(false)
            webSocketSession = null
        }
    }

    private suspend fun sendAppStatus(name: String) {
        webSocketSession?.send(
            Frame.Text(
                json.encodeToString(
                    Response(
                        name,
                        ResponseData(
                            app = appName,
                            id = id
                        )
                    )
                )
            )
        )
    }

    private fun onMessage(frame: Frame.Text) {
        try {
            val request = json.decodeFromString<Request>(frame.readText())
            if (request.message == "response") {
                // executes commands and sends callback via webSocketSession
                commandHandler.handle(request.data, webSocketSession!!)
            }
        } catch (e: Exception) {
            notifier.notify("Failed to parse or execute: " + frame.readText())
            notifier.notify(e.toString())
        }
    }
}
