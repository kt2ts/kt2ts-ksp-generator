package kt2ts.kspgenerator.utils

import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.readText
import org.json.JSONObject

// TODO[tmpl] final command for prettier
data class Kt2TsConfiguration(
    val clientDirectory: Path,
    val srcDirectory: Path,
    // TODO naming directory with isn't a Path
    val generatedDirectory: String,
    val dropPackage: String,
    // for classes from the jdk that will be "emulated" in js
    // => Duration, LocalDate, etc...
    // if missing, print a warning
    // and use any in generated code? or a nominalString
    // create a @Kt2TsConfiguration ?
    // => not simple, project code can't be run here
    // try to extend from gradle ?
    // TODO naming
    val mappings: Map<String, String>,
    // config, or an annotation
    val nominalStringMappings: Set<String>,
    // where is my nominal-string. If not present but needed, generate it
    val nominalStringImport: String?,
    // interfaces i want as type = all subtypes
    val interfaceAsTypes: Set<String>,
    val debugFile: File?
) {
    companion object {
        fun init(options: Map<String, String>): Kt2TsConfiguration {
            val destination =
                options["kt2Ts:clientDirectory"]?.let { Paths.get(it) }
                    ?: throw IllegalArgumentException()
            return Kt2TsConfiguration(
                clientDirectory = destination,
                srcDirectory = destination.resolve(options["kt2Ts:srcDirectory"] ?: "src"),
                generatedDirectory = options["kt2Ts:generatedDirectory"] ?: "generated",
                dropPackage = options["kt2Ts:dropPackage"] ?: "",
                mappings =
                    options["kt2Ts:mappings"]?.let {
                        Paths.get(it).readText().let {
                            JSONObject(it).toMap().mapValues { e -> e.value.toString() }
                        }
                    }
                        ?: emptyMap(),
                nominalStringMappings =
                    options["kt2Ts:nominalStringMappings"]?.let { it.split("|").toSet() }
                        ?: emptySet(),
                nominalStringImport = options["kt2Ts:nominalStringImport"],
                // TODO[tmpl]
                interfaceAsTypes = emptySet(),
                // TODO use instead of temp dir ?
                debugFile = options["kt2Ts:debugFile"]?.let { Paths.get(it).toFile() })
        }
    }
}
