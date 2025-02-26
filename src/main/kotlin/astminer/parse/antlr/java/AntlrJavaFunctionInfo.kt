package astminer.parse.antlr.java

import astminer.common.model.*
import astminer.parse.antlr.*
import astminer.parse.findEnclosingElementBy
import mu.KotlinLogging

private val logger = KotlinLogging.logger("Antlr-Java-function-info")

class AntlrJavaFunctionInfo(override val root: AntlrNode, override val filePath: String) : FunctionInfo<AntlrNode> {
    override val nameNode: AntlrNode? = collectNameNode()
    override val returnType: String? = collectReturnType()
    override val enclosingElement: EnclosingElement<AntlrNode>? = collectEnclosingClass()

    override val isConstructor: Boolean = false

    override val parameters: List<FunctionInfoParameter>? =
        try { collectParameters() } catch (e: IllegalStateException) {
            logger.warn { e.message }
            null
        }

    override val modifiers: List<String>? =
        root.parent?.children
            ?.filter { it.hasFirstLabel(METHOD_MODIFIER) && !it.hasLastLabel(METHOD_ANNOTATION) }
            ?.mapNotNull { it.token.original }

    override val annotations: List<String>? =
        root.parent?.children
            ?.filter { it.hasLastLabel(METHOD_ANNOTATION) }
            ?.mapNotNull { it.getChildOfType(ANNOTATION_NAME)?.token?.original }

    override val body: AntlrNode? = root.children.find { it.hasFirstLabel(METHOD_BODY_NODE) }

    override fun isBlank() = body == null || body.children.size <= 2

    private fun collectNameNode(): AntlrNode? = root.getChildOfType(METHOD_NAME_NODE)

    private fun collectReturnType(): String? {
        val returnTypeNode = root.getChildOfType(METHOD_RETURN_TYPE_NODE)
        return returnTypeNode?.getTokensFromSubtree()
    }

    private fun collectEnclosingClass(): EnclosingElement<AntlrNode>? = extractWithLogger(logger) {
        val enclosingClassNode = root
            .findEnclosingElementBy { it.lastLabelIn(possibleEnclosingElements) } ?: return@extractWithLogger null
        val enclosingType = when {
            enclosingClassNode.hasLastLabel(CLASS_DECLARATION_NODE) -> EnclosingElementType.Class
            enclosingClassNode.hasLastLabel(ENUM_DECLARATION_NODE) -> EnclosingElementType.Enum
            else -> error("No enclosing element type found")
        }
        EnclosingElement(
            type = enclosingType,
            name = enclosingClassNode.getChildOfType(ENCLOSING_NAME_NODE)?.token?.original,
            root = enclosingClassNode
        )
    }

    private fun collectParameters(): List<FunctionInfoParameter> {
        val parametersRoot = root.getChildOfType(METHOD_PARAMETER_NODE)
        val innerParametersRoot = parametersRoot?.getChildOfType(METHOD_PARAMETER_INNER_NODE) ?: return emptyList()

        if (innerParametersRoot.lastLabelIn(METHOD_SINGLE_PARAMETER_NODES)) {
            return listOf(getParameterInfo(innerParametersRoot))
        }

        return innerParametersRoot.children.filter {
            it.firstLabelIn(METHOD_SINGLE_PARAMETER_NODES)
        }.map { singleParameter -> getParameterInfo(singleParameter) }
    }

    private fun getParameterInfo(parameterNode: AntlrNode): FunctionInfoParameter {
        val returnTypeNode = parameterNode.getChildOfType(PARAMETER_RETURN_TYPE_NODE)
        val returnTypeToken = returnTypeNode?.getTokensFromSubtree()

        val parameterName = parameterNode.getChildOfType(PARAMETER_NAME_NODE)?.getTokensFromSubtree()
            ?: error("Parameter name wasn't found")

        return FunctionInfoParameter(parameterName, returnTypeToken)
    }

    companion object {
        private const val METHOD_RETURN_TYPE_NODE = "typeTypeOrVoid"
        private const val METHOD_NAME_NODE = "IDENTIFIER"
        private const val METHOD_MODIFIER = "modifier"
        private const val METHOD_ANNOTATION = "annotation"
        private const val ANNOTATION_NAME = "qualifiedName"
        private const val METHOD_BODY_NODE = "methodBody"

        private const val CLASS_DECLARATION_NODE = "classDeclaration"
        private const val ENUM_DECLARATION_NODE = "enumDeclaration"
        val possibleEnclosingElements = listOf(
            CLASS_DECLARATION_NODE,
            ENUM_DECLARATION_NODE
        )
        private const val ENCLOSING_NAME_NODE = "IDENTIFIER"

        private const val METHOD_PARAMETER_NODE = "formalParameters"
        private const val METHOD_PARAMETER_INNER_NODE = "formalParameterList"
        private val METHOD_SINGLE_PARAMETER_NODES = listOf("formalParameter", "lastFormalParameter")
        private const val PARAMETER_RETURN_TYPE_NODE = "typeType"
        private const val PARAMETER_NAME_NODE = "variableDeclaratorId"
    }
}
