package ai.serenade.intellij.listeners

import ai.serenade.intellij.services.MyProjectService
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManagerListener

class MyProjectManagerListener : ProjectManagerListener {
    override fun projectOpened(p: Project) {
        val projectService = p.service<MyProjectService>()
        projectService.start(p)
    }
}
