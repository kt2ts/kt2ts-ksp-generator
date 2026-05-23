package kt2ts.kspgenerator.utils

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

internal class Kt2TsConfigurationTest {

    private fun write(dir: Path, name: String, content: String): Path {
        val p = dir.resolve(name)
        p.writeText(content)
        return p
    }

    @Test
    fun `reads a flat mappings file`(@TempDir tmp: Path) {
        val p =
            write(
                tmp,
                "a.json",
                """{"java.time.Instant": "domain/datetime.ts", "java.util.UUID": "domain/uuid.ts"}""",
            )
        val mappings = Kt2TsConfiguration.readMappingsFile(p)
        assertEquals(
            mapOf(
                "java.time.Instant" to "domain/datetime.ts",
                "java.util.UUID" to "domain/uuid.ts",
            ),
            mappings,
        )
    }

    @Test
    fun `extends a sibling file as a string`(@TempDir tmp: Path) {
        write(tmp, "base.json", """{"java.util.UUID": "shared/uuid.ts"}""")
        val p =
            write(
                tmp,
                "child.json",
                """{"extends": "base.json", "java.time.Instant": "domain/datetime.ts"}""",
            )
        val mappings = Kt2TsConfiguration.readMappingsFile(p)
        assertEquals(
            mapOf(
                "java.util.UUID" to "shared/uuid.ts",
                "java.time.Instant" to "domain/datetime.ts",
            ),
            mappings,
        )
    }

    @Test
    fun `child mapping overrides extended one`(@TempDir tmp: Path) {
        write(tmp, "base.json", """{"java.util.UUID": "shared/uuid.ts"}""")
        val p =
            write(
                tmp,
                "child.json",
                """{"extends": "base.json", "java.util.UUID": "local/uuid.ts"}""",
            )
        val mappings = Kt2TsConfiguration.readMappingsFile(p)
        assertEquals(mapOf("java.util.UUID" to "local/uuid.ts"), mappings)
    }

    @Test
    fun `extends an array of files, later overrides earlier`(@TempDir tmp: Path) {
        write(
            tmp,
            "a.json",
            """{"java.util.UUID": "from-a.ts", "java.time.Instant": "from-a.ts"}""",
        )
        write(tmp, "b.json", """{"java.util.UUID": "from-b.ts"}""")
        val p = write(tmp, "child.json", """{"extends": ["a.json", "b.json"]}""")
        val mappings = Kt2TsConfiguration.readMappingsFile(p)
        assertEquals(
            mapOf("java.util.UUID" to "from-b.ts", "java.time.Instant" to "from-a.ts"),
            mappings,
        )
    }

    @Test
    fun `extends transitively`(@TempDir tmp: Path) {
        write(tmp, "grand.json", """{"java.util.UUID": "grand.ts"}""")
        write(tmp, "parent.json", """{"extends": "grand.json", "java.time.Instant": "parent.ts"}""")
        val p = write(tmp, "child.json", """{"extends": "parent.json"}""")
        val mappings = Kt2TsConfiguration.readMappingsFile(p)
        assertEquals(
            mapOf("java.util.UUID" to "grand.ts", "java.time.Instant" to "parent.ts"),
            mappings,
        )
    }

    @Test
    fun `extends from a subdirectory resolves relative to file`(@TempDir tmp: Path) {
        Files.createDirectory(tmp.resolve("sub"))
        write(tmp, "sub/base.json", """{"java.util.UUID": "base.ts"}""")
        val p = write(tmp, "child.json", """{"extends": "sub/base.json"}""")
        val mappings = Kt2TsConfiguration.readMappingsFile(p)
        assertEquals(mapOf("java.util.UUID" to "base.ts"), mappings)
    }

    @Test
    fun `direct cycle raises`(@TempDir tmp: Path) {
        val p = write(tmp, "self.json", """{"extends": "self.json"}""")
        assertThrows(IllegalArgumentException::class.java) {
            Kt2TsConfiguration.readMappingsFile(p)
        }
    }

    @Test
    fun `manifest file is read into a flat mapping`(@TempDir tmp: Path) {
        val p =
            write(
                tmp,
                "kt2ts-manifest.json",
                """{
                  "version": 1,
                  "classes": {
                    "lite.common.domain.Pdl": "@lite-eco/shared/generated/domain/enedis.generated",
                    "lite.common.domain.ApiPartnerId": "@lite-eco/shared/generated/domain/ids.generated"
                  }
                }""",
            )
        val mappings = Kt2TsConfiguration.readManifests(p.toString())
        assertEquals(
            mapOf(
                "lite.common.domain.Pdl" to "@lite-eco/shared/generated/domain/enedis.generated",
                "lite.common.domain.ApiPartnerId" to
                    "@lite-eco/shared/generated/domain/ids.generated",
            ),
            mappings,
        )
    }

    @Test
    fun `several manifest files merge, later overrides earlier`(@TempDir tmp: Path) {
        val a = write(tmp, "a.json", """{"classes": {"X": "from-a", "Y": "from-a"}}""")
        val b = write(tmp, "b.json", """{"classes": {"Y": "from-b"}}""")
        val mappings = Kt2TsConfiguration.readManifests("$a, $b")
        assertEquals(mapOf("X" to "from-a", "Y" to "from-b"), mappings)
    }

    @Test
    fun `missing manifest is skipped, does not throw`(@TempDir tmp: Path) {
        val missing = tmp.resolve("does-not-exist.json")
        val mappings = Kt2TsConfiguration.readManifests(missing.toString())
        assertEquals(emptyMap<String, String>(), mappings)
    }

    @Test
    fun `null or blank manifests option yields empty map`() {
        assertEquals(emptyMap<String, String>(), Kt2TsConfiguration.readManifests(null))
        assertEquals(emptyMap<String, String>(), Kt2TsConfiguration.readManifests(""))
        assertEquals(emptyMap<String, String>(), Kt2TsConfiguration.readManifests("   "))
    }

    @Test
    fun `indirect cycle raises`(@TempDir tmp: Path) {
        write(tmp, "a.json", """{"extends": "b.json"}""")
        val p = write(tmp, "b.json", """{"extends": "a.json"}""")
        assertThrows(IllegalArgumentException::class.java) {
            Kt2TsConfiguration.readMappingsFile(p)
        }
    }
}
