package kt2ts.kspgenerator.utils

import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.readText
import org.json.JSONArray
import org.json.JSONObject

data class Kt2TsConfiguration(
    val clientDirectory: Path,
    val srcDirectory: Path,
    val generatedDirectory: String,
    val dropPackage: String,
    val mappings: Map<String, String>,
    val nominalStringMappings: Set<String>,
    val nominalStringImport: String?,
    val interfaceAsTypes: Set<String>,
    val mapClass: String?,
    val mapClassFile: String?,
    val nodeBinary: String?,
    val prettierDependencyInstall: String?,
    val prettierBinary: String?,
    val absoluteImport: Boolean,
    val absoluteImportPrefix: String?,
    // [doc] separator used between an outer class name and its nested class in *TS type names*.
    // Defaults to `$` to preserve historical output. The Jackson discriminator value emitted
    // alongside (`objectType: '…'`) keeps the `$` form regardless, since that is what Jackson
    // Id.NAME produces.
    val nestedClassSeparator: String,
    // [doc] property name used as the Jackson polymorphic discriminator on sealed subtypes.
    // Defaults to "objectType" to match historical output; configure to match a different
    // @JsonTypeInfo(property = "…") used in the Kotlin source (e.g. "_type").
    val discriminatorProperty: String,
    // [doc] when set, write a kt2ts-manifest.json after generation listing every generated class
    // and its absolute TS import path. Consumer modules can read this manifest to auto-resolve
    // cross-module @GenerateTypescript types without listing them in kt-to-ts-mappings.json.
    val manifestOutput: File?,
    val debugFile: File?,
) {
    companion object {

        // [doc] The whole configuration can be expressed via a single JSON file, pointed at by
        // the `kt2ts:config` KSP arg, instead of repeating ~13 `arg("kt2ts:...", ...)` lines in
        // every consumer's build.gradle.kts. Paths inside the JSON are resolved relative to the
        // file. The file may `extends` other JSON files for shared defaults. KSP args, if set,
        // still win over config values (so a single setting can be overridden locally).
        fun init(options: Map<String, String>): Kt2TsConfiguration {
            val config: LoadedConfig =
                options["kt2ts:config"]?.let { loadConfigFile(Paths.get(it)) }
                    ?: LoadedConfig(baseDir = null, values = emptyMap())

            fun str(key: String, default: String? = null): String? =
                options["kt2ts:$key"] ?: config.values[key]?.toString() ?: default

            // Path string: KSP args are not re-resolved (they're already absolute from gradle);
            // config-file values are resolved relative to the JSON file.
            fun strPath(key: String, default: String? = null): String? {
                options["kt2ts:$key"]?.let {
                    return it
                }
                val v = config.values[key] as? String ?: return default
                return config.baseDir?.resolve(v)?.normalize()?.toString() ?: v
            }

            fun bool(key: String, default: Boolean = false): Boolean {
                options["kt2ts:$key"]?.let {
                    return it == "true"
                }
                return (config.values[key] as? Boolean) ?: default
            }

            fun stringList(key: String, separator: String): List<String> {
                options["kt2ts:$key"]?.let {
                    return it.split(separator).map(String::trim).filter(String::isNotEmpty)
                }
                return (config.values[key] as? List<*>)?.map { it.toString() } ?: emptyList()
            }

            fun pathList(key: String, separator: String): List<String> {
                options["kt2ts:$key"]?.let {
                    return it.split(separator).map(String::trim).filter(String::isNotEmpty)
                }
                val list = (config.values[key] as? List<*>) ?: return emptyList()
                return list.map { v ->
                    val s = v.toString()
                    config.baseDir?.resolve(s)?.normalize()?.toString() ?: s
                }
            }

            val clientDirectory =
                strPath("clientDirectory")?.let(Paths::get)
                    ?: throw IllegalArgumentException(
                        "kt2ts:clientDirectory (or `clientDirectory` in the config file) is required"
                    )

            return Kt2TsConfiguration(
                clientDirectory = clientDirectory,
                srcDirectory = clientDirectory.resolve(str("srcDirectory") ?: "src"),
                generatedDirectory = str("generatedDirectory", "generated")!!,
                dropPackage = str("dropPackage", "")!!,
                mappings =
                    buildMappings(
                        manifestPaths = pathList("manifests", ","),
                        mappingsFromFile =
                            options["kt2ts:mappings"]?.let { readMappingsFile(Paths.get(it)) }
                                ?: (config.values["mappings"] as? String)?.let { rel ->
                                    val abs =
                                        config.baseDir?.resolve(rel)?.normalize() ?: Paths.get(rel)
                                    readMappingsFile(abs)
                                }
                                ?: emptyMap(),
                        inlineMappings =
                            (config.values["mappings"] as? Map<*, *>)?.entries?.associate {
                                it.key.toString() to it.value.toString()
                            } ?: emptyMap(),
                    ),
                nominalStringMappings = stringList("nominalStringMappings", "|").toSet(),
                nominalStringImport = str("nominalStringImport"),
                interfaceAsTypes = emptySet(),
                mapClass = str("mapClass"),
                mapClassFile = str("mapClassFile"),
                nodeBinary = strPath("nodeBinary"),
                prettierDependencyInstall = str("prettierDependencyInstall"),
                prettierBinary = strPath("prettierBinary"),
                absoluteImport = bool("absoluteImport"),
                absoluteImportPrefix = str("absoluteImportPrefix"),
                nestedClassSeparator = str("nestedClassSeparator", "$")!!,
                discriminatorProperty = str("discriminatorProperty", "objectType")!!,
                manifestOutput = strPath("manifestOutput")?.let { File(it) },
                debugFile = strPath("debugFile")?.let { File(it) },
            )
        }

        private fun buildMappings(
            manifestPaths: List<String>,
            mappingsFromFile: Map<String, String>,
            inlineMappings: Map<String, String>,
        ): Map<String, String> {
            // Order of precedence (last wins): manifests < inline config < user mappings file.
            val fromManifests =
                manifestPaths.fold(emptyMap<String, String>()) { acc, p ->
                    acc + readManifestFile(Paths.get(p))
                }
            return fromManifests + inlineMappings + mappingsFromFile
        }

        // ------------------------------------------------------------------------------------ //
        // Single-file config support                                                            //
        // ------------------------------------------------------------------------------------ //

        internal data class LoadedConfig(val baseDir: Path?, val values: Map<String, Any?>)

        internal fun loadConfigFile(path: Path): LoadedConfig {
            val seen = mutableSetOf<Path>()
            val values = loadConfigFileRecursive(path, seen)
            return LoadedConfig(baseDir = path.toRealPath().parent, values = values)
        }

        // [doc] Recursively loads a kt2ts.json config; supports an `extends` key (string or
        // array) interpreted as path(s) relative to the current file. Object values
        // (notably `mappings`) deep-merge across the extends chain; other values follow
        // "child overrides parent". All path-typed string values are resolved to absolute as
        // soon as they leave their owning file, so the merged result is portable.
        private fun loadConfigFileRecursive(path: Path, seen: MutableSet<Path>): Map<String, Any?> {
            val canonical = path.toRealPath()
            if (!seen.add(canonical)) {
                throw IllegalArgumentException(
                    "Cyclic `extends` in kt2ts config, file already visited: $canonical"
                )
            }
            val json = JSONObject(canonical.readText())
            val baseDir = canonical.parent
            val extendedPaths: List<Path> =
                when (val v = json.opt("extends")) {
                    null -> emptyList()
                    is String -> listOf(baseDir.resolve(v))
                    is JSONArray ->
                        (0 until v.length()).map { i -> baseDir.resolve(v.getString(i)) }
                    else ->
                        throw IllegalArgumentException(
                            "`extends` in $canonical must be a string or array of strings"
                        )
                }
            val inherited =
                extendedPaths.fold(emptyMap<String, Any?>()) { acc, p ->
                    mergeConfigValues(acc, resolvePaths(loadConfigFileRecursive(p, seen), baseDir))
                }
            val own = jsonObjectToMap(json).filterKeys { it != "extends" }
            return mergeConfigValues(inherited, resolvePaths(own, baseDir))
        }

        // Resolve path-typed string entries (and list-of-paths entries) to absolute paths
        // relative to the file's directory. Mappings as inline object are NOT path-resolved
        // (their values are TS import paths, kept as-is).
        private val pathKeys =
            setOf("clientDirectory", "nodeBinary", "prettierBinary", "manifestOutput", "debugFile")
        private val pathListKeys = setOf("manifests")

        private fun resolvePaths(values: Map<String, Any?>, base: Path): Map<String, Any?> =
            values.mapValues { (k, v) ->
                when {
                    k in pathKeys && v is String -> base.resolve(v).normalize().toString()
                    k in pathListKeys && v is List<*> ->
                        v.map { e -> base.resolve(e.toString()).normalize().toString() }
                    k == "mappings" && v is String -> base.resolve(v).normalize().toString()
                    else -> v
                }
            }

        private fun mergeConfigValues(
            parent: Map<String, Any?>,
            child: Map<String, Any?>,
        ): Map<String, Any?> {
            val keys = parent.keys + child.keys
            return keys.associateWith { k ->
                val pv = parent[k]
                val cv = child[k]
                when {
                    !child.containsKey(k) -> pv
                    !parent.containsKey(k) -> cv
                    // Deep-merge `mappings` objects so a child can add entries without
                    // restating the parent's. Other objects: child wins wholesale.
                    k == "mappings" && pv is Map<*, *> && cv is Map<*, *> ->
                        pv.entries.associate { it.key.toString() to it.value } +
                            cv.entries.associate { it.key.toString() to it.value }
                    else -> cv
                }
            }
        }

        private fun jsonObjectToMap(obj: JSONObject): Map<String, Any?> =
            obj.keySet().associateWith { k -> unwrap(obj.opt(k)) }

        private fun unwrap(v: Any?): Any? =
            when (v) {
                is JSONObject -> jsonObjectToMap(v)
                is JSONArray -> (0 until v.length()).map { unwrap(v.opt(it)) }
                JSONObject.NULL -> null
                else -> v
            }

        // ------------------------------------------------------------------------------------ //
        // Manifest file support                                                                 //
        // ------------------------------------------------------------------------------------ //

        // [doc] reads zero or more kt2ts-manifest.json files (comma-separated paths) and returns
        // their combined class → import path map. Missing files are skipped with a warning so a
        // first build (before the producer module has emitted) does not hard-fail; missing
        // entries will surface later as a normal "type not found" error. Later manifests
        // override earlier ones.
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

        // ------------------------------------------------------------------------------------ //
        // External kt-to-ts-mappings.json support (legacy, still usable independently)         //
        // ------------------------------------------------------------------------------------ //

        // [doc] reads a kt-to-ts-mappings.json. Supports a reserved `extends` key (string or
        // array of strings) whose value is interpreted as path(s) relative to the file. Cycles
        // raise IllegalArgumentException.
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
