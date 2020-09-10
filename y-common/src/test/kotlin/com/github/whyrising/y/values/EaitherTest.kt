package com.github.whyrising.y.values

import com.github.whyrising.y.values.Either.Left
import com.github.whyrising.y.values.Either.Right
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.reflection.shouldBeSubtypeOf
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import io.kotest.property.checkAll

class EitherTest : FreeSpec({
    "Left" - {
        "should be a subtype of Either" {
            Left::class.shouldBeSubtypeOf<Either<*, *>>()
        }

        "should be covariant" {
            val left: Left<Number, Number> = Left<Int, Double>(1)

            left.value shouldBe 1
        }

        "toString() should return `Left(value)`" {
            checkAll { i: Int ->
                Left<Int, Double>(i).toString() shouldBe "Left($i)"
            }
        }
    }

    "Right" - {
        "should be a subtype of Either" {
            Right::class.shouldBeSubtypeOf<Either<*, *>>()
        }

        "type should be covariant" {
            val right: Right<Number, Number> = Right<Int, Double>(1.0)

            right.value shouldBe 1
        }

        "toString() should return `Left(value)`" {
            checkAll { i: Double ->
                Right<Int, Double>(i).toString() shouldBe "Right($i)"
            }
        }
    }

    "left() function should return Left type as Either type" {
        Either.left<Int, Double>(1).shouldBeTypeOf<Left<Int, Double>>()
    }

    "right() function should return Right type as Either type" {
        Either.right<Int, Double>(1.0).shouldBeTypeOf<Right<Int, Double>>()
    }
})
