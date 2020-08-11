package ai.serenade.intellij.services

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.layout.panel

class ToolWindowService(private val project: Project) {
    fun setContent(connected: Boolean) {
        val window = ToolWindowManager.getInstance(project).getToolWindow("Serenade")
        val contentFactory = ContentFactory.SERVICE.getInstance()
        val content = contentFactory.createContent(
            panel {
                titledRow("Welcome to Serenade!") {
                    noteRow("To get started, download the Serenade desktop app \nand run it alongside IntelliJ.")
                    row {
                        link("Download") {
                            BrowserUtil.browse("https://serenade.ai/")
                        }
                    }
                }
                titledRow("Connection Status") {
                    if (connected)
                        noteRow("Connected to the Serenade desktop app.")
                    else
                        noteRow("Disconnected. Is the Serenade desktop app running?\n\nTo reconnect, close and reopen this panel.")
                }
            },
            "", false
        )
        val set: () -> Unit = set@{
            window?.contentManager?.removeAllContents(true)
            window?.contentManager?.addContent(content)
        }
        ApplicationManager.getApplication().invokeLater(set)
    }
}
