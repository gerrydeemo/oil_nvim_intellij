package com.github.gerrydempsey.oil

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.openapi.fileTypes.FileType
import com.intellij.psi.FileViewProvider

class OilFile(viewProvider: FileViewProvider) : PsiFileBase(viewProvider, OilLanguage) {
    override fun getFileType(): FileType = OilFileType
    override fun toString(): String = "Oil File"
}
