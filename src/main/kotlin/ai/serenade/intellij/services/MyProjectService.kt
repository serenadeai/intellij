package ai.serenade.intellij.services

import com.intellij.openapi.project.Project
import ai.serenade.intellij.MyBundle

class MyProjectService(project: Project) {

    init {
        println(MyBundle.message("projectService", project.name))
    }
}
