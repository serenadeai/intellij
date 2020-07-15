package com.github.ummcheng.intellij.services

import com.intellij.openapi.project.Project
import com.github.ummcheng.intellij.MyBundle

class MyProjectService(project: Project) {

    init {
        println(MyBundle.message("projectService", project.name))
    }
}
