package kt2ts.kspgenerator.utils

import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSTypeParameter
import com.google.devtools.ksp.symbol.KSTypeReference
import com.google.devtools.ksp.symbol.Nullability
import kt2ts.kspgenerator.utils.ClassMapper.ClassMapping

object ClassWriter {

    // [doc] separator Jackson Id.NAME uses for nested classes (the JVM binary-name `$`). Always
    // emitted as-is in the `objectType` discriminator value so wire format keeps matching the
    // server, independently of the TS-side nestedClassSeparator chosen for type names.
    private const val JACKSON_NESTED_SEPARATOR = "$"

    // TODO[tmpl] about support of Jackson annotations ? field @Ignore
    fun toTs(
        parsed: ClassParser.Parsed,
        mappings: Map<String, String>,
        nominalStringMappings: Set<String>,
        nominalStringImport: String?,
        mapClassMapping: ClassMapping?,
        nestedClassSeparator: String = JACKSON_NESTED_SEPARATOR,
    ): StringBuilder {
        val d = parsed.type.declaration as? KSClassDeclaration ?: throw IllegalArgumentException()
        val mapping = ClassMapper.mapClass(d, nominalStringMappings, nominalStringImport)
        val sb = StringBuilder()
        val parentIsSealedClass = let {
            val parentSealedSubClasses =
                (parsed.type.declaration as KSClassDeclaration)
                    .superTypes
                    .mapNotNull { it.resolve().declaration as? KSClassDeclaration }
                    .flatMap { it.getSealedSubclasses() }
            parentSealedSubClasses.count() != 0
        }
        if (mapping == null) {
            when (d.classKind) {
                ClassKind.INTERFACE -> {
                    val subTypes = d.getSealedSubclasses().toList()
                    if (subTypes.isNotEmpty()) {
                        sb.appendLine("export type ${className(d, nestedClassSeparator)} =")
                        subTypes.forEach {
                            sb.appendLine("  | ${className(it, nestedClassSeparator)}")
                        }
                        sb.appendLine("")
                    }
                }

                ClassKind.CLASS -> {
                    // TODO[tmpl] filtering on properties existence should be done at initial
                    // selection => or not for sealed
                    // TODO some class can have properties AND be a sealed class
                    val properties =
                        d.declarations.filterIsInstance<KSPropertyDeclaration>().toList()
                    // TODO || parentIsSealedClass ??
                    val isSealedClass = d.getSealedSubclasses().toList().isNotEmpty()
                    if (!isSealedClass) {
                        // former if : if (properties.isNotEmpty() || parentIsSealedClass) {
                        // TODO[tmpl] about class which are not data classes
                        sb.appendLine("export interface ${className(d, nestedClassSeparator)} {")
                        if (parentIsSealedClass) {
                            // TODO[tmpl] depends on the jackson annotation
                            sb.appendLine(
                                "  objectType: '${className(d, JACKSON_NESTED_SEPARATOR)}';"
                            )
                        }
                        d.declarations.filterIsInstance<KSPropertyDeclaration>().forEach {
                            val nullableMark =
                                when (it.type.resolve().nullability) {
                                    Nullability.NULLABLE -> "?"
                                    Nullability.NOT_NULL,
                                    Nullability.PLATFORM -> ""
                                }
                            val c =
                                propertyClassMap(
                                    it.type,
                                    mappings,
                                    mapClassMapping,
                                    nestedClassSeparator,
                                )
                            sb.appendLine("  ${it.simpleName.asString()}$nullableMark: ${c.name};")
                        }
                        sb.appendLine("}")
                        sb.appendLine("")
                    } else {
                        val subTypes = d.getSealedSubclasses().toList()
                        if (subTypes.isNotEmpty()) {
                            sb.appendLine("export type ${className(d, nestedClassSeparator)} =")
                            subTypes.forEach {
                                sb.appendLine("  | ${className(it, nestedClassSeparator)}")
                            }
                            sb.appendLine("")
                        }
                    }
                }

                ClassKind.ENUM_CLASS -> {
                    sb.appendLine("export type ${className(d, nestedClassSeparator)} = ")
                    d.declarations
                        .filterIsInstance<KSClassDeclaration>()
                        .filter { it.classKind == ClassKind.ENUM_ENTRY }
                        .forEach { sb.appendLine(" | '${it.simpleName.asString()}'") }
                    sb.appendLine("")
                }

                ClassKind.ENUM_ENTRY ->
                    TODO(
                        "ClassKind.ENUM_ENTRY is not implemented ${className(d, nestedClassSeparator)}"
                    )

                ClassKind.OBJECT -> {
                    if (
                        d.declarations
                            .toList()
                            .filterIsInstance<KSPropertyDeclaration>()
                            .isNotEmpty()
                    ) {
                        TODO(
                            "ClassKind.OBJECT with declarations is not implemented ${className(d, nestedClassSeparator)}"
                        )
                    } else {
                        // TODO[tmpl] not good but the only way to handle EmptyCommandResponse for
                        // the moment
                        if (parentIsSealedClass) {
                            sb.appendLine(
                                "export interface ${className(d, nestedClassSeparator)} {"
                            )
                            sb.appendLine(
                                "  objectType: '${className(d, JACKSON_NESTED_SEPARATOR)}';"
                            )
                            sb.appendLine("}")
                            sb.appendLine("")
                        }
                    }
                }

                ClassKind.ANNOTATION_CLASS ->
                    TODO(
                        "ClassKind.ANNOTATION_CLASS is not implemented ${className(d, nestedClassSeparator)}"
                    )
            }
        } else {
            sb.appendLine("export type ${className(d, nestedClassSeparator)} = ${mapping.name};")
            sb.appendLine("")
        }
        return sb
    }

    fun className(
        d: KSDeclaration,
        nestedClassSeparator: String = JACKSON_NESTED_SEPARATOR,
    ): String {
        // [doc] type parameters (T, K…) carry their declaring class as parentDeclaration; do not
        // prefix or they become invalid TS identifiers like "InsertsDto$T".
        if (d is KSTypeParameter) {
            return d.simpleName.asString()
        }
        // TODO is enough ? only KSClassDeclaration can contain inner classes ?
        val parent = d.parentDeclaration as? KSClassDeclaration
        val prefix =
            parent?.let { className(it, nestedClassSeparator) + nestedClassSeparator } ?: ""
        val suffix =
            if (d is KSClassDeclaration && d.typeParameters.isNotEmpty()) {
                d.typeParameters.joinToString(", ", "<", ">") { it.simpleName.asString() }
            } else {
                ""
            }
        return prefix + d.simpleName.asString() + suffix
    }

    // [doc] like className but without the generic <T, K…> suffix, used at property usage sites
    // where the actual type arguments are appended instead.
    private fun rawName(d: KSDeclaration, nestedClassSeparator: String): String {
        if (d is KSTypeParameter) {
            return d.simpleName.asString()
        }
        val parent = d.parentDeclaration as? KSClassDeclaration
        val prefix =
            parent?.let { className(it, nestedClassSeparator) + nestedClassSeparator } ?: ""
        return prefix + d.simpleName.asString()
    }

    private fun propertyTypeName(
        t: KSTypeReference,
        mappings: Map<String, String>,
        mapClassMapping: ClassMapping?,
        nestedClassSeparator: String,
    ): String {
        val mapped = ClassMapper.mapProperty(t, mappings, mapClassMapping)
        if (mapped != null) return mapped.name
        val resolved = t.resolve()
        val base = rawName(resolved.declaration, nestedClassSeparator)
        val args = t.element?.typeArguments?.mapNotNull { it.type }.orEmpty()
        return if (args.isEmpty()) base
        else
            base +
                args.joinToString(", ", "<", ">") {
                    propertyTypeName(it, mappings, mapClassMapping, nestedClassSeparator)
                }
    }

    fun propertyClassMap(
        t: KSTypeReference,
        mappings: Map<String, String>,
        mapClassMapping: ClassMapping?,
        nestedClassSeparator: String = JACKSON_NESTED_SEPARATOR,
    ): ClassMapping =
        ClassMapper.mapProperty(t, mappings, mapClassMapping)
            ?: ClassMapping(propertyTypeName(t, mappings, mapClassMapping, nestedClassSeparator))

    fun nullablePropertyClassName(
        t: KSTypeReference,
        mappings: Map<String, String>,
        mapClassMapping: ClassMapping?,
        nestedClassSeparator: String = JACKSON_NESTED_SEPARATOR,
    ) =
        propertyClassMap(t, mappings, mapClassMapping, nestedClassSeparator).name.let {
            when (t.resolve().nullability) {
                Nullability.NULLABLE -> "($it | null)"
                Nullability.NOT_NULL,
                Nullability.PLATFORM -> it
            }
        }
}
