package ai.serenade.intellij.services

import ai.serenade.intellij.listeners.MyToolWindowListener
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.layout.panel
import io.ktor.util.KtorExperimentalAPI

class ToolWindowService(private val project: Project) {
    @KtorExperimentalAPI
    fun setContent(connected: Boolean) {
        val window = ToolWindowManager.getInstance(project).getToolWindow("Serenade")
            ?: return

        val contentFactory = ContentFactory.SERVICE.getInstance()
        val installed = Settings().installed()

        val content = contentFactory.createContent(
            panel {
                if (installed) {
                    titledRow("Welcome to Serenade!") {
                        noteRow("To get started, run the Serenade app.")
                    }
                    titledRow("Connection Status") {
                        if (connected) {
                            noteRow("Connected!\nThis tool window can be closed.")
                        } else {
                            noteRow("Disconnected!\nIs the Serenade desktop app running?")
                            row {
                                button(
                                    "Reconnect",
                                    actionListener = {
                                        project.service<IpcService>().start()
                                    }
                                )
                            }
                        }
                    }
                } else {
                    titledRow("Welcome to Serenade!") {
                        noteRow("To get started, download the Serenade app:")
                        row {
                            button(
                                "Download app",
                                actionListener = {
                                    BrowserUtil.browse("https://serenade.ai/")
                                }
                            )
                        }
                        row {
                            button(
                                "Reload plugin",
                                actionListener = {
                                    MyToolWindowListener(project).toolWindowShown(
                                        "Serenade",
                                        window
                                    )
                                }
                            )
                        }
                    }
                }
            },
            "",
            false
        )
        val set: () -> Unit = set@{
            window.contentManager.removeAllContents(true)
            window.contentManager.addContent(content)
        }
        ApplicationManager.getApplication().invokeLater(set)
    }
}
