package kt2ts.kspgenerator.utils

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSTypeReference
import kt2ts.kspgenerator.utils.ClassWriter.nullablePropertyClassName
import kt2ts.kspgenerator.utils.ClassWriter.propertyClassMap

object ClassMapper {

    // TODO[tmpl] an example if there isn't in default generated code
    data class ClassMapping(val name: String, val tsFile: String? = null)

    fun mapProperty(
        t: KSTypeReference,
        mappings: Map<String, String>,
        mapClassMapping: ClassMapping?,
    ): ClassMapping? {
        val d = t.resolve().declaration as? KSClassDeclaration ?: return null
        val qualifiedName = d.qualifiedName?.asString()
        if (qualifiedName != null && qualifiedName in mappings.keys) {
            return ClassMapping(d.simpleName.asString(), mappings.getValue(qualifiedName))
        }
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

    // TODO[tmpl] name
    fun recursiveAncestry(d: KSClassDeclaration): Set<KSClassDeclaration> =
        (listOf(d) +
                (d.superTypes.mapNotNull { it.resolve().declaration as? KSClassDeclaration })
                    .flatMap { recursiveAncestry(it) })
            .toSet()
}
