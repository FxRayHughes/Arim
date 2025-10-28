package top.maplex.arim.tools.variablecalculator.impl

import top.maplex.arim.tools.variablecalculator.Node
import kotlin.math.pow

data class PowerNode(
    val left: Node,
    val right: Node
) : Node {
    override fun evaluate(variableMap: Map<String, Double>): Double {
        return left.evaluate(variableMap).pow(right.evaluate(variableMap))
    }
}