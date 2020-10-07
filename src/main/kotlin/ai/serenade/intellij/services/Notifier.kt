package ai.serenade.intellij.services

import com.intellij.notification.NotificationDisplayType
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.project.Project

class Notifier(private val project: Project) {
    private val notificationGroup = NotificationGroup(
        "Serenade",
        NotificationDisplayType.BALLOON,
        true
    )

    fun notify(message: String) {
        val notification = notificationGroup.createNotification(
            "Serenade: $message",
            NotificationType.INFORMATION
        )
        Notifications.Bus.notify(notification, project)
    }
}
