package ai.serenade.intellij.services

import ai.serenade.intellij.listeners.MyToolWindowListener
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.content.ContentFactory

class ToolWindowService(private val project: Project) {
    fun setContent(connected: Boolean) {
        val window = ToolWindowManager.getInstance(project).getToolWindow("Serenade")
            ?: return
        val installed = Settings().installed()

        val content = ContentFactory.getInstance().createContent(
            panel {
                group("Welcome to Serenade") {
                    row {
                        label(
                            if (installed) {
                                "To get started, run the Serenade app."
                            } else {
                                "To get started, download the Serenade app:"
                            }
                        )
                    }
                    if (!installed) {
                        row {
                            button("Download app") {
                                BrowserUtil.browse("https://serenade.ai/")
                            }
                        }
                        row {
                            button("Reload plugin") {
                                MyToolWindowListener(project).toolWindowShown(window)
                            }
                        }
                    }
                }

                if (installed) {
                    group("Connection Status") {
                        row {
                            label(
                                if (connected) {
                                    "Connected! This tool window can be closed."
                                } else {
                                    "Disconnected! Is the Serenade desktop app running?"
                                }
                            )
                        }
                        if (!connected) {
                            row {
                                button("Reconnect") {
                                    project.service<IpcService>().start()
                                }
                            }
                        }
                    }
                }
            },
            "",
            false
        )

        ApplicationManager.getApplication().invokeLater {
            window.contentManager.removeAllContents(true)
            window.contentManager.addContent(content)
        }
    }
}
