package com.github.whyrising.y

sealed class PersistentArrayMap<out K, out V>(
    internal val array: Array<Pair<@UnsafeVariance K, @UnsafeVariance V>>
) : IPersistentMap<K, V> {

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


    internal object EmptyArrayMap : PersistentArrayMap<Nothing, Nothing>(
        emptyArray()
    ) {

        override fun toString(): String = "{}"
    }

    internal class ArrayMap<out K, out V>(
        internal val pairs: Array<Pair<@UnsafeVariance K, @UnsafeVariance V>>
    ) : PersistentArrayMap<K, V>(pairs)

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
