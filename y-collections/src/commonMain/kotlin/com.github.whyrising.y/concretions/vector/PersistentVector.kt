package com.github.whyrising.y.concretions.vector

import com.github.whyrising.y.ArrayChunk
import com.github.whyrising.y.Chunk
import com.github.whyrising.y.concretions.list.ASeq
import com.github.whyrising.y.concretions.list.PersistentList
import com.github.whyrising.y.concretions.vector.PersistentVector.EmptyVector
import com.github.whyrising.y.concretions.vector.PersistentVector.Node.EmptyNode
import com.github.whyrising.y.core.InstaCount
import com.github.whyrising.y.mutable.collection.IMutableCollection
import com.github.whyrising.y.mutable.collection.ITransientCollection
import com.github.whyrising.y.seq.IChunkedSeq
import com.github.whyrising.y.seq.IPersistentCollection
import com.github.whyrising.y.seq.ISeq
import com.github.whyrising.y.vector.APersistentVector
import com.github.whyrising.y.vector.IPersistentVector
import kotlinx.atomicfu.AtomicBoolean
import kotlinx.atomicfu.AtomicInt
import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.locks.reentrantLock
import kotlinx.atomicfu.locks.withLock
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

internal const val SHIFT = 5
internal const val BF = 32

internal class PersistentVectorSerializer<E>(element: KSerializer<E>) :
    KSerializer<PersistentVector<E>> {

    internal val listSerializer = ListSerializer(element)

    override val descriptor: SerialDescriptor = listSerializer.descriptor

    override fun deserialize(decoder: Decoder): PersistentVector<E> =
        listSerializer.deserialize(decoder).toPvector()

    override fun serialize(encoder: Encoder, value: PersistentVector<E>) =
        listSerializer.serialize(encoder, value)
}

@Serializable(with = PersistentVectorSerializer::class)
sealed class PersistentVector<out E>(
    override val count: Int,
    internal val shift: Int,
    internal val root: Node<E>,
    internal val tail: Array<Any?>
) : APersistentVector<E>(), IMutableCollection<E> {

    override
    fun assocN(index: Int, value: @UnsafeVariance E): IPersistentVector<E> {
        @Suppress("UNCHECKED_CAST")
        fun assoc(level: Int, node: Node<E>): Node<E> {
            val copy: Node<E> = Node(node.isMutable, node.array.copyOf())

            when (level) {
                0 -> copy.array[index and 0x01f] = value
                else -> {
                    val subIndex = (index ushr level) and 0x01f
                    copy.array[subIndex] =
                        assoc(level - SHIFT, (node.array[subIndex] as Node<E>))
                }
            }

            return copy
        }

        return when (index) {
            in 0 until count -> {
                when {
                    index >= tailOffset(count) -> {
                        val newTail = tail.copyOf()
                        newTail[index and 0x01f] = value

                        Vector(count, shift, root, newTail)
                    }
                    else -> Vector(count, shift, assoc(shift, root), tail)
                }
            }
            count -> conj(value)
            else -> throw IndexOutOfBoundsException("index = $index")
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun pushTail(level: Int, parent: Node<E>, tail: Node<E>): Node<E> {
        val rootNode = Node<E>(parent.isMutable, parent.array.copyOf())
        val subIndex = ((count - 1) ushr level) and 0x01f

        val nodeToInsert: Node<E> = if (level == SHIFT) tail
        else when (val child = parent.array[subIndex]) {
            null -> newPath(root.isMutable, level - 5, tail)
            else -> pushTail(level - SHIFT, child as Node<E>, tail)
        }

        rootNode.array[subIndex] = nodeToInsert

        return rootNode
    }

    override fun conj(e: @UnsafeVariance E): PersistentVector<E> {
        // empty slot available in tail?
        if (count - tailOffset(count) < BF) {
            val newTail = tail.copyOf(tail.size + 1)
            newTail[tail.size] = e

            return Vector(count + 1, shift, root, newTail)
        }

        val tailNode = Node<E>(root.isMutable, tail)
        var newShift = shift
        val newRoot: Node<E>

        when {
            // root overflow?
            (count ushr SHIFT) > (1 shl shift) -> {
                newRoot = Node(root.isMutable)
                newRoot.array[0] = root
                newRoot.array[1] = newPath(root.isMutable, shift, tailNode)
                newShift += SHIFT
            }
            else -> newRoot = pushTail(shift, root, tailNode)
        }

        return Vector(count + 1, newShift, newRoot, arrayOf(e))
    }

    override fun empty(): IPersistentCollection<E> = EmptyVector

    @Suppress("UNCHECKED_CAST")
    internal fun leafArrayBy(index: Int): Array<Any?> = when {
        indexOutOfBounds(index) -> throw IndexOutOfBoundsException()
        index >= tailOffset(count) -> tail
        else -> {
            var level = shift
            var node = root

            while (level > 0) {
                node = node.array[(index ushr level) and 0x01f] as Node<E>
                level -= SHIFT
            }

            val value = node.array

            value
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun nth(index: Int): E {
        val leaf = leafArrayBy(index)

        return leaf[index and 0x01f] as E
    }

    override fun asTransient(): TransientVector<E> = TransientVector(this)

    @Suppress("UNCHECKED_CAST")
    private fun popTail(level: Int, node: Node<E>): Node<E>? =
        (((count - 2) ushr level) and 0x01f).let { subIndex ->
            when {
                level > SHIFT -> {
                    val newChild =
                        popTail(level - SHIFT, node.array[subIndex] as Node<E>)

                    when {
                        newChild == null && subIndex == 0 -> null
                        else -> {
                            val n = Node<E>(node.isMutable, node.array.copyOf())
                            n.array[subIndex] = newChild

                            n
                        }
                    }
                }
                subIndex == 0 -> null
                else -> {
                    val n = Node<E>(node.isMutable, node.array.copyOf())
                    n.array[subIndex] = null

                    n
                }
            }
        }

    @Suppress("UNCHECKED_CAST")
    override fun pop(): PersistentVector<E> = when {
        isEmpty() -> EmptyVector
        count == 1 -> EmptyVector
        count - tailOffset(count) > 1 -> {
            val newTail = tail.copyOf(count - 1)

            Vector(count - 1, shift, root, newTail)
        }
        else -> {
            val newTail = leafArrayBy(count - 2)

            var newRoot: Node<E>? = popTail(shift, root)
            var newShift = shift

            if (newRoot == null) newRoot = EmptyNode

            if (shift > SHIFT && newRoot.array[1] == null) {
                newRoot = newRoot.array[0] as Node<E>
                newShift -= SHIFT
            }

            Vector(count - 1, newShift, newRoot, newTail)
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun rangedIterator(start: Int, end: Int): Iterator<E> =
        object : Iterator<E> {
            var i = start
            var base = i - (i % BF)
            var array: Array<Any?>? = when {
                start < count -> leafArrayBy(i)
                else -> null
            }

            override fun hasNext(): Boolean = i < end

            override fun next(): E = when {
                hasNext() -> {
                    if (i - base == BF) {
                        array = leafArrayBy(i)
                        base += BF
                    }

                    array?.get(i++ and 0x01f) as E
                }
                else -> throw NoSuchElementException()
            }
        }

    override fun iterator(): Iterator<E> = rangedIterator(0, count)

    override fun seq(): ISeq<E> = when (count) {
        0 -> Seq.emptySeq()
        else -> ChunkedSeq(this, 0, 0)
    }

    sealed class Node<out T>(
        val isMutable: AtomicBoolean,
        val array: Array<Any?>
    ) {
        internal class Node2<out T>(
            isMutable: AtomicBoolean,
            _array: Array<Any?>
        ) : Node<T>(isMutable, _array)

        internal object EmptyNode :
            Node<Nothing>(atomic(false), arrayOfNulls(BF))

        companion object {
            operator fun <T> invoke(isMutable: AtomicBoolean): Node<T> =
                Node2(isMutable, arrayOfNulls(BF))

            operator fun <T> invoke(
                isMutable: AtomicBoolean,
                nodes: Array<Any?>
            ): Node<T> = Node2(isMutable, nodes)
        }
    }

    internal object EmptyVector : PersistentVector<Nothing>(
        0,
        SHIFT,
        EmptyNode,
        arrayOfNulls(0)
    ) {
        override fun toString(): String = "[]"
    }

    internal class Vector<out E>(
        _count: Int,
        _shift: Int,
        _root: Node<E>,
        _tail: Array<Any?>
    ) : PersistentVector<E>(_count, _shift, _root, _tail)

    internal class ChunkedSeq<out E>(
        val vector: PersistentVector<E>,
        val index: Int,
        val offset: Int,
        val node: Array<Any?> = vector.leafArrayBy(index)
    ) : ASeq<E>(), IChunkedSeq<E>, InstaCount {
        constructor(
            vector: PersistentVector<E>,
            node: Array<Any?>,
            index: Int,
            offset: Int
        ) : this(vector, index, offset, node)

        @Suppress("UNCHECKED_CAST")
        override fun firstChunk(): Chunk<E> =
            ArrayChunk(node as Array<E>, offset)

        override fun restChunks(): ISeq<E> = when {
            index + node.size < vector.size ->
                ChunkedSeq(vector, index + node.size, 0)
            else -> PersistentList.Empty
        }

        override val count: Int
            get() = vector.count - (index + offset)

        @Suppress("UNCHECKED_CAST")
        override fun first(): E = node[offset] as E

        override fun rest(): ISeq<E> = (offset + 1).let {
            when {
                it < node.size -> ChunkedSeq(vector, node, index, it)
                else -> restChunks()
            }
        }
    }

    class TransientVector<out E> private constructor(
        size: Int,
        shift: Int,
        root: Node<E>,
        tail: Array<Any?>
    ) : InstaCount, ITransientCollection<E> {

        private val _count: AtomicInt = atomic(size)
        private val _shift: AtomicInt = atomic(shift)
        private val _root: AtomicRef<Node<E>> = atomic(mutableNode(root))
        private val _tail: AtomicRef<Array<Any?>> = atomic(tail)

        fun assertMutable() {
            if (!_root.value.isMutable.value)
                throw IllegalStateException(
                    "Transient used after persistent() call"
                )
        }

        override val count: Int
            get() {
                assertMutable()
                return _count.value
            }

        val shift: Int
            get() = _shift.value

        val root: Node<E>
            get() = _root.value

        internal var tail: Array<Any?>
            get() = _tail.value
            set(value) {
                _tail.value = value
            }

        internal fun invalidate() {
            _root.value.isMutable.value = false
        }

        private fun assertNodeCreatedByThisVector(node: Node<E>): Node<E> =
            when (node.isMutable) {
                root.isMutable -> node
                else -> Node(root.isMutable, node.array.copyOf())
            }

        @Suppress("UNCHECKED_CAST")
        private
        fun pushTail(level: Int, parent: Node<E>, tail: Node<E>): Node<E> {
            val subIndex = ((count - 1) ushr level) and 0x01f

            val rootNode = assertNodeCreatedByThisVector(parent)

            val nodeToInsert: Node<E> = if (level == SHIFT) tail
            else when (val child = rootNode.array[subIndex]) {
                null -> newPath(root.isMutable, level - 5, tail)
                else -> pushTail(level - SHIFT, child as Node<E>, tail)
            }

            rootNode.array[subIndex] = nodeToInsert

            return rootNode
        }

        override fun conj(e: @UnsafeVariance E): TransientVector<E> {
            lock.withLock {
                assertMutable()

                val oldCount = count
                // empty slot available in tail?
                if (oldCount - tailOffset(oldCount) < BF) {
                    tail[oldCount and 0x01f] = e
                    _count.incrementAndGet()

                    return this
                }

                val tailNode = Node<E>(root.isMutable, tail)
                tail = arrayOfNulls(BF)
                tail[0] = e

                var newShift = shift
                val newRoot: Node<E>
                if ((count ushr SHIFT) > (1 shl shift)) {
                    newRoot = Node(root.isMutable)
                    newRoot.array[0] = root
                    newRoot.array[1] = newPath(root.isMutable, shift, tailNode)
                    newShift += SHIFT
                } else newRoot = pushTail(shift, root, tailNode)

                _root.value = newRoot
                _shift.value = newShift
                _count.incrementAndGet()
            }

            return this
        }

        override fun persistent(): PersistentVector<E> {
            assertMutable()
            invalidate()

            val trimmedTail =
                arrayOfNulls<Any?>(_count.value - tailOffset(_count.value))

            tail.copyInto(trimmedTail, 0, 0, trimmedTail.size)

            return Vector(_count.value, _shift.value, _root.value, trimmedTail)
        }

        companion object {
            val lock = reentrantLock()

            private fun <E> mutableNode(node: Node<E>): Node<E> =
                Node(atomic(true), node.array.copyOf())

            private fun maximizeTail(tail: Array<Any?>): Array<Any?> {
                val maxTail = arrayOfNulls<Any?>(BF)

                return tail.copyInto(maxTail, 0, 0, tail.size)
            }

            operator
            fun <E> invoke(vec: PersistentVector<E>): TransientVector<E> =
                TransientVector(
                    vec.count,
                    vec.shift,
                    vec.root,
                    maximizeTail(vec.tail)
                )
        }
    }

    companion object {
        internal operator fun <E> invoke(): PersistentVector<E> = EmptyVector

        @Suppress("UNCHECKED_CAST")
        internal operator fun <E> invoke(vararg args: E): PersistentVector<E> {
            val argsCount = args.size

            return when {
                argsCount == 0 -> EmptyVector
                argsCount <= BF -> {
                    val tail = args as Array<Any?>
                    Vector(argsCount, SHIFT, EmptyNode, tail)
                }
                else -> {
                    val empty: TransientVector<E> = EmptyVector.asTransient()

                    args.fold(empty) { tVec, e -> tVec.conj(e) }.persistent()
                }
            }
        }

        private fun tailOffset(count: Int): Int = when {
            count < BF -> 0
            else -> ((count - 1) ushr SHIFT) shl SHIFT
        }

        private tailrec fun <E> newPath(
            isMutable: AtomicBoolean,
            level: Int,
            node: Node<E>
        ): Node<E> {
            if (level == 0) return node

            val path = Node<E>(isMutable)
            path.array[0] = node

            return newPath(isMutable, level - SHIFT, path)
        }

        internal fun <E> create(list: List<E>): PersistentVector<E> {
            val size = list.size

            return when {
                size == 0 -> EmptyVector
                size <= BF ->
                    Vector(size, SHIFT, EmptyNode, list.toTypedArray())
                else -> {
                    val empty: TransientVector<E> = EmptyVector.asTransient()

                    list.fold(empty) { tVec, e -> tVec.conj(e) }.persistent()
                }
            }
        }
    }
}

fun <E> v(): PersistentVector<E> = EmptyVector

fun <E> v(vararg elements: E): PersistentVector<E> = PersistentVector(*elements)

fun <E> List<E>.toPvector(): PersistentVector<E> = PersistentVector.create(this)
