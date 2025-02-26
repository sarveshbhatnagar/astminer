package astminer.parse.antlr

import astminer.common.model.NodeRange
import astminer.common.model.Position
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.Vocabulary
import org.antlr.v4.runtime.tree.ErrorNode
import org.antlr.v4.runtime.tree.TerminalNode

fun convertAntlrTree(tree: ParserRuleContext, ruleNames: Array<String>, vocabulary: Vocabulary): AntlrNode =
    compressTree(convertRuleContext(tree, ruleNames, null, vocabulary))

private fun convertRuleContext(
    ruleContext: ParserRuleContext,
    ruleNames: Array<String>,
    parent: AntlrNode?,
    vocabulary: Vocabulary
): AntlrNode {
    val typeLabel = ruleNames[ruleContext.ruleIndex]
    val currentNode = AntlrNode(typeLabel, parent, null, ruleContext.getNodeRange())
    val children: MutableList<AntlrNode> = ArrayList()

    ruleContext.children?.forEach {
        when (it) {
            is TerminalNode -> children.add(convertTerminal(it, currentNode, vocabulary))
            is ErrorNode -> children.add(convertErrorNode(it, currentNode))
            else -> children.add(convertRuleContext(it as ParserRuleContext, ruleNames, currentNode, vocabulary))
        }
    }
    currentNode.replaceChildren(children)
    return currentNode
}

private fun ParserRuleContext.getNodeRange(): NodeRange? {
    if (start == null || stop == null) return null
    return NodeRange(
        Position(start.line, start.charPositionInLine),
        Position(stop.line, stop.charPositionInLine + stop.stopIndex - stop.startIndex)
    )
}

private fun convertTerminal(terminalNode: TerminalNode, parent: AntlrNode?, vocabulary: Vocabulary): AntlrNode =
    AntlrNode(
        vocabulary.getSymbolicName(terminalNode.symbol.type),
        parent,
        terminalNode.symbol.text,
        terminalNode.getNodeRange()
    )

private fun TerminalNode.getNodeRange(): NodeRange? {
    if (symbol == null) return null
    return NodeRange(
        Position(symbol.line, symbol.charPositionInLine),
        Position(symbol.line, symbol.charPositionInLine + symbol.stopIndex - symbol.startIndex)
    )
}

private fun convertErrorNode(errorNode: ErrorNode, parent: AntlrNode?): AntlrNode =
    AntlrNode("Error", parent, errorNode.text, errorNode.getNodeRange())

/**
 * Remove intermediate nodes that have a single child.
 */
fun simplifyTree(tree: AntlrNode): AntlrNode {
    return if (tree.children.size == 1) {
        simplifyTree(tree.children.first())
    } else {
        tree.replaceChildren(tree.children.map { simplifyTree(it) }.toMutableList())
        tree
    }
}

/**
 * Compress paths of intermediate nodes that have a single child into individual nodes.
 */
fun compressTree(root: AntlrNode): AntlrNode {
    return if (root.children.size == 1) {
        val child = compressTree(root.children.first())
        val compressedNode = AntlrNode(
            root.typeLabel + "|" + child.typeLabel,
            root.parent,
            child.token.original,
            root.range
        )
        compressedNode.replaceChildren(child.children)
        compressedNode
    } else {
        root.replaceChildren(root.children.map { compressTree(it) }.toMutableList())
        root
    }
}
