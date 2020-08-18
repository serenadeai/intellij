package ai.serenade.intellij.services

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.CaretState
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.project.Project
import io.ktor.client.features.websocket.DefaultClientWebSocketSession
import io.ktor.http.cio.websocket.Frame
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

class CommandHandler(private val project: Project) {
    private val notifier = Notifier(project)
    private val json = Json(jsonConfiguration)
    private var webSocketSession: DefaultClientWebSocketSession? = null

    fun handle(
        clientRequest: RequestData,
        newWebSocketSession: DefaultClientWebSocketSession
    ) {
//        notifier.notify(clientRequest.message)
        webSocketSession = newWebSocketSession
        val callback = clientRequest.callback
        val commandsList = clientRequest.response?.execute?.commandsList

        if (callback != null && commandsList != null) {
            // runs commands in order and sends callback after the last one
            runCommandsInQueue(callback, commandsList)
        }
    }

    private fun runCommandsInQueue(
        callback: String,
        commandsList: List<Command>,
        data: CallbackData? = null
    ) {
        if (commandsList.isEmpty()) {
//            notifier.notify(callback)
//            notifier.notify(data.toString())
            sendCallback(callback, data)
        } else {
            val command = commandsList.first()
            val remainingCommands = commandsList.takeLast(commandsList.size - 1)
            when (command.type) {
                "COMMAND_TYPE_CLOSE_TAB" -> {
                    invokeRead(callback, remainingCommands) { closeTab() }
                }
                "COMMAND_TYPE_COPY" -> {
                }
                "COMMAND_TYPE_CREATE_TAB" -> {
                }
                "COMMAND_TYPE_DIFF" -> {
                    invokeWrite(callback, remainingCommands) { diff(command) }
                }
                "COMMAND_TYPE_GET_EDITOR_STATE" -> {
                    invokeRead(callback, remainingCommands) { sendEditorState() }
                }
                "COMMAND_TYPE_NEXT_TAB" -> {
                    invokeRead(callback, remainingCommands) { rotateTab(1) }
                }
                "COMMAND_TYPE_PASTE" -> {
                }
                "COMMAND_TYPE_PREVIOUS_TAB" -> {
                    invokeRead(callback, remainingCommands) { rotateTab(-1) }
                }
                "COMMAND_TYPE_REDO" -> {
                }
                "COMMAND_TYPE_SAVE" -> {
                }
                "COMMAND_TYPE_SELECT" -> {
                    invokeWrite(callback, remainingCommands) { select(command) }
                }
                "COMMAND_TYPE_SWITCH_TAB" -> {
                    if (command.index != null) {
                        invokeRead(callback, remainingCommands) { switchTab(command.index - 1) }
                    }
                }
                "COMMAND_TYPE_UNDO" -> {
                }
                else -> {
                    /*
                     * Not supported (client runs):
                     * - COMMAND_TYPE_PRESS
                     * - ...
                     */
//                    notifier.notify("Command type not implemented: " + command.type)
                    runCommandsInQueue(callback, remainingCommands, data)
                }
            }
        }
    }

    private fun sendCallback(callback: String, data: CallbackData?) {
        GlobalScope.launch {
            webSocketSession?.send(
                Frame.Text(
                    json.stringify(
                        Response.serializer(),
                        Response(
                            "callback", ResponseData(null, null, callback, data)
                        )
                    )
                )
            )
        }
    }

    /*
     * Wrappers
     */

    // run some read action and then run remaining commands
    private fun invokeRead(
        callback: String,
        remainingCommands: List<Command>,
        read: () -> CallbackData?
    ) {
        ApplicationManager.getApplication().invokeLater {
            runCommandsInQueue(callback, remainingCommands, read())
        }
    }

    // run some write action and then run remaining commands
    private fun invokeWrite(
        callback: String,
        remainingCommands: List<Command>,
        write: () -> CallbackData?
    ) {
        WriteCommandAction.runWriteCommandAction(project) {
            runCommandsInQueue(callback, remainingCommands, write())
        }
    }

    /*
     * Tab management
     */

    private fun closeTab(): CallbackData? {
        // close tab
        val manager = FileEditorManagerEx.getInstanceEx(project)
        manager.currentFile?.let { manager.closeFile(it) }
        return null
    }

    private fun rotateTab(direction: Int): CallbackData? {
        val manager = FileEditorManagerEx.getInstanceEx(project)
        val window = manager.currentWindow
        var index = 0
        // find the current tab index and shift
        for (i in 0 until window.tabCount) {
            val editor = window.editors[i]
            if (editor == window.selectedEditor) {
                index = i
            }
        }
        index += direction
        // switch tab will catch over/underflow
        return switchTab(index)
    }

    private fun switchTab(index: Int): CallbackData? {
        val manager = FileEditorManagerEx.getInstanceEx(project)
        val window = manager.currentWindow
        // catch over/underflow
        var newIndex = index
        if (index < 0) {
            newIndex = window.editors.size - 1
        }
        if (index >= window.editors.size) {
            newIndex = 0
        }
        // switch tab
        window.setSelectedEditor(window.editors[newIndex], true)
        return null
    }

    /*
     * Editor state
     */

    private fun cursorToLogicalPosition(source: String, cursor: Int): LogicalPosition {
        // iterate until the given substring index,
        // incrementing rows and columns as we go
        var row = 0
        var column = 0
        for (i in 0 until cursor) {
            column++
            if (source[i] == '\n') {
                row++
                column = 0
            }
        }
        return LogicalPosition(row, column)
    }

    private fun diff(command: Command): CallbackData? {
        val manager = FileEditorManagerEx.getInstanceEx(project)
        val editor = manager.selectedTextEditor
        if (editor == null) {
            notifier.notify("no selected text editor")
            return null
        }

        // set source and cursor
        if (command.source != null && command.cursor != null) {
            editor.document.replaceString(
                0, editor.document.textLength, command.source
            )
            val cursor = cursorToLogicalPosition(command.source, command.cursor)
            editor.caretModel.caretsAndSelections = listOf(
                CaretState(cursor, null, null)
            )
        }
        return null
    }

    private fun select(command: Command): CallbackData? {
        val manager = FileEditorManagerEx.getInstanceEx(project)
        val editor = manager.selectedTextEditor
        if (editor == null) {
            notifier.notify("no selected text editor")
            return null
        }

        // set cursor
        if (command.source != null &&
            command.cursor != null &&
            command.cursorEnd != null
        ) {
            val cursor = cursorToLogicalPosition(
                command.source, command.cursor
            )
            val cursorEnd = cursorToLogicalPosition(
                command.source, command.cursorEnd
            )

            editor.caretModel.caretsAndSelections = listOf(
                CaretState(cursor, cursor, cursorEnd)
            )
        }
        return null
    }

    private fun sendEditorState(): CallbackData? {
        val manager = FileEditorManagerEx.getInstanceEx(project)
        val editor = manager.selectedTextEditor
        if (editor == null) {
            notifier.notify("no selected text editor")
            return null
        }

        // build editor state data
        val document = editor.document
        val source = document.text
        val cursor = editor.selectionModel.selectionStart
        val filename = document.let {
            FileDocumentManager.getInstance().getFile(it)?.name
        }
        val files: List<String> = emptyList() // TODO
        val roots: List<String> = listOf(project.basePath ?: "")
        val tabs: List<String> = manager.currentWindow.files.map { it.name }

        return CallbackData(
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
    }
}
