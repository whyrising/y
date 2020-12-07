package com.github.whyrising.y

import com.github.whyrising.y.concretions.map.m
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.github.whyrising.y.concretions.map.PersistentArrayMap as PAM

@ExperimentalStdlibApi
class KeywordTest : FreeSpec({
    beforeTest {
        (keywordsCache() as HashMap<Symbol, Any>).clear()
    }

    "concurrency" {
        val counter = atomic(0)

        withContext(Dispatchers.Default) {
            massiveRun2 {
                Keyword("${counter.incrementAndGet()}")
            }
        }

        counter.getAndSet(0)
        System.gc()

        withContext(Dispatchers.Default) {
            massiveRun2 {
                Keyword("${counter.incrementAndGet()}")
            }
        }

        keywordsCache().size shouldBeExactly 100000
    }

    "when gc collected a keyword, it should remove it from the cache" {
        Keyword("a")

        System.gc()

        Keyword("a")
    }

    "Serialization" - {
        val map = m(k("a") to 1)
        val mapSerialized = "{\"a\":1}"
        val aStr = "\"a\""

        "serialize" {
            Json.encodeToString(k("a")) shouldBe aStr

            Json.encodeToString(map) shouldBe mapSerialized
        }

        "deserialize" {
            Json.decodeFromString<Keyword>(aStr) shouldBeSameInstanceAs k("a")
            Json.decodeFromString<PAM<Keyword, Int>>(mapSerialized) shouldBe map
        }
    }
})

private suspend fun massiveRun2(action: suspend () -> Unit) {
    val n = 100
    val times = 1000
    coroutineScope {
        repeat(n) {
            launch { repeat(times) { action() } }
        }
    }
}
