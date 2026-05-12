package com.github.gerrydempsey.oil

import com.intellij.lang.ASTNode
import com.intellij.lang.ParserDefinition
import com.intellij.lang.PsiBuilder
import com.intellij.lang.PsiParser
import com.intellij.lexer.Lexer
import com.intellij.lexer.LexerBase
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet
import com.intellij.extapi.psi.ASTWrapperPsiElement

class OilParserDefinition : ParserDefinition {
    companion object {
        val FILE = IFileElementType(OilLanguage)
        val LINE = IElementType("OIL_LINE", OilLanguage)
    }

    override fun createLexer(project: Project?): Lexer = OilLexer()
    override fun createParser(project: Project?): PsiParser = OilParser()
    override fun getFileNodeType(): IFileElementType = FILE
    override fun getCommentTokens(): TokenSet = TokenSet.EMPTY
    override fun getStringLiteralElements(): TokenSet = TokenSet.EMPTY
    override fun createElement(node: ASTNode): PsiElement = ASTWrapperPsiElement(node)
    override fun createFile(viewProvider: FileViewProvider): PsiFile = OilFile(viewProvider)
}

class OilLexer : LexerBase() {
    private var buffer: CharSequence? = null
    private var startOffset = 0
    private var endOffset = 0
    private var tokenStart = 0
    private var tokenEnd = 0

    override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
        this.buffer = buffer
        this.startOffset = startOffset
        this.endOffset = endOffset
        this.tokenStart = startOffset
        this.tokenEnd = findTokenEnd(startOffset)
    }

    private fun findTokenEnd(start: Int): Int {
        val b = buffer ?: return start
        var i = start
        while (i < endOffset && b[i] != '\n') {
            i++
        }
        if (i < endOffset && b[i] == '\n') {
            i++
        }
        return i
    }

    override fun getState(): Int = 0
    override fun getTokenType(): IElementType? = if (tokenStart < endOffset) OilParserDefinition.LINE else null
    override fun getTokenStart(): Int = tokenStart
    override fun getTokenEnd(): Int = tokenEnd

    override fun advance() {
        tokenStart = tokenEnd
        tokenEnd = findTokenEnd(tokenStart)
    }

    override fun getBufferSequence(): CharSequence = buffer ?: ""
    override fun getBufferEnd(): Int = endOffset
}

class OilParser : PsiParser {
    override fun parse(root: IElementType, builder: PsiBuilder): ASTNode {
        val rootMarker = builder.mark()
        while (!builder.eof()) {
            val lineMarker = builder.mark()
            builder.advanceLexer()
            lineMarker.done(OilParserDefinition.LINE)
        }
        rootMarker.done(root)
        return builder.treeBuilt
    }
}
