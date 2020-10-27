package com.github.whyrising.y

interface IPersistentMap<out K, out V> {
    fun assoc(key: @UnsafeVariance K, value: @UnsafeVariance V):
        IPersistentMap<K, V>
}
