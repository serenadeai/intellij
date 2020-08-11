package ai.serenade.intellij.listeners

import ai.serenade.intellij.services.IpcService
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManagerListener
import io.ktor.util.KtorExperimentalAPI
import kotlinx.coroutines.ExperimentalCoroutinesApi

class MyProjectManagerListener : ProjectManagerListener {
    @KtorExperimentalAPI
    @kotlinx.serialization.UnstableDefault
    @ExperimentalCoroutinesApi
    override fun projectOpened(p: Project) {
        val projectService = p.service<IpcService>()
        projectService.start()
    }
}
