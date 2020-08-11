package ai.serenade.intellij.services

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.project.Project
import io.ktor.client.features.websocket.DefaultClientWebSocketSession
import io.ktor.http.cio.websocket.Frame
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration

class CommandHandler(private val project: Project) {
    private val notifier = Notifier(project)

    private val json = Json(JsonConfiguration.Default.copy(
        encodeDefaults = false, // don't include all the null values
        ignoreUnknownKeys = true, // don't break on parsing unknown responses
        isLenient = true // empty strings
    ))

    fun closeTab() {
        val read: () -> Unit = read@{
            val manager = FileEditorManagerEx.getInstanceEx(project)
            manager.currentFile?.let { manager.closeFile(it) }
        }
        ApplicationManager.getApplication().invokeLater(read)
    }

    fun diff(command: Command) {
        val write: () -> Unit = write@{
            val editor = FileEditorManager.getInstance(project).selectedTextEditor
            if (editor == null) {
                notifier.notify("no selected text editor")
                return@write
            }
            command.source?.let { editor.document.replaceString(0, editor.document.textLength, it) }
            val cursor = command.cursor ?: 0
            editor.caretModel.moveToOffset(cursor)
        }
        WriteCommandAction.runWriteCommandAction(project, write)
    }

    fun sendEditorState(callback: String, webSocketSession: DefaultClientWebSocketSession) {
        val read: () -> Unit = read@{
            val manager = FileEditorManagerEx.getInstanceEx(project)
            val editor = manager.selectedTextEditor
            if (editor == null) {
                notifier.notify("no selected text editor")
                return@read
            }

            val document = editor.document
            val source = document.text
            val cursor = editor.selectionModel.selectionStart
            val filename = document.let { FileDocumentManager.getInstance().getFile(it)?.name }
            val files: List<String> = emptyList() // TODO
            val roots: List<String> = listOf(project.basePath ?: "")
            val tabs: List<String> = manager.currentWindow.files.map { it.name }

            val frame = Frame.Text(json.stringify(
                Response.serializer(),
                Response("callback", ResponseData(
                    null, null,
                    callback,
                    CallbackData(
                        "editorState",
                        NestedData(
                            source,
                            cursor,
                            filename,
                            files,
                            roots,
                            tabs
                        )
                    )
                ))
            ))

            GlobalScope.launch {
                webSocketSession.send(frame)
            }
        }

        ApplicationManager.getApplication().invokeLater(read)
    }
}

