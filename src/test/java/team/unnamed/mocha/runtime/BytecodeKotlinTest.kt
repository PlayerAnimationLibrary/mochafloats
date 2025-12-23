package team.unnamed.mocha.runtime

import org.junit.jupiter.api.Test
import team.unnamed.mocha.MochaEngine
import team.unnamed.mocha.runtime.binding.Binding

@Binding("plain")
class NoArgument(var offset: Int) {
    @Binding("calc")
    fun calc(): Double = offset.toDouble()
}

@Binding("offset")
class WithOffset(var offset: Int) {
    @Binding("calc")
    fun calc(value: Double): Double = value + offset.toDouble()
}

class BytecodeTest {
    @Test
    fun main() {
        val engine = MochaEngine.createStandard()

        val plain = NoArgument(0)
        engine.bindInstance(NoArgument::class.java, plain, "plain")

        val plainExpr = "plain.calc()"

        val plainInterpreted = engine.prepareEval(plainExpr)
        plain.offset = 3
        println(plainInterpreted.evaluate())
        plain.offset = 4
        println(plainInterpreted.evaluate())

        val plainCompiled = engine.compile(plainExpr)
        plain.offset = 3
        println(plainCompiled.evaluate())
        plain.offset = 4
        println(plainCompiled.evaluate())

        val offset = WithOffset(0)
        engine.bindInstance(WithOffset::class.java, offset, "offset")

        val offsetExpr = "offset.calc(1)"

        val offsetInterpreted = engine.prepareEval(offsetExpr)
        offset.offset = 3
        println(offsetInterpreted.evaluate())
        offset.offset = 4
        println(offsetInterpreted.evaluate())

        val offsetCompiled = engine.compile(offsetExpr)
        offset.offset = 3
        println(offsetCompiled.evaluate())
        offset.offset = 4
        println(offsetCompiled.evaluate())
    }
}
