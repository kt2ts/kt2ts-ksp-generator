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

    // -------------------- kt2ts:config single-file --------------------

    @Test
    fun `config file populates all settings`(@TempDir tmp: Path) {
        write(
            tmp,
            "kt2ts.json",
            """
            {
              "clientDirectory": ".",
              "dropPackage": "lite.web",
              "absoluteImport": true,
              "absoluteImportPrefix": "@lite-eco/web",
              "nestedClassSeparator": "_",
              "discriminatorProperty": "_type",
              "mapClass": "Record",
              "nominalStringMappings": ["lite.common.domain.LiteId"],
              "nominalStringImport": "@lite-eco/shared/utils/nominal-class",
              "mappings": { "java.util.UUID": "domain/uuid.ts" }
            }
            """,
        )
        val cfg =
            Kt2TsConfiguration.init(mapOf("kt2ts:config" to tmp.resolve("kt2ts.json").toString()))
        assertEquals(tmp.toRealPath(), cfg.clientDirectory.toRealPath())
        assertEquals("lite.web", cfg.dropPackage)
        assertEquals(true, cfg.absoluteImport)
        assertEquals("@lite-eco/web", cfg.absoluteImportPrefix)
        assertEquals("_", cfg.nestedClassSeparator)
        assertEquals("_type", cfg.discriminatorProperty)
        assertEquals("Record", cfg.mapClass)
        assertEquals(setOf("lite.common.domain.LiteId"), cfg.nominalStringMappings)
        assertEquals("@lite-eco/shared/utils/nominal-class", cfg.nominalStringImport)
        assertEquals(mapOf("java.util.UUID" to "domain/uuid.ts"), cfg.mappings)
    }

    @Test
    fun `config paths are resolved relative to the config file`(@TempDir tmp: Path) {
        Files.createDirectories(tmp.resolve("apps/web"))
        Files.createDirectories(tmp.resolve("packages/shared"))
        write(
            tmp,
            "apps/web/kt2ts.json",
            """
            {
              "clientDirectory": ".",
              "manifestOutput": "build/kt2ts-manifest.json",
              "manifests": ["../../packages/shared/kt2ts-manifest.json"]
            }
            """,
        )
        write(
            tmp,
            "packages/shared/kt2ts-manifest.json",
            """{"classes": {"lite.common.domain.Pdl": "@lite-eco/shared/generated/domain/enedis.generated"}}""",
        )
        val cfg =
            Kt2TsConfiguration.init(
                mapOf("kt2ts:config" to tmp.resolve("apps/web/kt2ts.json").toString())
            )
        val realTmp = tmp.toRealPath()
        assertEquals(realTmp.resolve("apps/web"), cfg.clientDirectory)
        assertEquals(
            realTmp.resolve("apps/web/build/kt2ts-manifest.json").normalize().toString(),
            cfg.manifestOutput?.absolutePath,
        )
        assertEquals(
            mapOf("lite.common.domain.Pdl" to "@lite-eco/shared/generated/domain/enedis.generated"),
            cfg.mappings,
        )
    }

    @Test
    fun `config extends merges scalars and deep-merges mappings`(@TempDir tmp: Path) {
        write(
            tmp,
            "defaults.json",
            """
            {
              "clientDirectory": ".",
              "absoluteImport": true,
              "mapClass": "Record",
              "mappings": {
                "java.util.UUID": "domain/uuid.ts",
                "java.time.Instant": "domain/datetime.ts"
              }
            }
            """,
        )
        write(
            tmp,
            "child.json",
            """
            {
              "extends": "defaults.json",
              "dropPackage": "lite.web",
              "mappings": {
                "java.time.Instant": "shared/date.ts",
                "lite.web.domain.Foo": "interfaces.ts"
              }
            }
            """,
        )
        val cfg =
            Kt2TsConfiguration.init(mapOf("kt2ts:config" to tmp.resolve("child.json").toString()))
        assertEquals("lite.web", cfg.dropPackage)
        assertEquals(true, cfg.absoluteImport)
        assertEquals("Record", cfg.mapClass)
        assertEquals(
            mapOf(
                "java.util.UUID" to "domain/uuid.ts",
                "java.time.Instant" to "shared/date.ts",
                "lite.web.domain.Foo" to "interfaces.ts",
            ),
            cfg.mappings,
        )
    }

    @Test
    fun `KSP arg overrides config file value`(@TempDir tmp: Path) {
        write(tmp, "kt2ts.json", """{"clientDirectory": ".", "dropPackage": "lite.web"}""")
        val cfg =
            Kt2TsConfiguration.init(
                mapOf(
                    "kt2ts:config" to tmp.resolve("kt2ts.json").toString(),
                    "kt2ts:dropPackage" to "lite.override",
                )
            )
        assertEquals("lite.override", cfg.dropPackage)
    }

    @Test
    fun `config mappings can point to an external file`(@TempDir tmp: Path) {
        write(tmp, "mappings.json", """{"java.util.UUID": "domain/uuid.ts"}""")
        write(tmp, "kt2ts.json", """{"clientDirectory": ".", "mappings": "mappings.json"}""")
        val cfg =
            Kt2TsConfiguration.init(mapOf("kt2ts:config" to tmp.resolve("kt2ts.json").toString()))
        assertEquals(mapOf("java.util.UUID" to "domain/uuid.ts"), cfg.mappings)
    }

    @Test
    fun `config extends cycle raises`(@TempDir tmp: Path) {
        write(tmp, "a.json", """{"extends": "b.json"}""")
        write(tmp, "b.json", """{"extends": "a.json"}""")
        assertThrows(IllegalArgumentException::class.java) {
            Kt2TsConfiguration.loadConfigFile(tmp.resolve("a.json"))
        }
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
