package com.github.whyrising.y

import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.doubles.shouldBeExactly
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import kotlin.test.Test

class CoreTest {
    @Test
    fun `identity(x) should return x`() {
        identity(10) shouldBeExactly 10
        identity(10.1) shouldBeExactly 10.1
        identity("a") shouldBe "a"
        identity(true).shouldBeTrue()
        val f = {}
        identity(f) shouldBeSameInstanceAs f
    }

    @Test
    fun `str(varargs) should return the string value of the arg`() {
        str() shouldBe ""
        str(null) shouldBe ""
        str(1) shouldBe "1"
        str(1, 2) shouldBe "12"
        str(1, 2, 3) shouldBe "123"
        str(1, null, 3) shouldBe "13"
        str(1, 2, null) shouldBe "12"
        str(null, 2, 3) shouldBe "23"
        str(1, 2, 3, 4) shouldBe "1234"
    }

    @Test
    fun `curry(f)`() {
        val arg1 = 1
        val arg2 = 1.0
        val arg3 = 1.0F
        val arg4 = ""
        val arg5 = true
        val arg6 = 1L
        val f1 = { _: Int, _: Double -> 1 }
        val f2 = { _: Int, _: Double, _: Float -> 1 }
        val f3 = { _: Int, _: Double, _: Float, _: String -> 1 }
        val f4 = { _: Int, _: Double, _: Float, _: String, _: Boolean -> 1 }
        val f5 =
            { _: Int, _: Double, _: Float, _: String, _: Boolean, _: Long -> 1 }

        val curried1 = curry(f1)
        val curried2 = curry(f2)
        val curried3 = curry(f3)
        val curried4 = curry(f4)
        val curried5 = curry(f5)

        curried1(arg1)(arg2) shouldBeExactly f1(arg1, arg2)

        curried2(arg1)(arg2)(arg3) shouldBeExactly f2(arg1, arg2, arg3)

        curried3(arg1)(arg2)(arg3)(arg4) shouldBeExactly
            f3(arg1, arg2, arg3, arg4)

        curried4(arg1)(arg2)(arg3)(arg4)(arg5) shouldBeExactly
            f4(arg1, arg2, arg3, arg4, arg5)

        curried5(arg1)(arg2)(arg3)(arg4)(arg5)(arg6) shouldBeExactly
            f5(arg1, arg2, arg3, arg4, arg5, arg6)
    }

    @Test
    fun `complement(f) should return a function`() {
        val f1 = { true }
        val f2 = { _: Int -> true }
        val f3 = { _: Int -> { _: Long -> true } }
        val f4 = { _: Int -> { _: Long -> { _: String -> true } } }
        val f5 = { _: Int ->
            { _: Long ->
                { _: String ->
                    { _: Float ->
                        true
                    }
                }
            }
        }

        val complementF1 = complement(f1)
        val complementF2 = complement(f2)
        val complementF3 = complement(f3)
        val complementF4 = complement(f4)
        val complementF5 = complement(f5)

        complementF1() shouldBe false
        complementF2(0) shouldBe false
        complementF3(0)(0L) shouldBe false
        complementF4(0)(0L)("") shouldBe false
        complementF5(0)(0L)("")(1.2F) shouldBe false
    }

    @Test
    fun `compose(f)`() {
        val f1: (Int) -> Int = ::identity

        compose<Int>() shouldBe ::identity
        compose(f1) shouldBe f1
    }

    @Test
    fun `when g has no args, compose returns the composition with no args `() {
        val f: (Int) -> String = { i: Int -> str(i) }
        val g: () -> Int = { 7 }

        val fog: () -> String = compose(f, g)

        fog() shouldBe f(g())
    }

    @Test
    fun `when g has 1 arg, compose should return the composition with 1 arg`() {
        val f: (Int) -> String = { i: Int -> str(i) }
        val g: (Float) -> Int = { 7 }

        val fog: (Float) -> String = compose(f, g)

        fog(1.2f) shouldBe f(g(1.2f))
    }

    @Test
    fun `when g has 2 args, compose returns the composition with 2 args`() {
        val x = 1.2f
        val y = 1.8
        val f: (Int) -> String = { i: Int -> str(i) }
        val g: (Float) -> (Double) -> Int = { { 7 } }

        val fog: (Float) -> (Double) -> String = compose(f, g)

        fog(x)(y) shouldBe f(g(x)(y))
    }

    @Test
    fun `when g has 3 args, should return the composition with 3 args`() {
        val x = 1.2f
        val y = 1.8
        val z = true
        val f: (Int) -> String = { i: Int -> str(i) }
        val g: (Float) -> (Double) -> (Boolean) -> Int =
            { { { 7 } } }

        val fog: (Float) -> (Double) -> (Boolean) -> String =
            compose(f, g)

        fog(x)(y)(z) shouldBe f(g(x)(y)(z))
    }
}
