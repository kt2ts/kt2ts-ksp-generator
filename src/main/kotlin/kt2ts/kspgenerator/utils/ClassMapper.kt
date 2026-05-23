package kt2ts.kspgenerator.utils

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSTypeReference
import kt2ts.kspgenerator.utils.ClassWriter.nullablePropertyClassName
import kt2ts.kspgenerator.utils.ClassWriter.propertyClassMap

object ClassMapper {

    // TODO[tmpl] an example if there isn't in default generated code
    data class ClassMapping(val name: String, val tsFile: String? = null)

    // [doc] Walk a property's type tree and return every mapping that applies — the outer type
    // plus any mapped type argument. mapProperty alone misses arguments deep inside generics
    // (e.g. ProviderInformations inside DiffWithInserts<ProviderInformations>), which then go
    // unimported in the generated TS.
    fun collectMappedTypes(
        t: KSTypeReference,
        mappings: Map<String, String>,
        mapClassMapping: ClassMapping?,
    ): List<ClassMapping> {
        val self = listOfNotNull(mapProperty(t, mappings, mapClassMapping))
        val args =
            t.element
                ?.typeArguments
                ?.mapNotNull { it.type }
                ?.flatMap { collectMappedTypes(it, mappings, mapClassMapping) } ?: emptyList()
        return self + args
    }

    fun mapProperty(
        t: KSTypeReference,
        mappings: Map<String, String>,
        mapClassMapping: ClassMapping?,
    ): ClassMapping? {
        val rawDecl = t.resolve().declaration
        // [doc] check mappings before casting to KSClassDeclaration so type aliases work too.
        val qualifiedName = rawDecl.qualifiedName?.asString()
        if (qualifiedName != null) {
            val mapped = lookupMapping(qualifiedName, mappings)
            if (mapped != null) {
                return ClassMapping(rawDecl.simpleName.asString(), mapped)
            }
        }
        val d = rawDecl as? KSClassDeclaration ?: return null
        return when (qualifiedName) {
            Boolean::class.qualifiedName -> ClassMapping("boolean")
            Double::class.qualifiedName -> ClassMapping("number")
            Int::class.qualifiedName -> ClassMapping("number")
            Long::class.qualifiedName -> ClassMapping("number")
            String::class.qualifiedName -> ClassMapping("string")
            Set::class.qualifiedName,
            List::class.qualifiedName -> {
                val type =
                    t.element?.typeArguments?.firstOrNull()?.type
                        ?: throw IllegalArgumentException()
                val name = nullablePropertyClassName(type, mappings, mapClassMapping)
                ClassMapping("$name[]")
            }
            Pair::class.qualifiedName -> {
                val a = t.element?.typeArguments ?: throw IllegalArgumentException()
                val t1 =
                    (a.firstOrNull()?.type ?: throw IllegalArgumentException()).let {
                        nullablePropertyClassName(it, mappings, mapClassMapping)
                    }
                val t2 =
                    (a.getOrNull(1)?.type ?: throw IllegalArgumentException()).let {
                        nullablePropertyClassName(it, mappings, mapClassMapping)
                    }
                ClassMapping("[$t1,$t2]")
            }
            // TODO[tmpl] Record vs Dict we have a problem
            // case by case
            // can specify an annotation ? which could be checked at serialization/deser
            // OR always loose, easier not to type it
            Map::class.qualifiedName -> {
                if (mapClassMapping == null) {
                    throw IllegalArgumentException()
                }
                // TODO t1 should be :
                // - a scalar (?)
                // - not nullable
                val t1 =
                    (t.element?.typeArguments?.firstOrNull()?.type
                            ?: throw IllegalArgumentException())
                        .let { propertyClassMap(it, mappings, mapClassMapping).name }
                val t2 =
                    (t.element?.typeArguments?.get(1)?.type ?: throw IllegalArgumentException())
                        .let { nullablePropertyClassName(it, mappings, mapClassMapping) }
                ClassMapping("${mapClassMapping.name}<$t1,$t2>", mapClassMapping.tsFile)
            }
            Any::class.qualifiedName -> ClassMapping("any")
            else -> null
        }
    }

    fun mapClass(
        d: KSClassDeclaration,
        nominalStringMappings: Set<String>,
        nominalStringImport: String?,
    ): ClassMapping? {
        val ancestry = recursiveAncestry(d).mapNotNull { it.qualifiedName?.asString() }
        nominalStringMappings.forEach {
            if (it in ancestry) {
                return ClassMapping(
                    "NominalString<'${d.simpleName.asString()}'>",
                    nominalStringImport ?: "utils/nominal-class",
                )
            }
        }
        return null
    }

    // [doc] resolves a class qualified name against the user-supplied mapping table. Exact-key
    // hits are preferred; otherwise any key containing `*` is treated as a glob (single `*`
    // matches one segment, i.e. `[^.]*`; `**` matches across dots). Among multiple matching
    // patterns, the longest pattern (the most specific) wins to make conflicts deterministic.
    internal fun lookupMapping(qualifiedName: String, mappings: Map<String, String>): String? {
        mappings[qualifiedName]?.let {
            return it
        }
        return mappings
            .filterKeys { it.contains('*') }
            .filterKeys { patternMatches(it, qualifiedName) }
            .maxByOrNull { it.key.length }
            ?.value
    }

    internal fun patternMatches(pattern: String, value: String): Boolean {
        val regex =
            buildString {
                    append('^')
                    var i = 0
                    while (i < pattern.length) {
                        val c = pattern[i]
                        when {
                            c == '*' && i + 1 < pattern.length && pattern[i + 1] == '*' -> {
                                append(".*")
                                i += 2
                            }
                            c == '*' -> {
                                append("[^.]*")
                                i++
                            }
                            else -> {
                                append(Regex.escape(c.toString()))
                                i++
                            }
                        }
                    }
                    append('$')
                }
                .let(::Regex)
        return regex.matches(value)
    }

    // TODO[tmpl] name
    fun recursiveAncestry(d: KSClassDeclaration): Set<KSClassDeclaration> =
        (listOf(d) +
                (d.superTypes.mapNotNull { it.resolve().declaration as? KSClassDeclaration })
                    .flatMap { recursiveAncestry(it) })
            .toSet()
}
