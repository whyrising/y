package com.github.whyrising.y

abstract class APersistentSet<out E>(val map: IPersistentMap<E, E>) :
    PersistentSet<E>, Set<E> {

    override val count: Int = map.count

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