package kt2ts.kspgenerator.utils

import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.readText
import org.json.JSONArray
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
    val mapClass: String?,
    val mapClassFile: String?,
    val nodeBinary: String?,
    val prettierDependencyInstall: String?,
    val prettierBinary: String?,
    val absoluteImport: Boolean,
    val absoluteImportPrefix: String?,
    // [doc] when set, write a kt2ts-manifest.json after generation listing every generated class
    // and its absolute TS import path. Consumer modules can read this manifest to auto-resolve
    // cross-module @GenerateTypescript types without listing them in kt-to-ts-mappings.json.
    val manifestOutput: File?,
    val debugFile: File?,
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
                    readManifests(options["kt2ts:manifests"]) +
                        (options["kt2ts:mappings"]?.let { readMappingsFile(Paths.get(it)) }
                            ?: emptyMap()),
                nominalStringMappings =
                    options["kt2ts:nominalStringMappings"]?.split("|")?.toSet() ?: emptySet(),
                nominalStringImport = options["kt2ts:nominalStringImport"],
                // TODO[tmpl]
                interfaceAsTypes = emptySet(),
                // TODO use instead of temp dir ?
                mapClass = options["kt2ts:mapClass"],
                mapClassFile = options["kt2ts:mapClassFile"],
                nodeBinary = options["kt2ts:nodeBinary"],
                prettierDependencyInstall = options["kt2ts:prettierDependencyInstall"],
                prettierBinary = options["kt2ts:prettierBinary"],
                absoluteImport = options["kt2ts:absoluteImport"] == "true",
                absoluteImportPrefix = options["kt2ts:absoluteImportPrefix"],
                manifestOutput = options["kt2ts:manifestOutput"]?.let { Paths.get(it).toFile() },
                debugFile = options["kt2ts:debugFile"]?.let { Paths.get(it).toFile() },
            )
        }

        // [doc] reads zero or more kt2ts-manifest.json files (comma-separated paths) and returns
        // their combined class → import path map. Missing files are skipped with a warning so a
        // first build (before the producer has emitted its manifest) does not hard-fail; missing
        // entries will surface later as a normal "type not found" error. When several manifests
        // are passed, later ones override earlier ones.
        internal fun readManifests(value: String?): Map<String, String> {
            if (value.isNullOrBlank()) return emptyMap()
            return value
                .split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .fold(emptyMap()) { acc, p -> acc + readManifestFile(Paths.get(p)) }
        }

        private fun readManifestFile(path: Path): Map<String, String> {
            if (!path.toFile().exists()) {
                System.err.println(
                    "[kt2ts] manifest file not found, skipping: $path " +
                        "(this is expected on a first build before the producer module has run)"
                )
                return emptyMap()
            }
            val json = JSONObject(path.readText())
            val classes = json.optJSONObject("classes") ?: return emptyMap()
            return classes.keySet().associateWith { classes.getString(it) }
        }

        // [doc] reads a kt-to-ts-mappings.json. Supports a reserved `extends` key (string or array
        // of strings) whose value is interpreted as path(s) relative to the file, which are loaded
        // recursively. Later (own) entries override earlier (extended) ones; in an extends array,
        // later files override earlier ones. Cycles raise IllegalArgumentException.
        internal fun readMappingsFile(path: Path): Map<String, String> =
            readMappingsFile(path, mutableSetOf())

        private fun readMappingsFile(path: Path, seen: MutableSet<Path>): Map<String, String> {
            val canonical = path.toRealPath()
            if (!seen.add(canonical)) {
                throw IllegalArgumentException(
                    "Cyclic `extends` in kt-to-ts mappings, file already visited: $canonical"
                )
            }
            val json = JSONObject(canonical.readText())
            val extendedPaths: List<Path> =
                when (val v = json.opt("extends")) {
                    null -> emptyList()
                    is String -> listOf(canonical.parent.resolve(v))
                    is JSONArray ->
                        (0 until v.length()).map { i -> canonical.parent.resolve(v.getString(i)) }
                    else ->
                        throw IllegalArgumentException(
                            "`extends` in $canonical must be a string or array of strings"
                        )
                }
            val inherited =
                extendedPaths.fold(emptyMap<String, String>()) { acc, p ->
                    acc + readMappingsFile(p, seen)
                }
            val own =
                json.toMap().filterKeys { it != "extends" }.mapValues { e -> e.value.toString() }
            return inherited + own
        }
    }
}
