package com.github.whyrising.y

import kotlin.collections.Map.Entry

sealed class PersistentArrayMap<out K, out V>(
    internal val array: Array<Pair<@UnsafeVariance K, @UnsafeVariance V>>
) : APersistentMap<K, V>() {

    @Suppress("UNCHECKED_CAST")
    private fun createArrayMap(newPairs: Array<out Pair<K, V>?>) =
        ArrayMap(newPairs as Array<Pair<K, V>>)

    private fun indexOf(key: @UnsafeVariance K): Int {
        for (i in array.indices)
            if (equiv(key, array[i].first)) return i

        return -1
    }

    private fun keyIsAlreadyAvailable(index: Int): Boolean = index >= 0

    override fun assoc(key: @UnsafeVariance K, value: @UnsafeVariance V):
        IPersistentMap<K, V> {
        val index: Int = indexOf(key)
        val newPairs: Array<out Pair<K, V>?>

        when {
            keyIsAlreadyAvailable(index) -> {
                if (array[index].second == value) return this

                newPairs = array.copyOf()
                newPairs[index] = Pair(key, value)
            }
            else -> {
                // TODO: if pairs.size >= HASHTABLE_THRESHOLD, create a HashMap

                newPairs = arrayOfNulls(array.size + 1)

                if (array.isNotEmpty())
                    array.copyInto(newPairs, 0, 0, array.size)

                newPairs[newPairs.size - 1] = Pair(key, value)
            }
        }

        return createArrayMap(newPairs)
    }

    override fun assocNew(key: @UnsafeVariance K, value: @UnsafeVariance V):
        IPersistentMap<K, V> {
        val index: Int = indexOf(key)
        val newPairs: Array<out Pair<K, V>?>

        if (keyIsAlreadyAvailable(index))
            throw RuntimeException("The key $key is already present.")

        // TODO: if pairs.size >= HASHTABLE_THRESHOLD, create a HashMap

        newPairs = arrayOfNulls(array.size + 1)

        if (array.isNotEmpty())
            array.copyInto(newPairs, 0, 0, array.size)

        newPairs[newPairs.size - 1] = Pair(key, value)

        return createArrayMap(newPairs)
    }

    override fun dissoc(key: @UnsafeVariance K): IPersistentMap<K, V> =
        indexOf(key).let { index ->
            when {
                keyIsAlreadyAvailable(index) -> {
                    val size = array.size - 1

                    if (size == 0) return EmptyArrayMap

                    val newPairs: Array<Pair<K, V>?> = arrayOfNulls(size)
                    array.copyInto(newPairs, 0, 0, index)
                    array.copyInto(newPairs, index, index + 1, array.size)

                    return createArrayMap(newPairs)
                }
                else -> return this
            }

        }

    override fun containsKey(key: @UnsafeVariance K): Boolean =
        keyIsAlreadyAvailable(indexOf(key))

    override fun entryAt(
        key: @UnsafeVariance K
    ): IMapEntry<K, V>? = indexOf(key).let { index ->
        when {
            keyIsAlreadyAvailable(index) -> {
                val (first, second) = array[index]
                MapEntry(first, second)
            }
            else -> null
        }
    }

    override fun valAt(
        key: @UnsafeVariance K, default: @UnsafeVariance V?
    ): V? = indexOf(key).let { index ->
        when {
            keyIsAlreadyAvailable(index) -> array[index].second
            else -> default
        }
    }

    override fun valAt(key: @UnsafeVariance K): V? = valAt(key, null)

    override fun seq(): ISeq<MapEntry<K, V>> = when (count) {
        0 -> emptySeq()
        else -> Seq(array, 0)
    }

    override val count: Int = array.size

    override fun empty(): IPersistentCollection<Any?> = EmptyArrayMap

    override
    fun iterator(): Iterator<Entry<K, V>> = object : Iterator<Entry<K, V>> {

        var index = 0

        override fun hasNext(): Boolean = index < array.size

        override fun next(): Entry<K, V> = when {
            index >= array.size -> throw NoSuchElementException()
            else -> {
                val pair = array[index]
                index++

                MapEntry(pair.first, pair.second)
            }
        }
    }

    internal object EmptyArrayMap : PersistentArrayMap<Nothing, Nothing>(
        emptyArray()
    ) {

        override fun toString(): String = "{}"
    }

    internal class ArrayMap<out K, out V>(
        internal val pairs: Array<Pair<@UnsafeVariance K, @UnsafeVariance V>>
    ) : PersistentArrayMap<K, V>(pairs)

    internal class Seq<out K, out V>(
        private val array: Array<Pair<@UnsafeVariance K, @UnsafeVariance V>>,
        val index: Int
    ) : ASeq<MapEntry<K, V>>() {

        override val count: Int = array.size - index

        override
        fun first(): MapEntry<K, V> = array[index].let { (first, second) ->
            MapEntry(first, second)
        }

        override fun rest(): ISeq<MapEntry<K, V>> = (index + 1).let { i ->
            when {
                i < array.size -> Seq(array, i)
                else -> emptySeq()
            }
        }
    }

    companion object {
        operator fun <K, V> invoke(): PersistentArrayMap<K, V> = EmptyArrayMap

        private fun <K> areKeysEqual(key1: K, key2: K): Boolean = when (key1) {
            key2 -> true
            else -> equiv(key1, key2)
        }

        @Suppress("UNCHECKED_CAST")
        operator
        fun <K, V> invoke(vararg pairs: Pair<K, V>): PersistentArrayMap<K, V> {
            for (i in pairs.indices)
                for (j in i + 1 until pairs.size)
                    if (areKeysEqual(pairs[i].first, pairs[j].first))
                        throw IllegalArgumentException("Duplicate key: $i")

            return ArrayMap(pairs as Array<Pair<K, V>>)
        }
    }
}
