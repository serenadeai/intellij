package ai.serenade.intellij.services

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class Notifier(
    private val project: Project,
    private val scope: CoroutineScope
) {
    fun notify(message: String) {
        val notification = NotificationGroupManager
            .getInstance()
            .getNotificationGroup("Serenade")
            .createNotification(
                "Serenade: $message",
                NotificationType.INFORMATION
            )
        scope.launch {
            delay(5000)
            notification.expire()
        }
        notification.notify(project)
    }
}
