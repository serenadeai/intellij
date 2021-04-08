package ai.serenade.intellij.services

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class Notifier(private val project: Project) {
    fun notify(message: String) {
        val notification = NotificationGroupManager
            .getInstance()
            .getNotificationGroup("Serenade")
            .createNotification(
                "Serenade: $message",
                NotificationType.INFORMATION
            )
        GlobalScope.launch {
            delay(5000)
            notification.expire()
        }
        notification.notify(project)
    }
}
