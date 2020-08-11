package ai.serenade.intellij

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.layout.panel

class MainToolWindow : ToolWindowFactory {
    override fun createToolWindowContent(p0: Project, p1: ToolWindow) {
        val contentFactory: ContentFactory = ContentFactory.SERVICE.getInstance()
        val content: Content = contentFactory.createContent(
            panel {
                noteRow("Welcome to Serenade!")
                noteRow(
                    "With Serenade, you can write code faster&mdash;by speaking in plain English, " +
                        "rather than typing. Use Serenade as your coding assistant, or abandon your keyboard entirely."
                )
                noteRow("To get started, download the Serenade app and run it alongside IntelliJ.")
                noteRow("<a class=\"serenade-download\" href=\"https://serenade.ai/\">Download</a>")
            },
            "", false
        )
        p1.contentManager.addContent(content)
    }
}
