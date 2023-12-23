package kt2ts.kspgenerator.utils

import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.readText
import org.json.JSONObject

// TODO[tmpl] the whole conf as a json
// TODO[tmpl] optional prettier executable in conf, instead of clientDirectory
data class Kt2TsConfiguration(
    val clientDirectory: Path,
    val srcDirectory: Path,
    // TODO naming directory with isn't a Path
    val generatedDirectory: String,
    // TODO bad naming
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
    val absoluteImport: Boolean,
    val absoluteImportPrefix: String?,
    val debugFile: File?
) {
    companion object {
        fun init(options: Map<String, String>): Kt2TsConfiguration {
            val destination =
                options["kt2ts:clientDirectory"]?.let { Paths.get(it) }
                    ?: throw IllegalArgumentException()
            return Kt2TsConfiguration(
                clientDirectory = destination,
                srcDirectory = destination.resolve(options["kt2ts:srcDirectory"] ?: "src"),
                generatedDirectory = options["kt2ts:generatedDirectory"] ?: "generated",
                dropPackage = options["kt2ts:dropPackage"] ?: "",
                mappings =
                    options["kt2ts:mappings"]?.let {
                        Paths.get(it).readText().let {
                            JSONObject(it).toMap().mapValues { e -> e.value.toString() }
                        }
                    } ?: emptyMap(),
                nominalStringMappings =
                    options["kt2ts:nominalStringMappings"]?.split("|")?.toSet() ?: emptySet(),
                nominalStringImport = options["kt2ts:nominalStringImport"],
                // TODO[tmpl]
                interfaceAsTypes = emptySet(),
                // TODO use instead of temp dir ?
                absoluteImport = options["kt2ts:absoluteImport"] == "true",
                absoluteImportPrefix = options["kt2ts:absoluteImportPrefix"],
                debugFile = options["kt2ts:debugFile"]?.let { Paths.get(it).toFile() })
        }
    }
}
