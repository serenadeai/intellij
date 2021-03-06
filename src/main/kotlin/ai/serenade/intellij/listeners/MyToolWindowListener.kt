package ai.serenade.intellij.listeners

import ai.serenade.intellij.services.IpcService
import ai.serenade.intellij.services.ToolWindowService
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ex.ToolWindowManagerListener

@io.ktor.util.KtorExperimentalAPI
class MyToolWindowListener(private val project: Project) : ToolWindowManagerListener {
    override fun toolWindowShown(toolWindow: ToolWindow) {
        if (toolWindow.id == "Serenade") {
            val projectService = project.service<IpcService>()
            if (projectService.webSocketSession == null) {
                projectService.start()
            }

            ToolWindowService(project).setContent(
                projectService.webSocketSession != null
            )
        }
    }
}
