package com.github.whyrising.y

import com.github.whyrising.y.APersistentVector.Seq
import com.github.whyrising.y.PersistentList.Empty
import com.github.whyrising.y.PersistentVector.EmptyVector
import com.github.whyrising.y.PersistentVector.Node
import com.github.whyrising.y.PersistentVector.Node.EmptyNode
import com.github.whyrising.y.PersistentVector.TransientVector
import com.github.whyrising.y.mocks.MockSeq
import com.github.whyrising.y.mocks.User
import io.kotest.assertions.throwables.shouldNotThrow
import io.kotest.assertions.throwables.shouldThrowExactly
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.matchers.types.shouldNotBeSameInstanceAs
import io.kotest.property.Arb
import io.kotest.property.arbitrary.filter
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.merge
import io.kotest.property.checkAll
import kotlinx.atomicfu.atomic

const val SHIFT = 5

class PersistentVectorTest : FreeSpec({
    "Node" - {
        @Suppress("UNCHECKED_CAST")
        "Node does have an array of nodes" {
            val array = arrayOfNulls<Int>(33)

            val node = Node<Int>(atomic(false), array as Array<Any?>)

            node.array shouldBeSameInstanceAs array
        }

        "Empty node" {
            val emptyNode = EmptyNode
            val nodes = emptyNode.array

            nodes.size shouldBeExactly 32
            nodes[0].shouldBeNull()
            nodes[31].shouldBeNull()
        }
    }

    "PersistentVector" - {
        "invoke() should return the EmptyVector" {
            PersistentVector<Int>() shouldBe EmptyVector
        }

        "invoke(args)" - {
            "when args count <= 32, it should add to the tail" {
                checkAll(Arb.list(Arb.int(), 0..32)) { list: List<Int> ->
                    val vec = PersistentVector(*list.toTypedArray())
                    val tail = vec.tail

                    if (vec.count == 0)
                        vec shouldBeSameInstanceAs EmptyVector
                    vec.shift shouldBeExactly SHIFT
                    vec.count shouldBeExactly list.size
                    vec.root shouldBeSameInstanceAs EmptyNode
                    tail.size shouldBeExactly list.size
                    tail shouldContainAll list
                }
            }

            "when args count > 32, it should call conj" {
                val list = (1..100).toList()

                val vec = PersistentVector(*list.toTypedArray())
                val tail = vec.tail
                val root = vec.root

                vec.shift shouldBeExactly SHIFT
                vec.count shouldBeExactly list.size
                tail.size shouldBeExactly 4
                root.array[0].shouldNotBeNull()
                root.array[1].shouldNotBeNull()
                root.array[2].shouldNotBeNull()
                root.array[3].shouldBeNull()
                tail[0] shouldBe 97
                tail[1] shouldBe 98
                tail[2] shouldBe 99
                tail[3] shouldBe 100
            }
        }

        "conj(e)" - {
            val i = 7
            "when level is 5 and there is room in tail, it should add to it" {
                val ints = Arb.int().filter { it != i }
                checkAll(Arb.list(ints, 0..31)) { list ->
                    val tempVec = PersistentVector(*list.toTypedArray())

                    val vec = tempVec.conj(i)
                    val tail = vec.tail
                    val root = vec.root

                    vec.shift shouldBeExactly SHIFT
                    vec.count shouldBeExactly list.size + 1
                    root shouldBeSameInstanceAs EmptyNode
                    root.isMutable.value.shouldBeFalse()
                    root.isMutable shouldBeSameInstanceAs tempVec.root.isMutable
                    tail.size shouldBeExactly list.size + 1
                    tail[list.size] as Int shouldBeExactly i
                }
            }

            @Suppress("UNCHECKED_CAST")
            "when the tail is full, it should push tail into the vec" - {
                "when the level is 5, it should insert the tail in root node" {
                    val listGen = Arb.list(Arb.int(), (32..1024)).filter {
                        it.size % 32 == 0
                    }

                    checkAll(listGen, Arb.int()) { l: List<Int>, i: Int ->
                        val tempVec = PersistentVector(*l.toTypedArray())
                        val tempTail = tempVec.tail

                        val vec = tempVec.conj(i)
                        val tail = vec.tail


                        vec.shift shouldBeExactly SHIFT
                        var index = 31
                        var isMostRightLeafFound = false
                        while (index >= 0 && !isMostRightLeafFound) {
                            val o = vec.root.array[index]
                            if (o != null) {
                                val mostRightLeaf = o as Node<Int>
                                val array = mostRightLeaf.array
                                array shouldBeSameInstanceAs tempTail
                                mostRightLeaf.isMutable shouldBeSameInstanceAs
                                    tempVec.root.isMutable
                                isMostRightLeafFound = true
                            }
                            index--
                        }

                        vec.root.isMutable.value.shouldBeFalse()
                        isMostRightLeafFound.shouldBeTrue()
                        vec.count shouldBeExactly l.size + 1
                        tail.size shouldBeExactly 1
                        tail[0] shouldBe i
                    }
                }

                """when the level is > 5, it should iterate through the
                                            levels then insert the tail""" {
                    val e = 99
                    val list = (1..1088).toList()
                    val tempVec = PersistentVector(*list.toTypedArray())
                    val tempTail = tempVec.tail

                    val vec = tempVec.conj(e)
                    val root = vec.root
                    val mostRightLeaf = ((root.array[1] as Node<Int>).array[1]
                        as Node<Int>).array

                    vec.shift shouldBeExactly SHIFT * 2
                    mostRightLeaf shouldBeSameInstanceAs tempTail
                    vec.tail[0] shouldBe e
                    vec.count shouldBeExactly list.size + 1
                    root.isMutable shouldBeSameInstanceAs tempVec.root.isMutable
                    root.isMutable.value.shouldBeFalse()
                }

                """when the level is > 5 and the path is null,
                    it should create a new path then insert the tail""" {
                    val e = 99
                    val list = (1..2080).toList()
                    val tempVec = PersistentVector(*list.toTypedArray())
                    val tempTail = tempVec.tail
                    val tempIsMutable = tempVec.root.isMutable

                    val vec = tempVec.conj(e)
                    val root = vec.root
                    val subRoot = root.array[2] as Node<Int>
                    val mostRightLeaf = subRoot.array[0] as Node<Int>

                    vec.shift shouldBeExactly SHIFT * 2
                    mostRightLeaf.array shouldBeSameInstanceAs tempTail
                    vec.tail[0] shouldBe e
                    vec.count shouldBeExactly list.size + 1
                    root.isMutable.value.shouldBeFalse()
                    root.isMutable shouldBeSameInstanceAs tempIsMutable
                    subRoot.isMutable.value.shouldBeFalse()
                    subRoot.isMutable shouldBeSameInstanceAs tempIsMutable
                    mostRightLeaf.isMutable.value.shouldBeFalse()
                    mostRightLeaf.isMutable shouldBeSameInstanceAs tempIsMutable
                }

                "when root overflow, it should add 1 lvl by creating new root" {
                    val e = 99
                    val list = (1..1056).toList()
                    val tempVec = PersistentVector(*list.toTypedArray())
                    val tempIsMutable = tempVec.root.isMutable

                    val vec = tempVec.conj(e)
                    val root = vec.root
                    val subRoot1 = root.array[0] as Node<Int>
                    val subRoot2 = root.array[1] as Node<Int>
                    val mostRightLeaf = subRoot2.array[0] as Node<Int>

                    vec.count shouldBeExactly list.size + 1
                    vec.shift shouldBeExactly 10
                    vec.tail[0] as Int shouldBeExactly e
                    mostRightLeaf.array shouldBeSameInstanceAs tempVec.tail

                    root.isMutable.value.shouldBeFalse()
                    root.isMutable shouldBeSameInstanceAs tempIsMutable
                    subRoot1.isMutable shouldBeSameInstanceAs tempIsMutable
                    subRoot2.isMutable shouldBeSameInstanceAs tempIsMutable
                }
            }
        }

        "empty()" {
            v(1, 2, 3, 4).empty() shouldBeSameInstanceAs EmptyVector
        }

        "length()" {
            checkAll { list: List<Int> ->
                val vec = PersistentVector(*list.toTypedArray())

                vec.length() shouldBeExactly list.size
            }
        }

        val default = -1
        "nth(index)" {
            val list = (1..1056).toList()

            val vec = PersistentVector(*list.toTypedArray())

            shouldThrowExactly<IndexOutOfBoundsException> { vec.nth(2000) }
            shouldThrowExactly<IndexOutOfBoundsException> { vec.nth(list.size) }
            shouldThrowExactly<IndexOutOfBoundsException> { vec.nth(default) }
            vec.nth(1055) shouldBeExactly 1056
            vec.nth(1024) shouldBeExactly 1025
            vec.nth(1023) shouldBeExactly 1024
        }

        "nth(index, default)" {
            val list = (1..1056).toList()

            val vec = PersistentVector(*list.toTypedArray())

            vec.nth(1055, default) shouldBeExactly 1056
            vec.nth(1024, default) shouldBeExactly 1025
            vec.nth(1023, default) shouldBeExactly 1024
            vec.nth(2000, default) shouldBeExactly default
            vec.nth(default, default) shouldBeExactly default
        }

        "count" {
            checkAll { list: List<Int> ->
                val vec = PersistentVector(*list.toTypedArray())

                vec.count shouldBeExactly list.size
            }
        }

        "toString()" {
            val vec = PersistentVector(1, 2, 3, 4)

            vec.toString() shouldBe "[1 2 3 4]"
        }

        "it's root should be immutable" {
            val vec = PersistentVector(1, 2, 3)

            vec.root.isMutable.value.shouldBeFalse()
        }

        "asTransient() should turn this vector into a transient vector" {
            val vec = PersistentVector(1, 2, 3, 4, 5, 6, 7, 8)

            val tVec: TransientVector<Int> = vec.asTransient()

            tVec.count shouldBeExactly vec.count
            tVec.shift shouldBeExactly vec.shift
            tVec.root.isMutable.value.shouldBeTrue()
            assertArraysAreEquiv(tVec.tail, vec.tail)
        }

        "seq()" - {
            "when called on an empty vector, it should return null" {
                val emptyVec = v<Int>()

                emptyVec.seq() shouldBeSameInstanceAs Empty
            }

            "when called on a filled vector, it should return a Seq instance" {
                val vec = v(1, 2, 3)

                val seq = vec.seq() as Seq<Int>
                val rest = seq.rest() as Seq<Int>

                seq.shouldNotBeNull()
                seq.count shouldBeExactly 3
                seq.first() shouldBeExactly 1
                seq.index shouldBeExactly 0

                rest.count shouldBeExactly 2
                rest.index shouldBeExactly 1
                rest.rest().first() shouldBeExactly 3
                rest.rest().rest() shouldBeSameInstanceAs Empty
            }
        }

        "hashCode()" - {
            "when called on EmptyVector, it should return 1" {
                EmptyVector.hashCode() shouldBeExactly 1
            }

            "when called on a populated vectorm it should calculate the hash" {

                val gen = Arb.list(Arb.int().merge(Arb.int().map { null }))
                checkAll(gen) { list: List<Int?> ->
                    val prime = 31
                    val emptyVecHash = EmptyVector.hashCode()
                    val expectedHash = list.fold(emptyVecHash) { hash, i ->
                        prime * hash + i.hashCode()
                    }
                    val vec = v(*list.toTypedArray())

                    vec.hashCode() shouldBeExactly expectedHash
                    vec.hashCode() shouldBeExactly expectedHash // for coverage
                }
            }
        }

        "equals(x)" {
            v(1, 2, 3, 4).equals(null).shouldBeFalse()

            (v(1) == v(1, 2, 3)).shouldBeFalse()

            (v(1, 2, 3) == v(1, 2, 3)).shouldBeTrue()

            (v(1, 2, 3) == v(1, 2, 5)).shouldBeFalse()

            (v(v(1)) == v(v(1))).shouldBeTrue()

            (v(1, 2, 3) == listOf(1, 4)).shouldBeFalse()

            (v(1, 2, 3) == listOf(1, 2, 5)).shouldBeFalse()

            (v(1, 2, 3) == listOf(1, 2, 3)).shouldBeTrue()

            (v(User(1)) == listOf(User(2))).shouldBeFalse()

            (v(1, 2) == mapOf(1 to 2)).shouldBeFalse()

            (v(1, 2) == MockSeq(v(1, 2))).shouldBeTrue()

            (v(1, 2) == MockSeq(v(1, 3))).shouldBeFalse()

            (v(1, 2) == MockSeq(v(1, 2, 4))).shouldBeFalse()
        }

        "equiv(x)" {
            // assert equals behaviour
            v(1, 2, 3, 4).equiv(null).shouldBeFalse()

            v(1).equiv(v(1, 2, 3)).shouldBeFalse()

            v(1, 2, 3).equiv(v(1, 2, 3)).shouldBeTrue()

            v(1, 2, 3).equiv(v(1, 2, 5)).shouldBeFalse()

            v(v(1)).equiv(v(v(1))).shouldBeTrue()

            v(1, 2, 3).equiv(listOf(1, 4)).shouldBeFalse()

            v(1, 2, 3).equiv(listOf(1, 2, 5)).shouldBeFalse()

            v(1, 2, 3).equiv(listOf(1, 2, 3)).shouldBeTrue()

            v(User(1)).equiv(listOf(User(2))).shouldBeFalse()

            v(1, 2).equiv(mapOf(1 to 2)).shouldBeFalse()

            v(1, 2).equiv(MockSeq(v(1, 2))).shouldBeTrue()

            v(1, 2).equiv(MockSeq(v(1, 3))).shouldBeFalse()

            v(1, 2).equiv(MockSeq(v(1, 2, 4))).shouldBeFalse()

            // assert equiv behaviour
            v(1).equiv("vec").shouldBeFalse()

            v(1).equiv(l(2, null)).shouldBeFalse()

            v(2, null).equiv(l(2, 3)).shouldBeFalse()

            v(null, 2).equiv(l(2, 3)).shouldBeFalse()

            v(1).equiv(setOf(1)).shouldBeFalse()

            v(Any()).equiv(v(Any())).shouldBeFalse()

            v(1).equiv(v(1L)).shouldBeTrue()

            v(l(1)).equiv(PersistentList(listOf(1L))).shouldBeTrue()

            v(listOf(1L)).equiv(l(l(1))).shouldBeTrue()

            v(1.1).equiv(l(1.1)).shouldBeTrue()
        }

        "List implementation" - {
            "size()" {
                checkAll { l: List<Int> ->
                    val vec = v(*l.toTypedArray())

                    val list = vec as List<Int>

                    list.size shouldBeExactly l.size
                }
            }

            "get()" {
                val genA = Arb.list(Arb.int()).filter { it.isNotEmpty() }
                checkAll(genA) { l: List<Int> ->
                    val vec = v(*l.toTypedArray())

                    val list = vec as List<Int>

                    list[0] shouldBeExactly l[0]
                }
            }

            "isEmpty()" {
                v<Int>().isEmpty().shouldBeTrue()

                v(1, 2, 3, 4).isEmpty().shouldBeFalse()
            }

            "iterator()" {
                checkAll { list: List<Int> ->
                    val vec = v(*list.toTypedArray())

                    val iter = vec.iterator()

                    if (list.isEmpty()) iter.hasNext().shouldBeFalse()
                    else iter.hasNext().shouldBeTrue()

                    list.fold(Unit) { _: Unit, i: Int ->
                        iter.next() shouldBeExactly i
                    }

                    iter.hasNext().shouldBeFalse()
                    shouldThrowExactly<NoSuchElementException> { iter.next() }
                }
            }

            "indexOf(element)" {
                val vec = v(1L, 2.0, 3, 4)

                vec.indexOf(3) shouldBeExactly 2
                vec.indexOf(4L) shouldBeExactly 3
                vec.indexOf(1) shouldBeExactly 0
                vec.indexOf(6) shouldBeExactly -1
            }

            "lastIndexOf(element)" - {
                val vec = v(1, 1, 6, 6, 4, 5, 4)

                "when the element is not in the list, it should return -1" {
                    vec.lastIndexOf(10) shouldBeExactly -1
                }

                """|when the element is in the list,
                   |it should return the index of the last occurrence of
                   |the specified element
                """ {
                    vec.lastIndexOf(6) shouldBeExactly 3
                    vec.lastIndexOf(1) shouldBeExactly 1
                    vec.lastIndexOf(4) shouldBeExactly 6
                }
            }

            "listIterator(index)" {
                val vec = v(1, 2, 3, 4, 5)

                val iter = vec.listIterator(2)

                iter.hasPrevious().shouldBeTrue()
                iter.hasNext().shouldBeTrue()

                iter.nextIndex() shouldBeExactly 2
                iter.previousIndex() shouldBeExactly 1

                iter.next() shouldBeExactly vec[2]

                iter.previousIndex() shouldBeExactly 2
                iter.nextIndex() shouldBeExactly 3

                iter.previous() shouldBeExactly vec[2]

                iter.previousIndex() shouldBeExactly 1
                iter.nextIndex() shouldBeExactly 2

                iter.next() shouldBeExactly vec[2]
                iter.next() shouldBeExactly vec[3]
                iter.next() shouldBeExactly vec[4]

                iter.hasNext().shouldBeFalse()

                shouldThrowExactly<NoSuchElementException> {
                    iter.next()
                }

                iter.previous()
                iter.previous()
                iter.previous()
                iter.previous()
                iter.previous()

                iter.hasPrevious().shouldBeFalse()

                shouldThrowExactly<NoSuchElementException> {
                    iter.previous()
                }
            }

            "listIterator()" {
                val vec = v(1, 2, 3, 4, 5)

                val iter = vec.listIterator()

                iter.hasPrevious().shouldBeFalse()
                iter.hasNext().shouldBeTrue()

                iter.nextIndex() shouldBeExactly 0
                iter.previousIndex() shouldBeExactly -1

                iter.next() shouldBeExactly vec[0]

                iter.previousIndex() shouldBeExactly 0
                iter.nextIndex() shouldBeExactly 1

                iter.previous() shouldBeExactly vec[0]

                iter.previousIndex() shouldBeExactly -1
                iter.nextIndex() shouldBeExactly 0

                iter.next() shouldBeExactly vec[0]
                iter.next() shouldBeExactly vec[1]
                iter.next() shouldBeExactly vec[2]
                iter.next() shouldBeExactly vec[3]
                iter.next() shouldBeExactly vec[4]

                iter.hasNext().shouldBeFalse()

                shouldThrowExactly<NoSuchElementException> {
                    iter.next()
                }

                iter.previous()
                iter.previous()
                iter.previous()
                iter.previous()
                iter.previous()

                iter.hasPrevious().shouldBeFalse()

                shouldThrowExactly<NoSuchElementException> {
                    iter.previous()
                }
            }

            "contains(element)" {
                val vec = v(1, 2, 3, 4)

                vec.contains(1).shouldBeTrue()
                vec.contains(1L).shouldBeTrue()

                vec.contains(15).shouldBeFalse()
            }

            "containsAll(elements)" {
                val vec = v(1, 2L, 3, 4, "a")

                vec.containsAll(v(1, 2, "a")).shouldBeTrue()

                vec.containsAll(v(1, 2, "b")).shouldBeFalse()
            }
        }
    }

    "EmptyVector" - {
        "toString() should return []" {
            PersistentVector<Int>().toString() shouldBe "[]"
        }

        "count should be 0" {
            PersistentVector<Int>().count shouldBeExactly 0
        }

        "shift should be 5" {
            PersistentVector<Int>().shift shouldBeExactly SHIFT
        }

        "tail should be an empty array of size 0" {
            val tail = PersistentVector<Int>().tail

            tail.size shouldBeExactly 0
            shouldThrowExactly<ArrayIndexOutOfBoundsException> {
                tail[0]
            }
        }

        "root should be an empty node of size 32" {
            val array = PersistentVector<Int>().root.array

            array.size shouldBeExactly 32
            array[0].shouldBeNull()
            array[31].shouldBeNull()
        }

        "it's root should be immutable" {
            val emptyVec = PersistentVector<Int>()

            emptyVec.root.isMutable.value.shouldBeFalse()
        }
    }

    "TransientVector" - {

        @Suppress("UNCHECKED_CAST")
        "constructor" {
            val v = PersistentVector(*(1..57).toList().toTypedArray())
            val vRoot = v.root

            val tv = TransientVector(v)
            val tvRoot = tv.root

            tv.count shouldBeExactly v.count
            tv.shift shouldBeExactly v.shift
            tv.tail.size shouldBeExactly 32
            assertArraysAreEquiv(tv.tail, v.tail)
            tvRoot shouldNotBeSameInstanceAs vRoot
            tvRoot.array.size shouldBeExactly vRoot.array.size
            tvRoot.array shouldNotBeSameInstanceAs vRoot.array
            tvRoot.isMutable shouldNotBeSameInstanceAs vRoot.isMutable
            tvRoot.isMutable.value.shouldBeTrue()
            vRoot.array.fold(0) { index: Int, e: Any? ->

                if (e != null) {
                    val tvNode = tvRoot.array[index] as Node<Int>
                    val vNode = e as Node<Int>

                    tvNode shouldBe vNode
                    tvNode.isMutable shouldBeSameInstanceAs vRoot.isMutable
                }
                index + 1
            }
        }

        "invalidate() should set isMutable to false" {
            val v = PersistentVector(*(1..57).toList().toTypedArray())
            val tv = TransientVector(v)
            val isMutable = tv.root.isMutable

            tv.invalidate()

            tv.root.isMutable shouldBeSameInstanceAs isMutable
            tv.root.isMutable.value.shouldBeFalse()
        }

        "assertMutable()" - {
            "when called on a mutable transient, it shouldn't throw" {
                val v = PersistentVector(*(1..57).toList().toTypedArray())
                val tv = TransientVector(v)

                shouldNotThrow<Exception> { tv.assertMutable() }
            }

            "when called on an invalidated transient, it should throw" {
                val v = PersistentVector(*(1..57).toList().toTypedArray())
                val tv = TransientVector(v)
                tv.invalidate()

                val e = shouldThrowExactly<IllegalStateException> {
                    tv.assertMutable()
                }

                e.message shouldBe "Transient used after persistent() call"
            }
        }

        "count" - {
            """when called on a mutable transient,
                        it should return the count of the transient vector""" {
                val v = PersistentVector(*(1..57).toList().toTypedArray())
                val tv = TransientVector(v)

                tv.root.isMutable.value.shouldBeTrue()
                tv.count shouldBeExactly v.count
            }

            """when called on a invalidated transient,
                                it should throw IllegalStateException""" {
                val v = PersistentVector(*(1..57).toList().toTypedArray())
                val tv = TransientVector(v)
                tv.invalidate()

                val e = shouldThrowExactly<IllegalStateException> { tv.count }

                e.message shouldBe "Transient used after persistent() call"
            }
        }

        "persistent()" - {
            "when called on a invalidated transient, when it should throw" {
                val v = PersistentVector(*(1..57).toList().toTypedArray())
                val tv = TransientVector(v)
                tv.invalidate()

                val e = shouldThrowExactly<IllegalStateException> {
                    tv.persistent()
                }

                e.message shouldBe "Transient used after persistent() call"
            }

            """when called on a mutable transient,
                it should return the PersistentVector of that transient,
                                                        and invalidate it""" {
                val v = PersistentVector(*(1..57).toList().toTypedArray())
                val tv = TransientVector(v)
                tv.tail = v.tail.copyOf(32)

                val vec = tv.persistent()
                val root = vec.root

                root.isMutable.value.shouldBeFalse()
                root shouldBeSameInstanceAs tv.root
                tv.root.isMutable.value.shouldBeFalse()
                vec.count shouldBeExactly v.count
                vec.shift shouldBeExactly v.shift
                vec.tail.size shouldBeExactly 25
                assertArraysAreEquiv(vec.tail, v.tail)
            }
        }

        "conj(e:E)" - {

            "when called on an invalidated transient, it should throw" {
                val tempTv = TransientVector(PersistentVector(1, 2, 3))
                tempTv.invalidate()

                val e = shouldThrowExactly<IllegalStateException> {
                    tempTv.conj(99)
                }

                e.message shouldBe "Transient used after persistent() call"
            }

            "when level is 5 and there is room in tail, it should add to it" {
                val i = 7
                val ints = Arb.int().filter { it != i }
                checkAll(Arb.list(ints, 0..31)) { list ->
                    val tempVec = PersistentVector(*list.toTypedArray())
                    val tempTv = TransientVector(tempVec)

                    val tv = tempTv.conj(i)
                    val tvTail = tv.tail
                    val tvRoot = tv.root

                    tv.shift shouldBeExactly SHIFT
                    tv.count shouldBeExactly list.size + 1
                    tvRoot.isMutable.value.shouldBeTrue()
                    tvRoot.array.size shouldBeExactly 32
                    tvTail.size shouldBeExactly 32
                    assertArraysAreEquiv(tvTail, tempVec.tail)

                    tvTail[list.size] as Int shouldBeExactly i
                }
            }

            "when tail overflow, it should push tail into the vec" - {
                @Suppress("UNCHECKED_CAST")
                "when level is 5, it should insert the tail in root node" {
                    val listGen = Arb.list(Arb.int(), (32..1024)).filter {
                        it.size % 32 == 0
                    }

                    checkAll(listGen, Arb.int()) { l: List<Int>, i: Int ->
                        val tempVec = PersistentVector(*l.toTypedArray())
                        val tempTv = TransientVector(tempVec)

                        val tv = tempTv.conj(i)
                        val tvTail = tv.tail
                        val tvRoot = tv.root

                        tv.shift shouldBeExactly SHIFT
                        var index = 31
                        var isMostRightLeafFound = false
                        while (index >= 0 && !isMostRightLeafFound) {
                            val node = tv.root.array[index]
                            if (node != null) {
                                val mostRightLeaf = node as Node<Int>
                                val array = mostRightLeaf.array

                                mostRightLeaf.isMutable shouldBeSameInstanceAs
                                    tempTv.root.isMutable
                                array shouldBe tempVec.tail
                                mostRightLeaf.isMutable shouldBeSameInstanceAs
                                    tempTv.root.isMutable
                                isMostRightLeafFound = true
                            }
                            index--
                        }

                        tvRoot.isMutable.value.shouldBeTrue()
                        isMostRightLeafFound.shouldBeTrue()
                        tv.count shouldBeExactly l.size + 1
                        tvTail.size shouldBeExactly 32
                        tvTail[0] shouldBe i
                    }
                }

                @Suppress("UNCHECKED_CAST")
                """when level is > 5, it should iterate through the
                                            levels then insert the tail""" {
                    val e = 99
                    val list = (1..1088).toList()
                    val tempVec = PersistentVector(*list.toTypedArray())
                    val root = tempVec.root
                    val tempTv = TransientVector(tempVec)

                    val tv = tempTv.conj(e)
                    val tvTail = tv.tail
                    val tvRoot = tv.root
                    val tvSubRoot = tvRoot.array[1] as Node<Int>
                    val firstLeft = tvSubRoot.array[0] as Node<Int>
                    val mostRightLeaf = tvSubRoot.array[1] as Node<Int>

                    tempTv.shift shouldBeExactly SHIFT * 2
                    tvTail[0] as Int shouldBeExactly e
                    assertArraysAreEquiv(mostRightLeaf.array, tempVec.tail)
                    tv.count shouldBeExactly list.size + 1

                    tvRoot.isMutable shouldNotBeSameInstanceAs root.isMutable
                    tvRoot.isMutable.value.shouldBeTrue()
                    tvSubRoot.isMutable shouldBeSameInstanceAs tvRoot.isMutable

                    firstLeft.isMutable shouldBeSameInstanceAs root.isMutable
                    mostRightLeaf.isMutable shouldBeSameInstanceAs
                        tvRoot.isMutable
                }

                @Suppress("UNCHECKED_CAST")
                """when level is > 5 and the subroot/path is null,
                        it should create a new path then insert the tail""" {
                    val e = 99
                    val list = (1..2080).toList()
                    val tempVec = PersistentVector(*list.toTypedArray())
                    val tempTv = TransientVector(tempVec)

                    val tv = tempTv.conj(e)
                    val tvTail = tv.tail
                    val tvRoot = tv.root
                    val tvSubRoot = tvRoot.array[2] as Node<Int>
                    val mostRightLeaf = tvSubRoot.array[0] as Node<Int>

                    tv.shift shouldBeExactly SHIFT * 2
                    assertArraysAreEquiv(mostRightLeaf.array, tempVec.tail)
                    tvTail[0] shouldBe e
                    tv.count shouldBeExactly list.size + 1
                    tvRoot.isMutable.value.shouldBeTrue()
                    tvSubRoot.isMutable shouldBeSameInstanceAs tvRoot.isMutable
                    mostRightLeaf.isMutable shouldBeSameInstanceAs
                        tvRoot.isMutable
                }

                @Suppress("UNCHECKED_CAST")
                "when root overflow, it should add 1 lvl by creating new root" {
                    val e = 99
                    val list = (1..1056).toList()
                    val tempVec = PersistentVector(*list.toTypedArray())
                    val tempTv = TransientVector(tempVec)

                    val tv = tempTv.conj(e)
                    val tvRoot = tv.root
                    val tvIsMutable = tvRoot.isMutable
                    val subRoot1 = tvRoot.array[0] as Node<Int>
                    val subRoot2 = tvRoot.array[1] as Node<Int>
                    val mostRightLeaf =
                        subRoot2.array[0] as Node<Int>

                    tv.count shouldBeExactly list.size + 1
                    tv.shift shouldBeExactly 10
                    tv.tail[0] as Int shouldBeExactly e
                    assertArraysAreEquiv(mostRightLeaf.array, tempVec.tail)

                    tvIsMutable shouldBeSameInstanceAs tempTv.root.isMutable
                    subRoot1.isMutable shouldBeSameInstanceAs tvIsMutable
                    subRoot2.isMutable shouldBeSameInstanceAs tvIsMutable
                    mostRightLeaf.isMutable shouldBeSameInstanceAs tvIsMutable
                }
            }
        }
    }

    "v() should return an empty persistent vector" {
        val empty = v<Int>()

        empty shouldBeSameInstanceAs EmptyVector
    }

    "v(args) should return an empty persistent vector" {
        val vec = v(1, 2, 3, 4)

        vec.count shouldBeExactly 4

        vec.nth(0) shouldBeExactly 1
        vec.nth(1) shouldBeExactly 2
        vec.nth(2) shouldBeExactly 3
        vec.nth(3) shouldBeExactly 4
    }
}) {
    companion object {
        private fun assertArraysAreEquiv(a1: Array<Any?>, a2: Array<Any?>) {
            a2.fold(0) { index: Int, i: Any? ->
                val n = a1[index] as Int

                n shouldBeExactly i as Int

                index + 1
            }
        }
    }
}
