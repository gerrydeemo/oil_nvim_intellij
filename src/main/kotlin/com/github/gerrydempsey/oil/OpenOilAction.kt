package com.github.gerrydempsey.oil

import com.intellij.ide.scratch.ScratchRootType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.util.Key
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.editor.Document

class OpenOilAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val currentFile = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: project.projectFile ?: return
        
        // Logical directory path if we are in an oil buffer
        val oilDirPath = currentFile.getUserData(OIL_DIRECTORY_PATH)
        
        val directory = if (oilDirPath != null) {
            // If in an oil buffer, go to the parent of the logical directory
            val dir = LocalFileSystem.getInstance().findFileByPath(oilDirPath) ?: return
            dir.parent ?: return
        } else if (currentFile.isDirectory) {
            currentFile
        } else {
            currentFile.parent ?: return
        }

        openOilBuffer(project, directory)
    }

    companion object {
        val OIL_DIRECTORY_PATH = Key.create<String>("OIL_DIRECTORY_PATH")
        val OIL_MARKERS = Key.create<MutableMap<RangeMarker, String>>("OIL_MARKERS")

        fun openOilBuffer(project: Project, directory: VirtualFile) {
            val fileEditorManager = FileEditorManager.getInstance(project)
            
            // Try to find an existing Oil buffer to reuse
            val oilFile = fileEditorManager.openFiles.find { it.getUserData(OIL_DIRECTORY_PATH) != null }

            val children = directory.children.sortedBy { it.name }
            val content = children.joinToString("\n") { it.name + (if (it.isDirectory) "/" else "") } + "\n"

            if (oilFile != null) {
                val document = FileDocumentManager.getInstance().getDocument(oilFile)
                if (document != null) {
                    WriteAction.run<Exception> {
                        // Update content and metadata
                        document.setText(content)
                        oilFile.putUserData(OIL_DIRECTORY_PATH, directory.path)
                        
                        // Try to rename the scratch file so tab title reflects current dir
                        val newName = "oil-${directory.name}.oil"
                        if (oilFile.name != newName) {
                            try {
                                oilFile.rename(this, newName)
                            } catch (e: Exception) {}
                        }
                    }
                    
                    val markers = oilFile.getUserData(OIL_MARKERS) ?: mutableMapOf<RangeMarker, String>().also {
                        oilFile.putUserData(OIL_MARKERS, it)
                    }
                    updateMarkers(document, markers, children)
                    
                    fileEditorManager.openFile(oilFile, true)
                    return
                }
            }

            // No existing buffer, create new
            val scratchFile = WriteAction.compute<VirtualFile, Exception> {
                ScratchRootType.getInstance().createScratchFile(
                    project, 
                    "oil-${directory.name}.oil", 
                    OilLanguage, 
                    content
                )
            } ?: return

            scratchFile.putUserData(OIL_DIRECTORY_PATH, directory.path)
            
            val document = FileDocumentManager.getInstance().getDocument(scratchFile) ?: return
            val markers = mutableMapOf<RangeMarker, String>()
            updateMarkers(document, markers, children)
            scratchFile.putUserData(OIL_MARKERS, markers)
            
            fileEditorManager.openFile(scratchFile, true)
        }

        private fun updateMarkers(document: Document, markers: MutableMap<RangeMarker, String>, children: List<VirtualFile>) {
            // Dispose old markers
            markers.keys.forEach { it.dispose() }
            markers.clear()
            
            var currentOffset = 0
            for (child in children) {
                val nameWithSlash = child.name + (if (child.isDirectory) "/" else "")
                if (currentOffset + nameWithSlash.length <= document.textLength) {
                    val marker = document.createRangeMarker(currentOffset, currentOffset + nameWithSlash.length)
                    marker.isGreedyToLeft = true
                    marker.isGreedyToRight = true
                    markers[marker] = nameWithSlash
                }
                currentOffset += nameWithSlash.length + 1
            }
        }
    }
}
