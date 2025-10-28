package top.maplex.arim.tools.fixedcalculator

import kotlin.math.*
import java.util.ArrayDeque as Deque


/**
 * 增强版计算器工具 - 支持数学表达式、指数、函数计算
 * @Author Saukiya & FxRayHughes
 *
 * 支持的运算符：
 * - 基础运算：+, -, *, /, %, ^(指数)
 * - 函数：sin, cos, tan, asin, acos, atan
 * - 对数：log(以10为底), ln(自然对数), log2
 * - 其他：sqrt(平方根), abs(绝对值), ceil(向上取整), floor(向下取整)
 *
 * 使用示例：
 * ```
 * FixedCalculator.calculator("2^3 + 5")  // 13.0
 * FixedCalculator.calculator("sin(30) + cos(60)")
 * FixedCalculator.calculator("log(100) + ln(2.718)")
 * FixedCalculator.calculator("sqrt(16) * 2^3")
 * FixedCalculator.calculator("abs(-5) + ceil(4.3)")
 * ```
 */
class FixedCalculator {

    fun evaluate(expression: String): Double {
        /*数字栈*/
        val number = Deque<Double>()
        /*符号栈*/
        val operator = Deque<Char>()
        /*函数栈*/
        val functions = Deque<String>()

        // 在栈顶压入一个?，配合它的优先级，目的是减少下面程序的判断
        operator.push('?')

        var num = 0.0
        var numBits = 0
        var canNegative = true
        val functionName = StringBuilder()
        var i = 0
        val chars = expression.replace(" ", "").toCharArray()

        while (i < chars.size) {
            val c = chars[i]

            // 检测函数名
            if (c.isLetter()) {
                functionName.clear()
                while (i < chars.size && chars[i].isLetter()) {
                    functionName.append(chars[i])
                    i++
                }
                functions.push(functionName.toString())
                continue
            }

            when (c) {
                '(' -> {
                    canNegative = true
                    operator.push('(')
                    if (numBits != 0) {
                        number.push(num)
                        numBits = 0
                        num = 0.0
                    }
                }

                ')' -> {
                    canNegative = false
                    num = if (numBits != 0) num else number.pop()
                    var op = '?'
                    while ((operator.pop().also { op = it }) != '(') {
                        num = binaryOperator(number.pop(), num, op)
                    }

                    // 处理函数
                    if (functions.isNotEmpty()) {
                        num = applyFunction(functions.pop(), num)
                    }

                    number.push(num)
                    numBits = 0
                    num = 0.0
                }

                '+', '*', '/', '%', '-', '^' -> {
                    if (canNegative && c == '-') {
                        // 补位负号
                        number.push(0.0)
                        operator.push(c)
                        i++
                        continue
                    }
                    canNegative = true
                    num = if (numBits != 0) num else number.pop()
                    val priority = getPriority(c)
                    while (priority <= getPriority(operator.peek())) {
                        num = binaryOperator(number.pop(), num, operator.pop())
                    }
                    operator.push(c)
                    number.push(num)
                    numBits = 0
                    num = 0.0
                }

                in '0'..'9' -> {
                    canNegative = false
                    if (numBits >= 0) {
                        num = (num * 10) + (c.code - 48)
                        numBits++
                    } else {
                        num += (c.code - 48) * 10.0.pow((numBits--).toDouble())
                    }
                }

                '.' -> numBits = -1
                else -> {}
            }
            i++
        }

        if (numBits == 0) {
            num = number.pop()
        }

        while (operator.peek() != '?') {
            num = binaryOperator(number.pop(), num, operator.pop())
        }
        return num
    }

    /**
     * 二元运算符计算
     */
    private fun binaryOperator(a1: Double, a2: Double, operator: Char): Double {
        return when (operator) {
            '+' -> a1 + a2
            '-' -> a1 - a2
            '*' -> a1 * a2
            '/' -> a1 / a2
            '%' -> a1 % a2
            '^' -> a1.pow(a2)  // 指数运算
            else -> throw IllegalStateException("illegal operator: $operator")
        }
    }

    /**
     * 应用数学函数
     */
    private fun applyFunction(funcName: String, value: Double): Double {
        return when (funcName.lowercase()) {
            // 三角函数 (参数为角度)
            "sin" -> sin(Math.toRadians(value))
            "cos" -> cos(Math.toRadians(value))
            "tan" -> tan(Math.toRadians(value))

            // 反三角函数 (返回角度)
            "asin", "arcsin" -> Math.toDegrees(asin(value))
            "acos", "arccos" -> Math.toDegrees(acos(value))
            "atan", "arctan" -> Math.toDegrees(atan(value))

            // 三角函数 (弧度版本)
            "sinr" -> sin(value)
            "cosr" -> cos(value)
            "tanr" -> tan(value)

            // 对数函数
            "log" -> log10(value)      // 常用对数
            "ln" -> ln(value)          // 自然对数
            "log2" -> log2(value)      // 以2为底

            // 其他数学函数
            "sqrt" -> sqrt(value)      // 平方根
            "cbrt" -> cbrt(value)      // 立方根
            "abs" -> abs(value)        // 绝对值
            "ceil" -> ceil(value)      // 向上取整
            "floor" -> floor(value)    // 向下取整
            "round" -> round(value)    // 四舍五入

            // 指数和幂函数
            "exp" -> exp(value)        // e^x
            "sq" -> value * value      // 平方

            else -> throw IllegalStateException("Unknown function: $funcName")
        }
    }

    /**
     * 获取运算符优先级
     */
    private fun getPriority(operator: Char): Int {
        return when (operator) {
            '?' -> 0
            '(' -> 1
            '+', '-' -> 2
            '*', '/', '%' -> 3
            '^' -> 4  // 指数运算优先级最高
            else -> throw IllegalStateException("illegal operator: $operator")
        }
    }

    companion object {

        val instance = FixedCalculator()
        /**
         * 便捷的静态方法
         */
        fun calculator(expression: String): Double {
            return instance.evaluate(expression)
        }
    }
}
