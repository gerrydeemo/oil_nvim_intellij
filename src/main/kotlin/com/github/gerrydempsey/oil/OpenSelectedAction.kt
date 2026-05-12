package com.github.gerrydempsey.oil

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.util.TextRange

class OpenSelectedAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        
        val directoryPath = file.getUserData(OpenOilAction.OIL_DIRECTORY_PATH) ?: return
        
        val caretModel = editor.caretModel
        val line = caretModel.logicalPosition.line
        val start = editor.document.getLineStartOffset(line)
        val end = editor.document.getLineEndOffset(line)
        val lineText = editor.document.getText(TextRange(start, end)).trim()
        
        if (lineText.isEmpty()) return
        
        val isDir = lineText.endsWith("/")
        val name = if (isDir) lineText.removeSuffix("/") else lineText
        
        val directory = LocalFileSystem.getInstance().findFileByPath(directoryPath) ?: return
        val targetFile = directory.findChild(name) ?: return
        
        if (targetFile.isDirectory) {
            OpenOilAction.openOilBuffer(project, targetFile)
        } else {
            FileEditorManager.getInstance(project).openFile(targetFile, true)
        }
    }

    override fun update(e: AnActionEvent) {
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        e.presentation.isEnabledAndVisible = file?.getUserData(OpenOilAction.OIL_DIRECTORY_PATH) != null
    }
}
