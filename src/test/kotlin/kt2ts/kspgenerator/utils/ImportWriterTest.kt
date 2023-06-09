package kt2ts.kspgenerator.utils

import kt2ts.kspgenerator.utils.ImportWriter.relativePath
import java.nio.file.Paths
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class ImportWriterTest {

    val testConfiguration =
        Kt2TsConfiguration(
            Paths.get("/client"),
            Paths.get("/client/src"),
            "generated",
            "com.kttswebapptemplate",
            emptyMap(),
            emptySet(),
            null,
            emptySet(),
            null)

    @Test
    fun `check relative path in same path`() {
        assertEquals(
            "./target-file",
            relativePath(
                "/root/subpath1/subpath2/target-file.ts",
                "/root/subpath1/subpath2/origin-file.ts",
                testConfiguration))
    }

    @Test
    fun `check relative path with subpath`() {
        assertEquals(
            "../subpath2.1/target-file",
            relativePath(
                "/root/subpath1/subpath2.1/target-file.ts",
                "/root/subpath1/subpath2.2/origin-file.ts",
                testConfiguration))
    }

    @Test
    fun `check relative path with deep subpath`() {
        assertEquals(
            "../../subpath1.1/subpath2.1/target-file",
            relativePath(
                "/root/subpath1.1/subpath2.1/target-file.ts",
                "/root/subpath1.2/subpath2.2/origin-file.ts",
                testConfiguration))
    }
}
