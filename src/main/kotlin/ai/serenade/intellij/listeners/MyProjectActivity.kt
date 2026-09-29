package ai.serenade.intellij.listeners

import ai.serenade.intellij.services.IpcService
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

class MyProjectActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        project.service<IpcService>().start()
    }
}
