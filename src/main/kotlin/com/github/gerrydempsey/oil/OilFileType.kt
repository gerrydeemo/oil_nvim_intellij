package com.github.gerrydempsey.oil

import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.ui.IconManager
import com.intellij.icons.AllIcons
import javax.swing.Icon

object OilFileType : LanguageFileType(OilLanguage) {
    override fun getName(): String = "Oil"
    override fun getDescription(): String = "Oil directory buffer"
    override fun getDefaultExtension(): String = "oil"
    override fun getIcon(): Icon = AllIcons.Nodes.Folder
}
