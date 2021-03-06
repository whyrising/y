package com.github.whyrising.y.set

import com.github.whyrising.y.concretions.map.MapEntry
import com.github.whyrising.y.core.IHashEq
import com.github.whyrising.y.map.IPersistentMap
import com.github.whyrising.y.map.MapIterable
import com.github.whyrising.y.seq.ISeq
import com.github.whyrising.y.util.Murmur3

abstract class APersistentSet<out E>(val map: IPersistentMap<E, E>) :
    PersistentSet<E>, Set<E>, Collection<E>, IHashEq {
    private var _hash = 0
    private var _hashEq = 0

    override val count: Int = map.count

    override fun toString(): String {
        var seq = seq()
        var str = "#{"
        while (seq.count != 0) {
            str += seq.first()
            seq = seq.rest()

            if (seq.count != 0) str += " "
        }

        return "$str}"
    }

    override fun hashCode(): Int {
        var hash = _hash

        if (hash == 0) {
            var seq = seq()
            while (seq.count != 0) {
                hash += seq.first().hashCode()
                seq = seq.rest()
            }
            _hash = hash
        }

        return hash
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Set<*>) return false

        if (count != other.size) return false

        for (e in other) {
            if (!contains(e)) return false
        }

        return true
    }

    @ExperimentalStdlibApi
    override fun hasheq(): Int {
        var cached = _hashEq

        if (cached == 0) {
            cached = Murmur3.hashUnordered(this)
            _hashEq = cached
        }

        return cached
    }

    @Suppress("UNCHECKED_CAST")
    override fun equiv(other: Any?): Boolean {
        when {
            this === other -> return true
            other !is Set<*> -> return false
            count != other.size -> return false
            else -> for (e in other) if (!contains(e as E)) return false
        }

        return true
    }

    override fun seq(): ISeq<E> = map.keyz()

    override operator fun get(key: @UnsafeVariance E): E? = map.valAt(key)

    operator fun invoke(e: @UnsafeVariance E): E? = get(e)

    override fun contains(element: @UnsafeVariance E): Boolean =
        map.containsKey(element)

    // Set Implementation
    override val size: Int
        get() = count

    override fun containsAll(elements: Collection<@UnsafeVariance E>): Boolean {
        for (e in elements)
            if (!contains(e)) return false

        return true
    }

    override fun isEmpty(): Boolean = count == 0

    @Suppress("UNCHECKED_CAST")
    override fun iterator(): Iterator<E> = when (map) {
        is MapIterable<*, *> -> map.keyIterator() as Iterator<E>
        else -> object : Iterator<E> {
            val iter = map.iterator()

            override fun hasNext(): Boolean = iter.hasNext()

            override fun next(): E = (iter.next() as MapEntry<E, E>).key
        }
    }
}
