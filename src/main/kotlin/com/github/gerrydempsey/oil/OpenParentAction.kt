package com.github.gerrydempsey.oil

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.vfs.LocalFileSystem

class OpenParentAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        
        val directoryPath = file.getUserData(OpenOilAction.OIL_DIRECTORY_PATH)
        
        val targetDirectory = if (directoryPath != null) {
            // We are in an Oil buffer, go to the parent of the logical directory
            val currentDir = LocalFileSystem.getInstance().findFileByPath(directoryPath) ?: return
            currentDir.parent ?: return
        } else {
            // We are in a regular file/dir, go to the parent of THIS file/dir
            file.parent ?: return
        }
        
        OpenOilAction.openOilBuffer(project, targetDirectory)
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = true
    }
}
