package ai.serenade.intellij.services

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.editor.CaretState
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import io.ktor.client.features.websocket.DefaultClientWebSocketSession
import io.ktor.http.cio.websocket.Frame
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection

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
                    invokeRead(callback, remainingCommands) { copy(command) }
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
                    invokeWrite(callback, remainingCommands) { paste(command) }
                }
                "COMMAND_TYPE_PREVIOUS_TAB" -> {
                    invokeRead(callback, remainingCommands) { rotateTab(-1) }
                }
                "COMMAND_TYPE_REDO" -> {
                    invokeRead(callback, remainingCommands) { redo() }
                }
                "COMMAND_TYPE_SAVE" -> {
                    invokeRead(callback, remainingCommands) { save() }
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
                    invokeRead(callback, remainingCommands) { undo() }
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
                            "callback",
                            ResponseData(callback = callback, data = data)
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

    private fun newTab(): CallbackData? {
        val manager = FileEditorManagerEx.getInstanceEx(project)
        val window = manager.currentWindow
        // TODO
        return null
    }

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
                0,
                editor.document.textLength,
                command.source
            )
            val cursor = editor.offsetToLogicalPosition(command.cursor)
            editor.caretModel.caretsAndSelections = listOf(
                CaretState(cursor, cursor, cursor)
            )
            editor.scrollingModel.scrollToCaret(ScrollType.RELATIVE)
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
            val cursor = editor.offsetToLogicalPosition(command.cursor)
            val cursorEnd = editor.offsetToLogicalPosition(command.cursorEnd)
            editor.caretModel.caretsAndSelections = listOf(
                CaretState(cursor, cursor, cursorEnd)
            )
            editor.scrollingModel.scrollToCaret(ScrollType.RELATIVE)
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

    /*
     * Clipboard
     */

    private fun copy(command: Command): CallbackData? {
        if (command.text != null) {
            val manager = FileEditorManagerEx.getInstanceEx(project)
            val editor = manager.selectedTextEditor
            if (editor == null) {
                notifier.notify("no selected text editor")
                return null
            }
            // copy
            val copyPasteManager = CopyPasteManager.getInstance()
            copyPasteManager.setContents(StringSelection(command.text))
        }
        return null
    }

    private fun paste(command: Command): CallbackData? {
        val manager = FileEditorManagerEx.getInstanceEx(project)
        val editor = manager.selectedTextEditor
        if (editor == null) {
            notifier.notify("no selected text editor")
            return null
        }
        // paste
        val copyPasteManager = CopyPasteManager.getInstance()
        var text = copyPasteManager.getContents<String>(DataFlavor.stringFlavor)
            ?: return null

        var insertionPoint = command.cursor ?: 0
        var updatedCursor = insertionPoint
        val document = editor.document
        val source = document.text

        // if we specify a direction, it means that we want to paste as a line, so add a newline
        if (command.direction != null && !text.endsWith('\n')) {
            text += '\n'
        }

        if (command.direction != null || text.endsWith('\n')) {
            // default to paste below if there's a newline at the end
            val direction = command.direction ?: "below"

            // for below (the default), move the cursor to the start of the next line
            if (direction == "below") {
                while (insertionPoint < source.length) {
                    if (source[insertionPoint] == '\n') {
                        insertionPoint++
                        break
                    }
                    insertionPoint++
                }
            }
            // for paste above, go to the start of the current line
            else if (direction == "above") {
                // if we're at the end of a line, then move the cursor back one, or else we'll paste below
                if (source[insertionPoint] == '\n' && insertionPoint > 0) {
                    insertionPoint--
                }
                while (insertionPoint >= 0) {
                    if (source[insertionPoint] == '\n') {
                        break
                    }
                    insertionPoint--
                }
            }

            updatedCursor = insertionPoint
        }

        // move the cursor to the end of the pasted text
        updatedCursor += text.length
        if (text.endsWith('\n')) {
            updatedCursor--
        }

        diff(
            Command(
                type = "COMMAND_TYPE_DIFF",
                source = source.substring(0, insertionPoint) + text + source.substring(insertionPoint),
                cursor = updatedCursor
            )
        )
        return null
    }

    /*
     * Actions
     */

    private fun save(): CallbackData? {
        val manager = FileEditorManagerEx.getInstanceEx(project)
        val document = manager.selectedTextEditor?.document
        if (document != null) {
            val fileDocumentManager = FileDocumentManager.getInstance()
            fileDocumentManager.saveDocument(document)
        }
        return null
    }

    private fun redo(): CallbackData? {
        val manager = FileEditorManagerEx.getInstanceEx(project)
        val fileEditor = manager.selectedEditor
        val undoManager = UndoManager.getInstance(project)
        undoManager.redo(fileEditor)
        return null
    }

    private fun undo(): CallbackData? {
        val manager = FileEditorManagerEx.getInstanceEx(project)
        val fileEditor = manager.selectedEditor
        val undoManager = UndoManager.getInstance(project)
        undoManager.undo(fileEditor)
        return null
    }
}
