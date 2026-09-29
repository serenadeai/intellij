package ai.serenade.intellij.services

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.WindowManager
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.net.ConnectException
import java.util.UUID

const val RECONNECT_TIMEOUT_MS: Long = 3000

class IpcService(private val project: Project) : Disposable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val notifier = Notifier(project, scope)
    private val commandHandler = CommandHandler(project, scope)
    private val toolWindow = ToolWindowService(project)
    private val client = HttpClient(CIO) {
        install(WebSockets)
    }

    private val appName = "intellij"
    private var connectJob: Job? = null
    private var shouldNotify = true
    private var id: String = UUID.randomUUID().toString()
    private var heartbeatJob: Job? = null

    @Volatile
    var webSocketSession: DefaultClientWebSocketSession? = null
        private set

    fun start() {
        if (connectJob?.isActive == true) {
            return
        }

        connectJob = scope.launch {
            while (isActive) {
                connect()
                delay(RECONNECT_TIMEOUT_MS)
            }
        }

        WindowManager.getInstance().getFrame(project)?.addWindowListener(
            object : WindowAdapter() {
                override fun windowActivated(event: WindowEvent?) {
                    scope.launch { sendAppStatus("active") }
                }
            }
        )
    }

    private suspend fun connect() {
        try {
            if (webSocketSession == null) {
                tryConnect()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: ConnectException) {
            if (shouldNotify) {
                notifier.notify("Could not connect")
                shouldNotify = false
            }
            onClose()
        } catch (e: Exception) {
            notifier.notify("Could not connect: $e")
            onClose()
        }
    }

    private suspend fun tryConnect() {
        client.webSocket(
            host = "localhost",
            port = 17373,
            path = "/"
        ) {
            webSocketSession = this
            shouldNotify = true
            id = UUID.randomUUID().toString()

            heartbeatJob = scope.launch {
                while (isActive) {
                    sendAppStatus("heartbeat")
                    delay(60 * 1000)
                }
            }

            notifier.notify("Connected")
            toolWindow.setContent(true)

            for (frame in incoming) {
                if (frame is Frame.Text) {
                    onMessage(frame)
                }
            }
        }

        notifier.notify("Disconnected")
        onClose()
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
                val session = webSocketSession ?: return
                commandHandler.handle(request.data, session)
            }
        } catch (e: Exception) {
            notifier.notify("Failed to parse or execute: ${frame.readText()}")
            notifier.notify(e.toString())
        }
    }

    private fun onClose() {
        heartbeatJob?.cancel()
        heartbeatJob = null
        webSocketSession = null
        toolWindow.setContent(false)
    }

    override fun dispose() {
        connectJob?.cancel()
        heartbeatJob?.cancel()
        scope.cancel()
        client.close()
    }
}
