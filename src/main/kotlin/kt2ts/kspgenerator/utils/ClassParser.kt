package kt2ts.kspgenerator.utils

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeParameter
import com.google.devtools.ksp.symbol.KSTypeReference
import kt2ts.kspgenerator.utils.ClassMapper.ClassMapping
import kt2ts.kspgenerator.utils.ClassMapper.mapProperty

// TODO[tmpl] name
object ClassParser {

    val packageExceptions = setOf("java", "jdk", "kotlin")

    // [doc] first parsing resolve all dependencies, stops when a dependency has already been parsed
    // second parsing parses all fields from initial selection, no recursivity, but exhaustively

    //    data class Dependency(val declaration: KSClassDeclaration, val generics:
    // List<KSClassDeclaration>)

    //    data class ParsedType(val name: String, val path: String, val arguments: List<ParsedType>)
    //
    //    data class ParsedField(val name: String, val type: ParsedType)

    // TODO[tmpl] name
    data class Parsed(
        // TOOD[tmpl] KSType vs KSDeclaration
        val type: KSType,
        val file: KSFile?,
        val dependencies: Set<KSTypeReference>,
        // TODO[tmpl] should we ? so this mapping isn't continual re-done
        //        val mapping: ClassMapper.ClassMapping?,
        //        val otherImports: List<String>,
        //        val fields: List<ParsedField>,
        //        val addObjectType: Boolean,
        val debug: Any? = null,
    ) {
        // without it we have duplicates (KS* comparison doesn't work as expected)
        override fun equals(other: Any?) =
            if (other is Parsed) {
                type.declaration.qualifiedName == other.type.declaration.qualifiedName
            } else {
                false
            }

        override fun hashCode() = type.declaration.qualifiedName.hashCode()
    }

    // TODO[tmpl] sealed support is very limited
    // will work if a field uses the sealed object and not directly a subclass
    fun parse(
        t: KSType,
        data: Set<Parsed>,
        mappings: Map<String, String>,
        mapClassMapping: ClassMapping?,
    ): Set<Parsed> {
        let {
            val p = t.declaration.packageName.asString()
            val i = p.indexOf(".")
            val packageStartWith = if (i != -1) p.substring(0, i) else p
            if (packageStartWith in packageExceptions) {
                return data
            }
        }
        // [doc] type parameters (T, K…), type aliases, etc. are not classes to descend into.
        val d = t.declaration as? KSClassDeclaration ?: return data
        val dependencies =
            d.declarations
                //                .mapNotNull { (it as?
                // KSPropertyDeclaration)?.type?.resolve()?.declaration }
                .filterIsInstance<KSPropertyDeclaration>()
                .flatMap { mapDependencies(it.type, mappings, mapClassMapping) }
                //                .map { it.resolve() }
                //                .filterIsInstance<KSClassDeclaration>()
                //                .mapNotNull { mapDependency(it) }
                .toSet()
        val withCurrent = data + Parsed(t, d.containingFile, dependencies)
        // [doc] dedup by qualifiedName: KSType equality is not stable across resolve() calls.
        val alreadyNames =
            withCurrent.mapNotNull { it.type.declaration.qualifiedName?.asString() }.toSet()
        return withCurrent
            .let { acc ->
                dependencies
                    // [doc] infinite loop is possible without it
                    .filter { it.resolve().declaration.qualifiedName?.asString() !in alreadyNames }
                    .fold(acc) { a, d -> parse(d.resolve(), a, mappings, mapClassMapping) }
            }
            // TODO[tmpl] a priori we should use findActuals
            .let { acc ->
                val seen = acc.mapNotNull { it.type.declaration.qualifiedName?.asString() }.toSet()
                d.getSealedSubclasses()
                    .filter { it.qualifiedName?.asString() !in seen }
                    .fold(acc) { a, d ->
                        parse(d.asStarProjectedType(), a, mappings, mapClassMapping)
                    }
            }
    }

    //    fun test(kd: KSDeclaration): KSDeclaration? {
    //        val d = (kd as? KSPropertyDeclaration)?.type?.resolve()?.declaration as?
    // KSClassDeclaration
    //        if (d == null) {
    //            return kd
    //        }
    //        val a = allAscendance(d)
    //        if (mapAscendanceToScalar(a) != null) {
    //            return null
    //        }
    //        if (Collection::class.qualifiedName in a) {
    //            //            Debug.add("${kd}")
    //            //            Debug.add("${kd::class.java}")
    //            val t = kd as? KSPropertyDeclaration
    //            t!!
    //            Debug.add("${t.type.element?.typeArguments}")
    //            Debug.add("---")
    //            //            Debug.add("${t.typeParameters}")
    //            t.type
    //        }
    //        return kd
    //    }

    // TODO[tmpl] test: generic with generic inside
    fun mapDependencies(
        t: KSTypeReference,
        mappings: Map<String, String>,
        mapClassMapping: ClassMapping?,
    ): List<KSTypeReference> {
        //        val d = t.resolve().declaration as? KSClassDeclaration
        //        val a = allAscendance(classDeclaration)
        // TODO[tmpl] this method to find ancestry is actually a very bad idea
        // we could have created a inheriting class (which could handle the generic)
        //        if (Collection::class.qualifiedName in a) {
        //            val element =
        //                d.type.element
        //                    ?: let {
        //                        // TODO[tmpl]
        ////                        Debug.add()
        //                        throw IllegalArgumentException()
        //                    }
        //            val typeArgument = element.typeArguments
        //            return Dependency(typeArgument.)
        //        }
        // [doc] for type with generic
        // TODO[tmpl] no scenario where it happens ?
        val arguments =
            t.element
                ?.typeArguments
                ?.mapNotNull { it.type }
                // TODO[tmpl] HERE we can fall in a infinite loop ?
                // but DO NOT use data to filter, we need a contextual set()
                ?.flatMap { mapDependencies(it, mappings, mapClassMapping) } ?: emptyList()
        // [doc] type parameters (T, K…) are placeholders, not concrete dependencies; do not
        // emit them so callers don't try to resolve them as classes downstream.
        if (t.resolve().declaration is KSTypeParameter) {
            return arguments
        }
        // TODO[tmpl] actually not a good idea, take out the mapped to get them later
        val r =
            if (mapProperty(t, mappings, mapClassMapping) == null) {
                listOf(t)
            } else {
                emptyList()
            }
        return r + arguments
    }

    //    fun mapDependency(d: KSClassDeclaration): KSClassDeclaration? {
    //        val a = allAscendance(d)
    //        if (mapAscendanceToScalar(a) != null) {
    //            return null
    //        }
    //        if (Collection::class.qualifiedName in a) {
    //            //            d.asType(emptyList())
    //            //            Debug.add("${d.qualifiedName?.asString()} :")
    //            //
    //            //            Debug.add(
    //            //                "${d.asType(emptyList()).arguments.mapNotNull {
    //            // it.type!!.resolve().declaration.qualifiedName!!.asString() }}")
    //            //
    //            // Debug.add("${d.asType(emptyList()).declaration.qualifiedName?.asString()}")
    //            return null
    //        }
    //        return d
    //    }

    //    fun mapToScalar(d: KSClassDeclaration): String? = mapAscendanceToScalar(allAscendance(d))

    // TODO[tmpl] naming ancestry ?
    //    fun mapAscendanceToScalar(a: Set<String>): String? {
    //        if (UUID::class.qualifiedName in a) {
    //            return ""
    //        }
    //        if (Number::class.qualifiedName in a) {
    //            return ""
    //        }
    //        if (String::class.qualifiedName in a) {
    //            return ""
    //        }
    //        if (String::class.qualifiedName in a) {
    //            return ""
    //        }
    //        //        if (Comparable::class.qualifiedName in a) {
    //        //            return ""
    //        //        }
    //        //        if (CharSequence::class.qualifiedName in a) {
    //        //            return ""
    //        //        }
    //        return null
    //    }

}
