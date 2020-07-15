package com.github.ummcheng.intellij.listeners

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManagerListener
import com.github.ummcheng.intellij.services.MyProjectService

internal class MyProjectManagerListener : ProjectManagerListener {

    override fun projectOpened(project: Project) {
        project.getService(MyProjectService::class.java)
    }
}
