package ai.serenade.intellij.services

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.editor.CaretState
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.FileIndexFacade
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.WindowManager
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.websocket.Frame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.awt.datatransfer.StringSelection
import java.nio.file.Paths

class CommandHandler(
    private val project: Project,
    private val scope: CoroutineScope
) {
    private val notifier = Notifier(project, scope)
    private var webSocketSession: DefaultClientWebSocketSession? = null
    private var openFileList: MutableList<String>? = null

    fun handle(
        clientRequest: RequestData,
        newWebSocketSession: DefaultClientWebSocketSession
    ) {
        webSocketSession = newWebSocketSession
        val callback = clientRequest.callback
        val commandsList = clientRequest.response?.execute?.commandsList

        if (callback != null && commandsList != null) {
            runCommandsInQueue(callback, commandsList)
        }
    }

    private fun runCommandsInQueue(
        callback: String,
        commandsList: List<Command>,
        data: CallbackData? = null
    ) {
        if (commandsList.isEmpty()) {
            sendCallback(callback, data)
            return
        }

        val command = commandsList.first()
        val remainingCommands = commandsList.drop(1)

        when (command.type) {
            "COMMAND_TYPE_CLOSE_TAB" -> invokeRead(callback, remainingCommands) { closeTab() }
            "COMMAND_TYPE_COPY" -> invokeRead(callback, remainingCommands) { copy(command) }
            "COMMAND_TYPE_CREATE_TAB" -> invokeAction(callback, remainingCommands, "NewFile")
            "COMMAND_TYPE_DEBUGGER_CONTINUE" -> invokeAction(callback, remainingCommands, "Resume")
            "COMMAND_TYPE_DEBUGGER_INLINE_BREAKPOINT" -> runCommandsInQueue(callback, remainingCommands, data)
            "COMMAND_TYPE_DEBUGGER_PAUSE" -> invokeAction(callback, remainingCommands, "Pause")
            "COMMAND_TYPE_DEBUGGER_SHOW_HOVER" -> runCommandsInQueue(callback, remainingCommands, data)
            "COMMAND_TYPE_DEBUGGER_START" -> invokeAction(callback, remainingCommands, "Debug")
            "COMMAND_TYPE_DEBUGGER_STEP_INTO" -> invokeAction(callback, remainingCommands, "StepInto")
            "COMMAND_TYPE_DEBUGGER_STEP_OUT" -> invokeAction(callback, remainingCommands, "StepOut")
            "COMMAND_TYPE_DEBUGGER_STEP_OVER" -> invokeAction(callback, remainingCommands, "StepOver")
            "COMMAND_TYPE_DEBUGGER_STOP" -> invokeAction(callback, remainingCommands, "Stop")
            "COMMAND_TYPE_DEBUGGER_TOGGLE_BREAKPOINT" -> invokeAction(callback, remainingCommands, "ToggleLineBreakpoint")
            "COMMAND_TYPE_DIFF" -> invokeWrite(callback, remainingCommands, "Diff") { diff(command) }
            "COMMAND_TYPE_GET_EDITOR_STATE" -> invokeRead(callback, remainingCommands, ModalityState.any()) {
                checkModality { sendEditorState() }
            }
            "COMMAND_TYPE_NEXT_TAB" -> invokeRead(callback, remainingCommands) { rotateTab(1) }
            "COMMAND_TYPE_OPEN_FILE" -> invokeRead(callback, remainingCommands) { open(command) }
            "COMMAND_TYPE_OPEN_FILE_LIST" -> invokeRead(callback, remainingCommands) { setOpenFileList(command) }
            "COMMAND_TYPE_PREVIOUS_TAB" -> invokeRead(callback, remainingCommands) { rotateTab(-1) }
            "COMMAND_TYPE_REDO" -> invokeRead(callback, remainingCommands) { redo() }
            "COMMAND_TYPE_SAVE" -> invokeRead(callback, remainingCommands) { save() }
            "COMMAND_TYPE_SELECT" -> invokeWrite(callback, remainingCommands, "Select") { select(command) }
            "COMMAND_TYPE_SWITCH_TAB" -> command.index?.let {
                invokeRead(callback, remainingCommands) { switchTab(it - 1) }
            }
            "COMMAND_TYPE_UNDO" -> invokeRead(callback, remainingCommands) { undo() }
            else -> runCommandsInQueue(callback, remainingCommands, data)
        }
    }

    private fun sendCallback(callback: String, data: CallbackData?) {
        scope.launch {
            webSocketSession?.send(
                Frame.Text(
                    json.encodeToString(
                        Response(
                            "callback",
                            ResponseData(callback = callback, data = data)
                        )
                    )
                )
            )
        }
    }

    private fun checkModality(action: () -> CallbackData?): CallbackData? {
        return if (ModalityState.current() == ModalityState.nonModal()) {
            action()
        } else {
            CallbackData("modal", NestedData(filename = "jetbrains-modal", error = true))
        }
    }

    private fun executeAction(actionName: String) {
        val action = ActionManager.getInstance().getAction(actionName) ?: return
        val focusOwner = WindowManager.getInstance().getFrame(project)?.focusOwner
        ActionManager.getInstance().tryToExecute(
            action,
            null,
            focusOwner,
            ActionPlaces.ACTION_SEARCH,
            true
        )
    }

    private fun invokeAction(
        callback: String,
        remainingCommands: List<Command>,
        actionName: String
    ) {
        invokeRead(callback, remainingCommands) {
            executeAction(actionName)
            null
        }
    }

    private fun invokeRead(
        callback: String,
        remainingCommands: List<Command>,
        modalityState: ModalityState = ModalityState.defaultModalityState(),
        read: () -> CallbackData?
    ) {
        ApplicationManager.getApplication().invokeLater(
            { runCommandsInQueue(callback, remainingCommands, read()) },
            modalityState
        )
    }

    private fun invokeWrite(
        callback: String,
        remainingCommands: List<Command>,
        commandName: String,
        write: () -> CallbackData?
    ) {
        WriteCommandAction.writeCommandAction(project)
            .withName(commandName)
            .run<Throwable> {
                runCommandsInQueue(callback, remainingCommands, write())
            }
    }

    private fun closeTab(): CallbackData? {
        val manager = FileEditorManagerEx.getInstanceEx(project)
        manager.currentFile?.let { manager.closeFile(it) }
        return null
    }

    private fun rotateTab(direction: Int): CallbackData? {
        val window = FileEditorManagerEx.getInstanceEx(project).currentWindow ?: return null
        var index = 0
        val editors = window.allComposites
        for (i in editors.indices) {
            if (editors[i] == window.selectedComposite) {
                index = i
                break
            }
        }
        return switchTab(index + direction)
    }

    private fun switchTab(index: Int): CallbackData? {
        val window = FileEditorManagerEx.getInstanceEx(project).currentWindow ?: return null
        val editors = window.allComposites
        if (editors.isEmpty()) {
            return null
        }

        val newIndex = when {
            index < 0 -> editors.size - 1
            index >= editors.size -> 0
            else -> index
        }
        window.setSelectedComposite(editors[newIndex], true)
        return null
    }

    private fun open(command: Command): CallbackData? {
        val index = command.index ?: 0
        val path = openFileList?.getOrNull(index) ?: return null
        val virtualFile = VfsUtil.findFile(Paths.get(path), true) ?: return null
        FileEditorManagerEx.getInstanceEx(project).openFile(virtualFile, true)
        return null
    }

    private fun diff(command: Command): CallbackData? {
        val editor = FileEditorManagerEx.getInstanceEx(project).selectedTextEditor
        if (editor == null) {
            notifier.notify("no selected text editor")
            return null
        }

        if (command.source != null) {
            val source = command.source.replace(Regex("\\r\\n"), "\\n")
            editor.document.replaceString(0, editor.document.textLength, source)
            val cursor = command.cursor ?: 0
            val position = editor.offsetToLogicalPosition(cursor)
            editor.caretModel.caretsAndSelections = listOf(CaretState(position, position, position))
            editor.scrollingModel.scrollToCaret(ScrollType.RELATIVE)
        }
        return null
    }

    private fun select(command: Command): CallbackData? {
        val editor = FileEditorManagerEx.getInstanceEx(project).selectedTextEditor
        if (editor == null) {
            notifier.notify("no selected text editor")
            return null
        }

        if (command.source != null && command.cursor != null && command.cursorEnd != null) {
            val cursor = editor.offsetToLogicalPosition(command.cursor)
            val cursorEnd = editor.offsetToLogicalPosition(command.cursorEnd)
            editor.caretModel.caretsAndSelections = listOf(CaretState(cursor, cursor, cursorEnd))
            editor.scrollingModel.scrollToCaret(ScrollType.RELATIVE)
        }
        return null
    }

    private fun sendEditorState(): CallbackData {
        val manager = FileEditorManagerEx.getInstanceEx(project)
        val files: List<String> = openFileList ?: emptyList()
        val roots = listOf(project.basePath ?: "")
        val tabs = manager.currentWindow?.fileList?.map { it.name } ?: emptyList()
        val editor = manager.selectedTextEditor
        val document = editor?.document
        val filename = document?.let { FileDocumentManager.getInstance().getFile(it)?.name } ?: ""

        return CallbackData(
            "editorState",
            NestedData(
                source = document?.text ?: "",
                cursor = editor?.selectionModel?.selectionStart ?: 0,
                filename = filename,
                files = files,
                roots = roots,
                tabs = tabs
            )
        )
    }

    private fun setOpenFileList(command: Command): CallbackData {
        val basePath = project.basePath
        val query = command.path
        if (basePath != null && query != null) {
            val pattern = ".*" + query.lowercase().replace(Regex(" "), ".") + ".*"
            val projectDir = VfsUtil.findFile(Paths.get(basePath), true)
            if (projectDir != null) {
                val fileIndex = FileIndexFacade.getInstance(project)
                openFileList = mutableListOf()

                VfsUtil.processFileRecursivelyWithoutIgnored(projectDir) { file: VirtualFile ->
                    if (openFileList!!.size < 20 &&
                        !file.isDirectory &&
                        !fileIndex.isExcludedFile(file) &&
                        file.path.matches(Regex(pattern, RegexOption.IGNORE_CASE))
                    ) {
                        openFileList!!.add(file.path)
                    }
                    true
                }
            }
        }

        return CallbackData(
            "sendText",
            NestedData(text = "callback open")
        )
    }

    private fun copy(command: Command): CallbackData? {
        if (command.text != null) {
            if (FileEditorManagerEx.getInstanceEx(project).selectedTextEditor == null) {
                notifier.notify("no selected text editor")
                return null
            }
            CopyPasteManager.getInstance().setContents(StringSelection(command.text))
        }
        return null
    }

    private fun save(): CallbackData? {
        val document = FileEditorManagerEx.getInstanceEx(project).selectedTextEditor?.document
        if (document != null) {
            FileDocumentManager.getInstance().saveDocument(document)
        }
        return null
    }

    private fun redo(): CallbackData? {
        val manager = FileEditorManagerEx.getInstanceEx(project)
        val fileEditor = manager.selectedEditor ?: return null
        val undoManager = UndoManager.getInstance(project)
        if (undoManager.isRedoAvailable(fileEditor)) {
            undoManager.redo(fileEditor)
        }
        return null
    }

    private fun undo(): CallbackData? {
        val manager = FileEditorManagerEx.getInstanceEx(project)
        val fileEditor = manager.selectedEditor ?: return null
        val undoManager = UndoManager.getInstance(project)
        if (undoManager.isUndoAvailable(fileEditor)) {
            undoManager.undo(fileEditor)
        }
        return null
    }
}
