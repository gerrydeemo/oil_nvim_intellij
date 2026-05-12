package com.github.gerrydempsey.oil

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileDocumentManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.util.TextRange
import java.io.IOException
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType

class OilSaveListener : FileDocumentManagerListener {
    private var isSyncing = false

    private fun notifyError(project: Project, content: String) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Oil Notifications")
            .createNotification(content, NotificationType.ERROR)
            .notify(project)
    }

    override fun beforeDocumentSaving(document: Document) {
        if (isSyncing) return
        
        val file = FileDocumentManager.getInstance().getFile(document) ?: return
        val directoryPath = file.getUserData(OpenOilAction.OIL_DIRECTORY_PATH) ?: return
        val markers = file.getUserData(OpenOilAction.OIL_MARKERS) ?: return
        
        val project = com.intellij.openapi.project.ProjectLocator.getInstance().guessProjectForFile(file) ?: return

        try {
            isSyncing = true
            syncChanges(project, directoryPath, document, markers)
        } finally {
            isSyncing = false
        }
    }

    private fun syncChanges(
        project: Project, 
        directoryPath: String, 
        document: Document, 
        markers: MutableMap<RangeMarker, String>
    ) {
        val directory = LocalFileSystem.getInstance().findFileByPath(directoryPath) ?: return
        
        WriteCommandAction.runWriteCommandAction(project) {
            try {
                // 1. Map current lines to markers
                val activeMarkers = mutableMapOf<Int, RangeMarker>()
                for (i in 0 until document.lineCount) {
                    val start = document.getLineStartOffset(i)
                    val end = document.getLineEndOffset(i)
                    val marker = markers.keys.find { m -> m.isValid && m.startOffset >= start && m.endOffset <= end }
                    if (marker != null) activeMarkers[i] = marker
                }

                val markersHandled = mutableSetOf<RangeMarker>()
                val finalFilesHandled = mutableSetOf<String>()

                // 2. Process Renames
                for (i in 0 until document.lineCount) {
                    val lineText = document.getLineText(i)
                    if (lineText.isEmpty()) continue
                    
                    val marker = activeMarkers[i] ?: continue
                    val originalNameWithSlash = markers[marker]!!
                    val originalName = originalNameWithSlash.removeSuffix("/")
                    
                    if (lineText != originalNameWithSlash) {
                        val isDir = lineText.endsWith("/")
                        val newName = if (isDir) lineText.removeSuffix("/") else lineText
                        
                        val childFile = directory.findChild(originalName)
                        if (childFile != null && directory.findChild(newName) == null) {
                            childFile.rename(this, newName)
                            finalFilesHandled.add(lineText)
                        } else if (childFile != null) {
                            notifyError(project, "Rename failed: $newName already exists")
                            finalFilesHandled.add(originalNameWithSlash)
                        }
                    } else {
                        finalFilesHandled.add(originalNameWithSlash)
                    }
                    markersHandled.add(marker)
                }

                // 3. Adoption Pass
                for (i in 0 until document.lineCount) {
                    if (activeMarkers.containsKey(i)) continue
                    val lineText = document.getLineText(i)
                    if (lineText.isEmpty() || finalFilesHandled.contains(lineText)) continue

                    val name = lineText.removeSuffix("/")
                    if (directory.findChild(name) != null) {
                        finalFilesHandled.add(lineText)
                        val existingMarker = markers.entries.find { it.value == lineText }?.key
                        if (existingMarker != null) markersHandled.add(existingMarker)
                    }
                }

                // 4. Delete Pass
                for ((marker, nameWithSlash) in markers) {
                    if (!markersHandled.contains(marker)) {
                        val name = nameWithSlash.removeSuffix("/")
                        val fileToDelete = directory.findChild(name)
                        if (fileToDelete != null) {
                            // VirtualFile.delete is permanent on disk but tracked in IntelliJ Local History
                            fileToDelete.delete(this)
                        }
                    }
                }

                // 5. Create Pass
                for (i in 0 until document.lineCount) {
                    val lineText = document.getLineText(i)
                    if (lineText.isEmpty() || finalFilesHandled.contains(lineText)) continue

                    val isDir = lineText.endsWith("/")
                    val name = if (isDir) lineText.removeSuffix("/") else lineText
                    
                    if (directory.findChild(name) == null) {
                        if (isDir) {
                            directory.createChildDirectory(this, name)
                        } else {
                            directory.createChildData(this, name)
                        }
                    }
                }

                // 6. Rebuild markers
                markers.keys.forEach { it.dispose() }
                markers.clear()
                for (i in 0 until document.lineCount) {
                    val lineText = document.getLineText(i)
                    if (lineText.isNotEmpty()) {
                        val start = document.getLineStartOffset(i)
                        val marker = document.createRangeMarker(start, start + lineText.length)
                        marker.isGreedyToLeft = true
                        marker.isGreedyToRight = true
                        markers[marker] = lineText
                    }
                }

            } catch (e: IOException) {
                notifyError(project, "Filesystem error: ${e.message}")
            }
        }
    }

    private fun Document.getLineText(line: Int): String {
        if (line < 0 || line >= lineCount) return ""
        val start = getLineStartOffset(line)
        val end = getLineEndOffset(line)
        return getText(TextRange(start, end)).trim()
    }
}
