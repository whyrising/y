package com.github.whyrising.y.map

import com.github.whyrising.y.associative.Associative
import com.github.whyrising.y.seq.IPersistentCollection
import com.github.whyrising.y.seq.ISeq

interface IPersistentMap<out K, out V> :
    Associative<K, V>,
    Iterable<Map.Entry<K, V>>,
    IPersistentCollection<Any?> {

    override fun assoc(key: @UnsafeVariance K, value: @UnsafeVariance V):
        IPersistentMap<K, V>

    fun assocNew(key: @UnsafeVariance K, value: @UnsafeVariance V):
        IPersistentMap<K, V>

    fun dissoc(key: @UnsafeVariance K): IPersistentMap<K, V>

    fun keyz(): ISeq<K>

    fun vals(): ISeq<V>
}
