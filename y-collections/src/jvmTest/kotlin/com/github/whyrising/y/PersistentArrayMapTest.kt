package com.github.whyrising.y

import com.github.whyrising.y.PersistentArrayMap.ArrayMap
import com.github.whyrising.y.PersistentArrayMap.EmptyArrayMap
import io.kotest.assertions.throwables.shouldThrowExactly
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs

class PersistentArrayMapTest : FreeSpec({
    "ArrayMap" - {
        "invoke() should return EmptyArrayMap" {
            val emptyMap = PersistentArrayMap<String, Int>()

            emptyMap shouldBeSameInstanceAs EmptyArrayMap
            emptyMap.array.size shouldBeExactly 0
        }

        "invoke(pairs)" - {
            "it should return an ArrayMap" {
                val array = arrayOf("a" to 1, "b" to 2, "c" to 3)

                val map = PersistentArrayMap(*array)
                val pairs = map.array

                pairs shouldBe array
            }

            "when duplicate keys, it should throw an exception" {
                shouldThrowExactly<IllegalArgumentException> {
                    PersistentArrayMap("a" to 1, "b" to 2, "b" to 3)
                }
                shouldThrowExactly<IllegalArgumentException> {
                    PersistentArrayMap("a" to 1, "a" to 2, "b" to 3)
                }
                shouldThrowExactly<IllegalArgumentException> {
                    PersistentArrayMap("a" to 1, "b" to 2, "a" to 3)
                }

                shouldThrowExactly<IllegalArgumentException> {
                    PersistentArrayMap(1L to "a", 1 to "b")
                }
            }
        }

        "assoc(key, val)" - {
            "when map is empty, it should add the new entry" {
                val map = PersistentArrayMap<String, Int>()

                val newMap = map.assoc("a", 1) as ArrayMap<String, Int>
                val pairs = newMap.pairs

                pairs[0].first shouldBe "a"
                pairs[0].second shouldBe 1
            }

            "when the key is new, it should add it to the map" - {
                "when size < threshold, it should return a PersistentArrayMap" {
                    val array = arrayOf("a" to 1, "b" to 2, "c" to 3)
                    val map = PersistentArrayMap(*array)

                    val newMap = map.assoc("d", 4) as ArrayMap<String, Int>
                    val pairs = newMap.pairs

                    pairs[0].first shouldBe "a"
                    pairs[0].second shouldBe 1

                    pairs[3].first shouldBe "d"
                    pairs[3].second shouldBe 4
                }

                "when size >= THRESHOLD, it should return PersistentHashMap" {
                    // TODO : when PersistentHashMap is  implemented
                }
            }

            """when map already has the key and different value,
               it should replace it in a new map""" {
                val key = 2
                val value = "78"
                val array = arrayOf(1L to "1", 2L to "2", 3 to "3")
                val map = PersistentArrayMap(*array)

                val newMap = map.assoc(key, value) as ArrayMap<Any, String>
                val pairs = newMap.pairs

                pairs.size shouldBeExactly array.size

                array[1].first shouldBe key
                array[1].second shouldBe "2"

                pairs[0].first shouldBe 1L
                pairs[0].second shouldBe "1"

                pairs[1].first shouldBe key
                pairs[1].second shouldBe value

                pairs[2].first shouldBe 3
                pairs[2].second shouldBe "3"
            }

            """when map already has the key and same value,
               it should return the same map""" {
                val key = 2
                val value = "2"
                val array = arrayOf(1L to "1", 2L to "2", 3 to "3")
                val map = PersistentArrayMap(*array)

                val newMap = map.assoc(key, value) as ArrayMap<Any, String>

                newMap shouldBeSameInstanceAs map
            }
        }

        "assocNew(key, val)" - {
            "when map already has the key, it should throw" {
                val value = "78"
                val array = arrayOf(1L to "1", 2L to "2", 3 to "3")
                val map = PersistentArrayMap(*array)

                shouldThrowExactly<RuntimeException> {
                    map.assocNew(2, value)
                }.message shouldBe "The key 2 is already present."
            }

            "when new key, it should add the association to the new map" {
                val key = 4
                val value = "4"
                val array = arrayOf(1L to "1", 2L to "2", 3 to "3")
                val map = PersistentArrayMap(*array)

                val newMap = map.assocNew(key, value) as ArrayMap<Any, String>
                val pairs = newMap.pairs

                pairs.size shouldBeExactly array.size + 1

                pairs[0].first shouldBe 1L
                pairs[0].second shouldBe "1"

                pairs[2].first shouldBe 3
                pairs[2].second shouldBe "3"

                pairs[array.size].first shouldBe key
                pairs[array.size].second shouldBe value
            }

            "when size >= THRESHOLD, it should return PersistentHashMap" {
                // TODO : when PersistentHashMap is  implemented
            }
        }

        "dissoc(key)" - {
            "when key doesn't exit, it should return the same instance" {
                val array = arrayOf(1L to "1", 2L to "2", 3 to "3")
                val map = PersistentArrayMap(*array)

                map.dissoc(9) shouldBeSameInstanceAs map
            }

            "when key exists and size is 1, it should return the empty map" {
                val map = PersistentArrayMap(2L to "2")

                map.dissoc(2) shouldBeSameInstanceAs EmptyArrayMap
            }

            "when key exists, it should return a new map without that key" {
                val array = arrayOf(1L to "1", 2L to "2", 3 to "3")
                val map = PersistentArrayMap(*array)

                val newMap = map.dissoc(2) as PersistentArrayMap<Any?, String>
                val pairs = newMap.array

                pairs.size shouldBeExactly array.size - 1
                pairs[0] shouldBe array[0]
                pairs[1] shouldBe array[2]
            }
        }

        "containsKey(key)" {
            val array = arrayOf("a" to 1, "b" to 2, "c" to 3)
            val map = PersistentArrayMap(*array)

            map.containsKey("a").shouldBeTrue()
            map.containsKey("b").shouldBeTrue()

            map.containsKey("d").shouldBeFalse()
        }

        "entryAt(key)" - {
            val array = arrayOf("a" to 1, "b" to 2, "c" to 3)
            val map = PersistentArrayMap(*array)

            "when key doesn't exit, it should return null" {
                map.entryAt("d").shouldBeNull()
            }

            "when key does exist, it should return a MapEntry" {
                val mapEntry = map.entryAt("a") as MapEntry<String, Int>

                mapEntry.key shouldBe "a"
                mapEntry.value shouldBe 1
            }
        }

        "valAt(key, default)" - {
            val array = arrayOf("a" to 1, "b" to 2, "c" to 3)
            val map = PersistentArrayMap(*array)

            "when key exists, it should return the assoc value" {
                map.valAt("a", -1) shouldBe 1
            }

            "when key doesn't exist, it should return the default value" {
                map.valAt("z", -1) shouldBe -1
            }
        }

        "valAt(key)" - {
            val array = arrayOf("a" to 1, "b" to 2, "c" to 3)
            val map = PersistentArrayMap(*array)

            "when key exists, it should return the assoc value" {
                map.valAt("a") shouldBe 1
            }

            "when key doesn't exist, it should return the default value" {
                map.valAt("z").shouldBeNull()
            }
        }

        "count()" {
            val array = arrayOf("a" to 1, "b" to 2, "c" to 3)

            PersistentArrayMap<String, Int>().count shouldBeExactly 0
            PersistentArrayMap(*array).count shouldBeExactly array.size
        }

        "empty()" {
            val array = arrayOf("a" to 1, "b" to 2, "c" to 3)

            PersistentArrayMap(*array).empty() shouldBeSameInstanceAs
                EmptyArrayMap
        }

        @Suppress("UNCHECKED_CAST")
        "conj(entry)" - {
            val array = arrayOf("a" to 1, "b" to 2, "c" to 3)
            val map = PersistentArrayMap(*array)

            "when entry is a Map.Entry, it should call assoc() on it" {
                val newMap = map.conj(MapEntry("a", 99))
                    as ArrayMap<String, Int>

                newMap.count shouldBeExactly array.size
                newMap.array[0].second shouldBeExactly 99
                newMap.array[1].second shouldBeExactly 2
                newMap.array[2].second shouldBeExactly 3
            }

            "when entry is a IPersistentVector" - {
                "when count != 2, it should throw" {
                    shouldThrowExactly<IllegalArgumentException> {
                        map.conj(v("a", 99, 75))
                    }.message shouldBe
                        "Vector [a 99 75] count should be 2 to conj in a map"
                }

                "when count == 2, it should call assoc() on it" {
                    val newMap = map.conj(v("a", 99))
                        as ArrayMap<String, Int>

                    newMap.count shouldBeExactly array.size
                    newMap.array[0].second shouldBeExactly 99
                    newMap.array[1].second shouldBeExactly 2
                    newMap.array[2].second shouldBeExactly 3
                }
            }

            "when entry is null, it should return this" {
                map.conj(null) shouldBeSameInstanceAs map
            }

            "when entry is a seq of MapEntry" - {
                "when an element is not a MapEntry, it should throw" {
                    shouldThrowExactly<IllegalArgumentException> {
                        map.conj(l(MapEntry("x", 42), "item"))
                    }.message shouldBe
                        "All elements of the seq must be of type Map.Entry" +
                        " to conj: item"
                }

                "when all elements are MapEntry, it should assoc() all" {
                    val entries = l(MapEntry("x", 42), MapEntry("y", 47))

                    val newMap = map.conj(entries) as ArrayMap<String, Int>

                    newMap.count shouldBeExactly array.size + entries.count
                }
            }
        }
    }

    "EmptyArrayMap" - {
        "toString() should return `{}`" {
            PersistentArrayMap<String, Int>().toString() shouldBe "{}"
        }
    }
})