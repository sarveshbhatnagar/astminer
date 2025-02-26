package astminer.storage.ast

import astminer.common.model.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.PrintWriter

private typealias Id = Int

/**
 * Formats the output in the json format by flattening the trees.
 * Each line in the output file is a single json object that corresponds to one of the labeled trees.
 * Each tree is flattened and represented as a list of nodes.
 */
class JsonAstStorage(
    override val outputDirectoryPath: String,
    private val withPaths: Boolean,
    private val withRanges: Boolean
) : Storage {
    private val treeFlattener = TreeFlattener()

    private val datasetWriters = mutableMapOf<DatasetHoldout, PrintWriter>()

    init {
        val outputDirectory = File(outputDirectoryPath)
        outputDirectory.mkdirs()
    }

    @Serializable
    private data class LabeledAst(
        val label: String,
        val path: String? = null,
        val ast: List<OutputNode>
    )

    @Serializable
    private data class OutputNode(
        val token: String,
        val typeLabel: String,
        val range: NodeRange? = null,
        val children: List<Id>
    )

    private fun TreeFlattener.EnumeratedNode.toOutputNode() =
        OutputNode(
            node.token.final(),
            node.typeLabel,
            if (withRanges) node.range else null,
            children.map { it.id }
        )

    override fun store(labeledResult: LabeledResult<out Node>, holdout: DatasetHoldout) {
        val outputNodes = treeFlattener.flatten(labeledResult.root).map { it.toOutputNode() }
        val path = if (withPaths) labeledResult.filePath else null
        val labeledAst = LabeledAst(labeledResult.label, path, outputNodes)
        val writer = datasetWriters.getOrPut(holdout) { holdout.resolveHoldout() }
        writer.println(Json.encodeToString(labeledAst))
    }

    override fun close() {
        datasetWriters.values.map { it.close() }
    }

    private fun DatasetHoldout.resolveHoldout(): PrintWriter {
        val holdoutDir = File(outputDirectoryPath).resolve(this.dirName)
        holdoutDir.mkdirs()
        val astFile = holdoutDir.resolve("asts.jsonl")
        astFile.createNewFile()
        return PrintWriter(astFile)
    }
}

/**
 * Gives ids to all nodes in the tree and flattens the tree
 */
class TreeFlattener {
    private var currentId: Id = 0

    /**
     * Node that has been given an Id.
     * Also all his children have been given ids.
     */
    data class EnumeratedNode(val id: Id, val node: Node, val children: List<EnumeratedNode>)

    private fun enumerateTree(node: Node): EnumeratedNode {
        val nodeId = currentId
        currentId += 1
        return EnumeratedNode(nodeId, node, node.children.map { enumerateTree(it) })
    }

    private fun putFlattenedTree(enumeratedNode: EnumeratedNode, flattenedTree: MutableList<EnumeratedNode>) {
        flattenedTree.add(enumeratedNode)
        for (child in enumeratedNode.children) {
            putFlattenedTree(child, flattenedTree)
        }
    }

    /**
     * Enumerates the given tree and returns the flattened tree.
     * Enumerated node's id must be equal to its index in the returned list
     */
    fun flatten(node: Node): List<EnumeratedNode> {
        currentId = 0
        val enumeratedTree = enumerateTree(node)
        val result = mutableListOf<EnumeratedNode>()
        putFlattenedTree(enumeratedTree, result)
        return result
    }
}
