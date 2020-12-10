import com.github.whyrising.y.Keyword
import com.github.whyrising.y.Symbol
import com.github.whyrising.y.concretions.map.m
import com.github.whyrising.y.k
import com.github.whyrising.y.keywordsCache
import com.github.whyrising.y.s
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeLessThan
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KeywordTest {
    @BeforeTest
    fun setUp() {
        (keywordsCache() as HashMap<Symbol, Any>).clear()
    }

    @ExperimentalStdlibApi
    @Test
    fun ctor() {
        val key = Keyword("a")

        assertEquals(Symbol("a"), key.symbol)
        assertEquals(Symbol("a").hasheq() + -0x61c88647, key.hashEq)
    }

    @ExperimentalStdlibApi
    @Test
    fun hasheq() {
        val key = Keyword("a")

        assertEquals(key.hashEq, key.hasheq())
    }

    @Test
    fun assertHashCode() {
        val key = Keyword("a")

        assertEquals(s("a").hashCode() + -0x61c88647, key.hashCode())
    }

    @Test
    fun `equals(other)`() {
        val key = Keyword("a")

        assertTrue { key == key }

        assertFalse { key.equals("A") }

        assertTrue { Keyword("a") == Keyword("a") }

        assertFalse { Keyword("a") == Keyword("b") }
    }

    @Test
    fun `compareTo(other)`() {
        Keyword("a").compareTo(Keyword("a")) shouldBeExactly 0
        Keyword("a").compareTo(Keyword("b")) shouldBeLessThan 0
        Keyword("b").compareTo(Keyword("a")) shouldBeGreaterThan 0
    }

    @Test
    fun `name property`() {
        val key = Keyword("a")

        assertEquals("a", key.name)
    }

    @Test
    fun `invoke(map)`() {
        val map = m(Keyword("a") to 1, Keyword("b") to 2, "c" to 3)

        assertEquals(1, Keyword("a")(map)!!)
        assertEquals(2, Keyword("b")(map)!!)
        assertNull(Keyword("c")(map))
        assertNull(Keyword("z")(map))
    }

    @Test
    fun `invoke(map, default)`() {
        val map1 = m(Keyword("a") to 1, Keyword("b") to 2)
        val map2 = mapOf(Keyword("a") to 1, Keyword("b") to 2)

        assertEquals(1, Keyword("a")(map1, -1)!!)
        assertEquals(2, Keyword("b")(map1, -1)!!)
        assertNull(Keyword("z")(map1, null))

        assertEquals(1, Keyword("a")(map2, -1)!!)
        assertEquals(2, Keyword("b")(map2, -1)!!)
        assertNull(Keyword("z")(map2, null))
        assertEquals(-1, Keyword("x")(map2, -1)!!)
    }

    @Test
    fun `assert same key instance`() {
        assertTrue { Keyword("a") === Keyword("a") }
    }

    @Test
    fun `k(a)`() {
        val key = k("a")

        assertEquals(key, Keyword("a"))
    }

    @Test
    fun `toString() should return print property`() {
        assertEquals(key.toString(), ":a")
        assertEquals(key.print, ":a")

        val k = Keyword("b")
        assertEquals(k.toString(), ":b")
        assertEquals(k.print, ":b")
    }

    companion object {
        val key = k("a")
    }
}
