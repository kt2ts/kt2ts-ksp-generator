package kt2ts.kspgenerator.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class ClassMapperTest {

    @Test
    fun `exact mapping wins over patterns`() {
        val mappings =
            mapOf("lite.common.domain.UserFileId" to "exact", "lite.common.domain.*Id" to "glob")
        assertEquals("exact", ClassMapper.lookupMapping("lite.common.domain.UserFileId", mappings))
    }

    @Test
    fun `single star matches within a package segment, not across dots`() {
        val mappings = mapOf("lite.common.domain.*Id" to "ids")
        assertEquals("ids", ClassMapper.lookupMapping("lite.common.domain.UserFileId", mappings))
        assertEquals("ids", ClassMapper.lookupMapping("lite.common.domain.DeviceId", mappings))
        assertNull(
            ClassMapper.lookupMapping("lite.common.domain.sub.UserFileId", mappings),
            "single * must not span dots",
        )
    }

    @Test
    fun `double star matches across dots`() {
        val mappings = mapOf("lite.common.**Id" to "ids")
        assertEquals(
            "ids",
            ClassMapper.lookupMapping("lite.common.domain.sub.UserFileId", mappings),
        )
    }

    @Test
    fun `longest matching pattern wins`() {
        val mappings =
            mapOf("lite.common.domain.*" to "broad", "lite.common.domain.*Id" to "specific")
        assertEquals(
            "specific",
            ClassMapper.lookupMapping("lite.common.domain.UserFileId", mappings),
        )
    }

    @Test
    fun `no match returns null`() {
        val mappings = mapOf("lite.common.domain.*Id" to "ids")
        assertNull(ClassMapper.lookupMapping("lite.web.domain.Foo", mappings))
        assertNull(ClassMapper.lookupMapping("lite.common.domain.Pdl", mappings))
    }

    @Test
    fun `patternMatches treats dots as literal`() {
        assertTrue(ClassMapper.patternMatches("java.util.UUID", "java.util.UUID"))
        assertFalse(ClassMapper.patternMatches("java.util.UUID", "javaXutil.UUID"))
    }
}
