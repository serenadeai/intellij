package ai.serenade.intellij.listeners

import ai.serenade.intellij.services.IpcService
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManagerListener

class MyProjectManagerListener : ProjectManagerListener {
    @io.ktor.util.KtorExperimentalAPI
    @kotlinx.serialization.UnstableDefault
    override fun projectOpened(project: Project) {
        val projectService = project.service<IpcService>()
        projectService.start()
    }
}
