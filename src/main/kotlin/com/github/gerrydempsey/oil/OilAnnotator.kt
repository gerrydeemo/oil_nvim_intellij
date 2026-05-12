package com.github.gerrydempsey.oil

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.DumbAware
import com.intellij.psi.PsiFile
import javax.swing.Icon

class OilAnnotator : Annotator, DumbAware {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        // Run only once per file
        if (element !is OilFile) return

        val text = element.text
        val lines = text.split("\n")
        var currentOffset = 0

        val virtualFile = element.virtualFile ?: return
        val dirPath = virtualFile.getUserData(OpenOilAction.OIL_DIRECTORY_PATH) ?: return
        val directory = LocalFileSystem.getInstance().findFileByPath(dirPath) ?: return

        for (line in lines) {
            if (line.isEmpty()) {
                currentOffset += 1
                continue
            }
            
            val isDir = line.endsWith("/")
            val name = if (isDir) line.removeSuffix("/") else line
            val childFile = directory.findChild(name)
            
            val range = TextRange(currentOffset, currentOffset + line.length)
            val annotationBuilder = holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                .range(range)

            // 1. Text Styling
            if (isDir) {
                annotationBuilder.textAttributes(DefaultLanguageHighlighterColors.KEYWORD)
            } else if (name.startsWith(".")) {
                annotationBuilder.textAttributes(DefaultLanguageHighlighterColors.LINE_COMMENT)
            } else {
                val ext = name.substringAfterLast(".", "")
                when (ext.lowercase()) {
                    "kt", "java", "py", "js", "ts", "rb", "go", "rs" -> 
                        annotationBuilder.textAttributes(DefaultLanguageHighlighterColors.IDENTIFIER)
                    "md", "txt", "html", "css" -> 
                        annotationBuilder.textAttributes(DefaultLanguageHighlighterColors.STRING)
                    "json", "yaml", "yml", "xml", "toml", "gradle", "kts" -> 
                        annotationBuilder.textAttributes(DefaultLanguageHighlighterColors.METADATA)
                }
            }

            // 2. Gutter Icon
            val icon = if (childFile != null) {
                childFile.fileType.icon
            } else {
                if (isDir) AllIcons.Nodes.Folder else FileTypeManager.getInstance().getFileTypeByFileName(name).icon
            }
            
            annotationBuilder.gutterIconRenderer(OilGutterIconRenderer(icon))
            annotationBuilder.create()

            currentOffset += line.length + 1
        }
    }

    private class OilGutterIconRenderer(private val icon: Icon) : GutterIconRenderer() {
        override fun getIcon(): Icon = icon
        override fun equals(other: Any?): Boolean = other is OilGutterIconRenderer && other.icon == icon
        override fun hashCode(): Int = icon.hashCode()
    }
}
