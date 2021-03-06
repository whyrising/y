package com.github.whyrising.y

import com.github.whyrising.y.concretions.list.ChunkedSeq
import com.github.whyrising.y.concretions.list.Cons
import com.github.whyrising.y.concretions.list.PersistentList.Empty
import com.github.whyrising.y.concretions.vector.v
import com.github.whyrising.y.core.IHashEq
import com.github.whyrising.y.mocks.HashEqMock
import com.github.whyrising.y.seq.ISeq
import com.github.whyrising.y.seq.LazySeq
import com.github.whyrising.y.util.Category
import com.github.whyrising.y.util.DoubleOps
import com.github.whyrising.y.util.LongOps
import com.github.whyrising.y.util.category
import com.github.whyrising.y.util.hasheq
import com.github.whyrising.y.util.lazyChunkedSeq
import com.github.whyrising.y.util.ops
import com.github.whyrising.y.util.toSeq
import io.kotest.assertions.throwables.shouldThrowExactly
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs

@ExperimentalStdlibApi
class UtilTest : FreeSpec({

    class UnsupportedNumber : Number() {
        override fun toByte(): Byte = 0

        override fun toChar(): Char = 'a'

        override fun toDouble(): Double = 0.0

        override fun toFloat(): Float = 0.0f

        override fun toInt(): Int = 0

        override fun toLong(): Long = 0

        override fun toShort(): Short = 0

        override fun toString(): String = "UnsupportedNumber"
    }

    "category(n:Number)" {
        val a: Byte = 1
        val b: Short = 1
        val c = 1
        val d: Long = 1
        val e = 1f
        val f = 1.0
        val unsupportedNum = UnsupportedNumber()

        category(a) shouldBe Category.INTEGER
        category(b) shouldBe Category.INTEGER
        category(c) shouldBe Category.INTEGER
        category(d) shouldBe Category.INTEGER
        category(e) shouldBe Category.FLOATING
        category(f) shouldBe Category.FLOATING

        val exception = shouldThrowExactly<IllegalStateException> {
            category(unsupportedNum)
        }
        exception.message shouldBe
            "The category of the number: $unsupportedNum is not supported"
    }

    "ops(n:Number)" {
        val a: Byte = 1
        val b: Short = 1
        val c = 1
        val d: Long = 1
        val e = 1f
        val f = 1.0
        val unsupportedNum = UnsupportedNumber()

        ops(a) shouldBe LongOps
        ops(b) shouldBe LongOps
        ops(c) shouldBe LongOps
        ops(d) shouldBe LongOps
        ops(e) shouldBe DoubleOps
        ops(f) shouldBe DoubleOps

        shouldThrowExactly<IllegalStateException> {
            ops(unsupportedNum)
        }.message shouldBe
            "The Ops of the number: $unsupportedNum is not supported"
    }

    "LongOps, DoubleOps" - {
        "combine(y: Ops)" {
            LongOps.combine(LongOps) shouldBe LongOps
            LongOps.combine(DoubleOps) shouldBe DoubleOps

            DoubleOps.combine(DoubleOps) shouldBe DoubleOps
            DoubleOps.combine(LongOps) shouldBe DoubleOps
        }

        "equiv(x: Number, y: Number)" {
            LongOps.equiv(1, 1L).shouldBeTrue()
            DoubleOps.equiv(1f, 1.0).shouldBeTrue()
        }
    }

    "lazyChunkedSeq(iterator)" - {
        "when iterator is empty, it should return Empty" {
            val iterator = emptyList<Int>().iterator()

            lazyChunkedSeq(iterator) shouldBeSameInstanceAs Empty
        }

        @Suppress("UNCHECKED_CAST")
        "when iterator is not empty, it should return a LazySeq of ChunkedSeq" {
            val chunkSize = 32
            val l = (0 until 100).toList()
            val iterator = l.iterator()

            val ls = lazyChunkedSeq(iterator) as LazySeq<Int>
            val chunkedSeq = ls.f!!() as ChunkedSeq<Int>
            val restChunks = chunkedSeq.restChunks() as LazySeq<Int>
            val chunkedSeq2 = restChunks.f!!() as ChunkedSeq<Int>

            chunkedSeq.count shouldBeExactly 68
            chunkedSeq.firstChunk().count shouldBeExactly chunkSize
            chunkedSeq.first() shouldBeExactly 0

            chunkedSeq2.count shouldBeExactly 33
            chunkedSeq2.firstChunk().count shouldBeExactly chunkSize
            chunkedSeq2.first() shouldBeExactly 32
        }
    }

    "toSeq(x)" - {
        "when x is null, it should return null" {
            toSeq<Int>(null).shouldBeNull()
        }

        "ASeq" {
            val x: Any = Cons(1, Empty)

            val seq = toSeq<Int>(x) as ISeq<Int>

            seq.first() shouldBeExactly 1
        }

        "Seqable" {
            val x: Any = v(1, 2)

            val seq = toSeq<Int>(x) as ISeq<Int>

            seq.first() shouldBeExactly 1
        }

        "Iterable<*>" {
            val x = listOf(1, 2, 3, 5)

            val seq = toSeq<Int>(x) as ISeq<Int>

            seq.first() shouldBeExactly x[0]
            seq.count shouldBeExactly x.size
        }

        "not supported" {
            val x: Any = true

            val e = shouldThrowExactly<IllegalArgumentException> {
                toSeq<Int>(x)
            }

            e.message shouldBe
                "Don't know how to create ISeq from: ${x::class.simpleName}"
        }
    }

    "hasheq(object)" {
        hasheq(null) shouldBeExactly 0

        hasheq("abcd") shouldBeExactly 946207298

        // Numbers
        // Integers
        hasheq(126.toByte()) shouldBeExactly -771541190
        hasheq(30000.toShort()) shouldBeExactly 1314523797
        hasheq(3123456985) shouldBeExactly 775213884
        // Decimals
        hasheq(-0.0) shouldBeExactly 0
        hasheq(12.6) shouldBeExactly 1931083776
        hasheq(-0.0f) shouldBeExactly 0
        hasheq(12.6f) shouldBeExactly 1095342490

        // IHashEq
        val hasheq: IHashEq = HashEqMock()
        hasheq(hasheq) shouldBeExactly 111111111

        hasheq(true) shouldBeExactly true.hashCode()
    }
})
