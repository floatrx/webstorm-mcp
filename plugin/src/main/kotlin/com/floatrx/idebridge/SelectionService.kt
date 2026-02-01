package com.floatrx.idebridge

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile

/**
 * Service to retrieve current editor selection from the IDE.
 */
object SelectionService {

    data class Selection(
        val text: String,
        val filePath: String,
        val startLine: Int,
        val endLine: Int,
        val startColumn: Int,
        val endColumn: Int,
        val language: String,
        val projectName: String
    ) {
        fun toJson(): String = buildString {
            append("{")
            append("\"text\":${text.escapeJson()},")
            append("\"filePath\":${filePath.escapeJson()},")
            append("\"startLine\":$startLine,")
            append("\"endLine\":$endLine,")
            append("\"startColumn\":$startColumn,")
            append("\"endColumn\":$endColumn,")
            append("\"language\":${language.escapeJson()},")
            append("\"projectName\":${projectName.escapeJson()}")
            append("}")
        }

        private fun String.escapeJson(): String {
            val escaped = this
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
            return "\"$escaped\""
        }
    }

    fun getSelection(): Selection? {
        var result: Selection? = null

        ApplicationManager.getApplication().invokeAndWait {
            result = getSelectionInternal()
        }

        return result
    }

    private fun getSelectionInternal(): Selection? {
        // Get the currently active project
        val project = ProjectManager.getInstance().openProjects.firstOrNull() ?: return null

        // Get the currently active editor
        val editor: Editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return null

        // Get selection model
        val selectionModel = editor.selectionModel
        val caretModel = editor.caretModel
        val document = editor.document

        // Get selected text (may be empty if no selection)
        val selectedText = selectionModel.selectedText ?: ""

        // Get cursor/selection positions
        val startOffset = if (selectionModel.hasSelection()) selectionModel.selectionStart else caretModel.offset
        val endOffset = if (selectionModel.hasSelection()) selectionModel.selectionEnd else caretModel.offset

        val startLine = document.getLineNumber(startOffset) + 1 // 1-indexed
        val endLine = document.getLineNumber(endOffset) + 1
        val startColumn = startOffset - document.getLineStartOffset(startLine - 1) + 1
        val endColumn = endOffset - document.getLineStartOffset(endLine - 1) + 1

        // Get file info
        val virtualFile: VirtualFile? = FileEditorManager.getInstance(project).selectedFiles.firstOrNull()
        val filePath = virtualFile?.path ?: ""

        // Get language info
        val psiFile: PsiFile? = virtualFile?.let {
            PsiDocumentManager.getInstance(project).getPsiFile(document)
        }
        val language = psiFile?.language?.displayName ?: virtualFile?.extension?.uppercase() ?: "Unknown"

        return Selection(
            text = selectedText,
            filePath = filePath,
            startLine = startLine,
            endLine = endLine,
            startColumn = startColumn,
            endColumn = endColumn,
            language = language,
            projectName = project.name
        )
    }
}
