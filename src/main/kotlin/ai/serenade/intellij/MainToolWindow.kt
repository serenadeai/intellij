package ai.serenade.intellij

import ai.serenade.intellij.services.ToolWindowService
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory

class MainToolWindow : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        ToolWindowService(project).setContent(false)
    }
}
