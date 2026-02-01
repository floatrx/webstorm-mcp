package com.floatrx.idebridge

import com.intellij.ide.BrowserUtil
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Checks for plugin updates on GitHub releases at startup.
 */
class UpdateChecker : ProjectActivity {
    private val logger = Logger.getInstance(UpdateChecker::class.java)

    companion object {
        private const val CURRENT_VERSION = "1.1.0"
        private const val GITHUB_REPO = "floatrx/webstorm-mcp"
        private const val RELEASES_API = "https://api.github.com/repos/$GITHUB_REPO/releases/latest"
        private const val RELEASES_URL = "https://github.com/$GITHUB_REPO/releases"
    }

    override suspend fun execute(project: Project) {
        // Run in background to not block startup
        ApplicationManager.getApplication().executeOnPooledThread {
            checkForUpdates(project)
        }
    }

    private fun checkForUpdates(project: Project) {
        try {
            val client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build()

            val request = HttpRequest.newBuilder()
                .uri(URI.create(RELEASES_API))
                .header("Accept", "application/vnd.github.v3+json")
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build()

            val response = client.send(request, HttpResponse.BodyHandlers.ofString())

            if (response.statusCode() == 200) {
                val latestVersion = extractVersion(response.body())
                if (latestVersion != null && isNewerVersion(latestVersion, CURRENT_VERSION)) {
                    showUpdateNotification(project, latestVersion)
                }
            }
        } catch (e: Exception) {
            // Silently ignore - don't bother user if check fails
            logger.debug("Update check failed: ${e.message}")
        }
    }

    private fun extractVersion(json: String): String? {
        // Simple regex to extract tag_name from JSON
        val regex = """"tag_name"\s*:\s*"v?([^"]+)"""".toRegex()
        return regex.find(json)?.groupValues?.get(1)
    }

    private fun isNewerVersion(latest: String, current: String): Boolean {
        val latestParts = latest.split(".").mapNotNull { it.toIntOrNull() }
        val currentParts = current.split(".").mapNotNull { it.toIntOrNull() }

        for (i in 0 until maxOf(latestParts.size, currentParts.size)) {
            val latestPart = latestParts.getOrElse(i) { 0 }
            val currentPart = currentParts.getOrElse(i) { 0 }
            if (latestPart > currentPart) return true
            if (latestPart < currentPart) return false
        }
        return false
    }

    private fun showUpdateNotification(project: Project, latestVersion: String) {
        ApplicationManager.getApplication().invokeLater {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("IDE Bridge Updates")
                .createNotification(
                    "IDE Bridge Update Available",
                    "Version $latestVersion is available (current: $CURRENT_VERSION)",
                    NotificationType.INFORMATION
                )
                .addAction(object : com.intellij.notification.NotificationAction("Download") {
                    override fun actionPerformed(
                        e: com.intellij.openapi.actionSystem.AnActionEvent,
                        notification: com.intellij.notification.Notification
                    ) {
                        BrowserUtil.browse(RELEASES_URL)
                        notification.expire()
                    }
                })
                .notify(project)
        }
    }
}
