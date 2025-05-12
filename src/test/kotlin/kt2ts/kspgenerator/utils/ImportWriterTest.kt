package kt2ts.kspgenerator.utils

import java.nio.file.Paths
import kt2ts.kspgenerator.utils.ImportWriter.relativePath
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class ImportWriterTest {

    val testConfiguration =
        Kt2TsConfiguration(
            clientDirectory = Paths.get("/client"),
            srcDirectory = Paths.get("/client/src"),
            generatedDirectory = "generated",
            dropPackage = "com.kttswebapptemplate",
            mappings = emptyMap(),
            nominalStringMappings = emptySet(),
            nominalStringImport = null,
            interfaceAsTypes = emptySet(),
            mapClass = null,
            mapClassFile = null,
            nodeBinary = null,
            prettierDependencyInstall = null,
            prettierBinary = null,
            absoluteImport = false,
            absoluteImportPrefix = null,
            debugFile = null,
        )

    @Test
    fun `check relative path in same path`() {
        assertEquals(
            "./target-file",
            relativePath(
                "/root/subpath1/subpath2/target-file.ts",
                "/root/subpath1/subpath2/origin-file.ts",
                testConfiguration,
            ),
        )
    }

    @Test
    fun `check relative path with subpath`() {
        assertEquals(
            "../subpath2.1/target-file",
            relativePath(
                "/root/subpath1/subpath2.1/target-file.ts",
                "/root/subpath1/subpath2.2/origin-file.ts",
                testConfiguration,
            ),
        )
    }

    @Test
    fun `check relative path with deep subpath`() {
        assertEquals(
            "../../subpath1.1/subpath2.1/target-file",
            relativePath(
                "/root/subpath1.1/subpath2.1/target-file.ts",
                "/root/subpath1.2/subpath2.2/origin-file.ts",
                testConfiguration,
            ),
        )
    }
}
