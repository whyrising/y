package com.github.whyrising.y

internal const val INIT_HASH_CODE = 0

abstract class APersistentVector<out E> : IPersistentVector<E> {
    internal var _hashCode: Int = INIT_HASH_CODE

    override fun toString(): String {
        var i = 0
        var str = ""
        while (i < count) {
            str += "${nth(i)} "
            i++
        }

        return "[${str.trim()}]"
    }

    override fun hashCode(): Int {
        var hash = _hashCode

        if (hash != INIT_HASH_CODE) return hash

        var index = 0
        while (index < count) {
            hash = 31 * hash + nth(index).hashCode()

            index++
        }
        _hashCode = hash

        return _hashCode
    }

    override fun length(): Int = count

    protected fun indexOutOfBounds(index: Int) = index >= count || index < 0

    override fun nth(index: Int, default: @UnsafeVariance E): E = when {
        indexOutOfBounds(index) -> default
        else -> nth(index)
    }
}
