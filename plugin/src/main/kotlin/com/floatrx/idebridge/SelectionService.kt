package com.floatrx.idebridge

import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.impl.EditorHistoryManager
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.util.PsiTreeUtil
import git4idea.repo.GitRepositoryManager

/**
 * Service to retrieve current editor selection and IDE context.
 */
object SelectionService {

    // JSON escape helper (shared)
    private fun String.escapeJson(): String {
        val escaped = this
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
        return "\"$escaped\""
    }

    // ============ Selection ============

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
    }

    // ============ Problems/Errors ============

    data class Problem(
        val message: String,
        val severity: String,  // ERROR, WARNING, INFO
        val line: Int,
        val column: Int,
        val description: String?
    )

    data class ProblemsResult(
        val filePath: String,
        val problems: List<Problem>
    ) {
        fun toJson(): String = buildString {
            append("{")
            append("\"filePath\":${filePath.escapeJson()},")
            append("\"problems\":[")
            append(problems.joinToString(",") { p ->
                buildString {
                    append("{")
                    append("\"message\":${p.message.escapeJson()},")
                    append("\"severity\":${p.severity.escapeJson()},")
                    append("\"line\":${p.line},")
                    append("\"column\":${p.column},")
                    append("\"description\":${(p.description ?: "").escapeJson()}")
                    append("}")
                }
            })
            append("]}")
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

    // ============ Problems API ============

    fun getProblems(): ProblemsResult? {
        var result: ProblemsResult? = null

        ApplicationManager.getApplication().invokeAndWait {
            result = getProblemsInternal()
        }

        return result
    }

    private fun getProblemsInternal(): ProblemsResult? {
        val project = ProjectManager.getInstance().openProjects.firstOrNull() ?: return null
        val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return null
        val document = editor.document
        val virtualFile = FileEditorManager.getInstance(project).selectedFiles.firstOrNull()
        val filePath = virtualFile?.path ?: ""

        // Get highlights from the document
        val highlights = DaemonCodeAnalyzerImpl.getHighlights(
            document,
            HighlightSeverity.INFORMATION,
            project
        )

        val problems = highlights
            .filter { it.severity >= HighlightSeverity.INFORMATION }
            .mapNotNull { info ->
                val line = document.getLineNumber(info.startOffset) + 1
                val column = info.startOffset - document.getLineStartOffset(line - 1) + 1
                val severity = when {
                    info.severity >= HighlightSeverity.ERROR -> "ERROR"
                    info.severity >= HighlightSeverity.WARNING -> "WARNING"
                    else -> "INFO"
                }
                val message = info.description ?: info.toolTip?.replace(Regex("<[^>]*>"), "") ?: return@mapNotNull null

                Problem(
                    message = message,
                    severity = severity,
                    line = line,
                    column = column,
                    description = info.toolTip?.replace(Regex("<[^>]*>"), "")
                )
            }
            .distinctBy { "${it.line}:${it.column}:${it.message}" }
            .sortedWith(compareBy({ it.severity != "ERROR" }, { it.severity != "WARNING" }, { it.line }))

        return ProblemsResult(filePath = filePath, problems = problems)
    }

    // ============ Open Files API ============

    data class OpenFile(
        val filePath: String,
        val fileName: String,
        val isActive: Boolean,
        val isModified: Boolean
    )

    data class OpenFilesResult(
        val projectName: String,
        val files: List<OpenFile>
    ) {
        fun toJson(): String = buildString {
            append("{")
            append("\"projectName\":${projectName.escapeJson()},")
            append("\"files\":[")
            append(files.joinToString(",") { f ->
                buildString {
                    append("{")
                    append("\"filePath\":${f.filePath.escapeJson()},")
                    append("\"fileName\":${f.fileName.escapeJson()},")
                    append("\"isActive\":${f.isActive},")
                    append("\"isModified\":${f.isModified}")
                    append("}")
                }
            })
            append("]}")
        }
    }

    fun getOpenFiles(): OpenFilesResult? {
        var result: OpenFilesResult? = null

        ApplicationManager.getApplication().invokeAndWait {
            result = getOpenFilesInternal()
        }

        return result
    }

    private fun getOpenFilesInternal(): OpenFilesResult? {
        val project = ProjectManager.getInstance().openProjects.firstOrNull() ?: return null
        val fileEditorManager = FileEditorManager.getInstance(project)

        val openFiles = fileEditorManager.openFiles
        val selectedFile = fileEditorManager.selectedFiles.firstOrNull()

        val files = openFiles.map { vf ->
            OpenFile(
                filePath = vf.path,
                fileName = vf.name,
                isActive = vf == selectedFile,
                isModified = fileEditorManager.getEditors(vf).any {
                    it.isModified
                }
            )
        }

        return OpenFilesResult(projectName = project.name, files = files)
    }

    // ============ Git Status API ============

    data class GitFileChange(
        val filePath: String,
        val status: String  // MODIFIED, ADDED, DELETED, UNTRACKED
    )

    data class GitStatusResult(
        val branch: String,
        val repoRoot: String,
        val changes: List<GitFileChange>
    ) {
        fun toJson(): String = buildString {
            append("{")
            append("\"branch\":${branch.escapeJson()},")
            append("\"repoRoot\":${repoRoot.escapeJson()},")
            append("\"changes\":[")
            append(changes.joinToString(",") { c ->
                buildString {
                    append("{")
                    append("\"filePath\":${c.filePath.escapeJson()},")
                    append("\"status\":${c.status.escapeJson()}")
                    append("}")
                }
            })
            append("]}")
        }
    }

    fun getGitStatus(): GitStatusResult? {
        var result: GitStatusResult? = null

        ApplicationManager.getApplication().invokeAndWait {
            result = getGitStatusInternal()
        }

        return result
    }

    private fun getGitStatusInternal(): GitStatusResult? {
        val project = ProjectManager.getInstance().openProjects.firstOrNull() ?: return null
        val gitRepoManager = GitRepositoryManager.getInstance(project)
        val repo = gitRepoManager.repositories.firstOrNull() ?: return null

        val branch = repo.currentBranchName ?: "HEAD"
        val repoRoot = repo.root.path

        // Get changed files from the change list manager
        val changeListManager = com.intellij.openapi.vcs.changes.ChangeListManager.getInstance(project)
        val changes = mutableListOf<GitFileChange>()

        // Modified/Added/Deleted files
        changeListManager.allChanges.forEach { change ->
            val filePath = change.afterRevision?.file?.path ?: change.beforeRevision?.file?.path ?: return@forEach
            val status = when (change.type) {
                com.intellij.openapi.vcs.changes.Change.Type.NEW -> "ADDED"
                com.intellij.openapi.vcs.changes.Change.Type.DELETED -> "DELETED"
                com.intellij.openapi.vcs.changes.Change.Type.MOVED -> "MOVED"
                else -> "MODIFIED"
            }
            changes.add(GitFileChange(filePath = filePath, status = status))
        }

        // Untracked files
        changeListManager.unversionedFilesPaths.forEach { filePath ->
            changes.add(GitFileChange(filePath = filePath.path, status = "UNTRACKED"))
        }

        return GitStatusResult(branch = branch, repoRoot = repoRoot, changes = changes)
    }

    // ============ Recent Files API ============

    data class RecentFile(
        val filePath: String,
        val fileName: String
    )

    data class RecentFilesResult(
        val projectName: String,
        val files: List<RecentFile>
    ) {
        fun toJson(): String = buildString {
            append("{")
            append("\"projectName\":${projectName.escapeJson()},")
            append("\"files\":[")
            append(files.joinToString(",") { f ->
                buildString {
                    append("{")
                    append("\"filePath\":${f.filePath.escapeJson()},")
                    append("\"fileName\":${f.fileName.escapeJson()}")
                    append("}")
                }
            })
            append("]}")
        }
    }

    fun getRecentFiles(limit: Int = 15): RecentFilesResult? {
        var result: RecentFilesResult? = null

        ApplicationManager.getApplication().invokeAndWait {
            result = getRecentFilesInternal(limit)
        }

        return result
    }

    private fun getRecentFilesInternal(limit: Int): RecentFilesResult? {
        val project = ProjectManager.getInstance().openProjects.firstOrNull() ?: return null

        val recentFiles = EditorHistoryManager.getInstance(project)
            .fileList
            .take(limit)
            .map { vf ->
                RecentFile(
                    filePath = vf.path,
                    fileName = vf.name
                )
            }

        return RecentFilesResult(projectName = project.name, files = recentFiles)
    }

    // ============ Symbol at Cursor API ============

    data class SymbolInfo(
        val name: String,
        val kind: String,        // function, class, variable, property, etc.
        val filePath: String,
        val line: Int,
        val text: String         // The full text/signature of the element
    ) {
        fun toJson(): String = buildString {
            append("{")
            append("\"name\":${name.escapeJson()},")
            append("\"kind\":${kind.escapeJson()},")
            append("\"filePath\":${filePath.escapeJson()},")
            append("\"line\":$line,")
            append("\"text\":${text.escapeJson()}")
            append("}")
        }
    }

    fun getSymbolAtCursor(): SymbolInfo? {
        var result: SymbolInfo? = null

        ApplicationManager.getApplication().invokeAndWait {
            result = getSymbolAtCursorInternal()
        }

        return result
    }

    private fun getSymbolAtCursorInternal(): SymbolInfo? {
        val project = ProjectManager.getInstance().openProjects.firstOrNull() ?: return null
        val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return null
        val document = editor.document
        val virtualFile = FileEditorManager.getInstance(project).selectedFiles.firstOrNull() ?: return null

        val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document) ?: return null
        val offset = editor.caretModel.offset
        val element = psiFile.findElementAt(offset) ?: return null

        // Walk up to find a named element (function, class, variable, etc.)
        var current: PsiElement? = element
        while (current != null && current !is PsiFile) {
            if (current is PsiNamedElement && current.name != null) {
                val kind = getElementKind(current)
                if (kind != "unknown") {
                    val line = document.getLineNumber(current.textOffset) + 1
                    val text = current.text.lines().take(5).joinToString("\n")  // First 5 lines
                    return SymbolInfo(
                        name = current.name ?: "",
                        kind = kind,
                        filePath = virtualFile.path,
                        line = line,
                        text = if (text.length > 500) text.take(500) + "..." else text
                    )
                }
            }
            current = current.parent
        }

        return null
    }

    private fun getElementKind(element: PsiElement): String {
        val className = element.javaClass.simpleName.lowercase()
        return when {
            className.contains("function") || className.contains("method") -> "function"
            className.contains("class") -> "class"
            className.contains("interface") -> "interface"
            className.contains("variable") || className.contains("field") -> "variable"
            className.contains("property") -> "property"
            className.contains("parameter") -> "parameter"
            className.contains("type") && className.contains("alias") -> "type"
            className.contains("enum") -> "enum"
            className.contains("module") || className.contains("namespace") -> "module"
            className.contains("import") -> "import"
            else -> "unknown"
        }
    }
}
