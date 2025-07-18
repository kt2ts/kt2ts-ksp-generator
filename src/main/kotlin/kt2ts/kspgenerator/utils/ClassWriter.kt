package kt2ts.kspgenerator.utils

import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSTypeReference
import com.google.devtools.ksp.symbol.Nullability
import kt2ts.kspgenerator.utils.ClassMapper.ClassMapping

object ClassWriter {

    // TODO[tmpl] about support of Jackson annotations ? field @Ignore
    fun toTs(
        parsed: ClassParser.Parsed,
        mappings: Map<String, String>,
        nominalStringMappings: Set<String>,
        nominalStringImport: String?,
        mapClassMapping: ClassMapping?,
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
                        sb.appendLine("export type ${className(d)} =")
                        subTypes.forEach { sb.appendLine("  | ${className(it)}") }
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
                        sb.appendLine("export interface ${className(d)} {")
                        if (parentIsSealedClass) {
                            // TODO[tmpl] depends on the jackson annotation
                            sb.appendLine("  objectType: '${className(d)}';")
                        }
                        d.declarations.filterIsInstance<KSPropertyDeclaration>().forEach {
                            val nullableMark =
                                when (it.type.resolve().nullability) {
                                    Nullability.NULLABLE -> "?"
                                    Nullability.NOT_NULL,
                                    Nullability.PLATFORM -> ""
                                }
                            val c = propertyClassMap(it.type, mappings, mapClassMapping)
                            sb.appendLine("  ${it.simpleName.asString()}$nullableMark: ${c.name};")
                        }
                        sb.appendLine("}")
                        sb.appendLine("")
                    } else {
                        val subTypes = d.getSealedSubclasses().toList()
                        //                                .filter {
                        //                                it.declarations
                        //
                        // .filterIsInstance<KSPropertyDeclaration>()
                        //                                    .toList()
                        //                                    .isNotEmpty()
                        //                            }
                        if (subTypes.isNotEmpty()) {
                            sb.appendLine("export type ${className(d)} =")
                            subTypes.forEach { sb.appendLine("  | ${className(it)}") }
                            sb.appendLine("")
                        }
                    }
                }

                ClassKind.ENUM_CLASS -> {
                    sb.appendLine("export type ${className(d)} = ")
                    d.declarations
                        .filterIsInstance<KSClassDeclaration>()
                        .filter { it.classKind == ClassKind.ENUM_ENTRY }
                        .forEach { sb.appendLine(" | '${it.simpleName.asString()}'") }
                    sb.appendLine("")
                    //                    d.declarations.toList().forEach { Debug.add("$it
                    // ${it::class.java}") }
                }

                ClassKind.ENUM_ENTRY ->
                    TODO("ClassKind.ENUM_ENTRY is not implemented ${className(d)}")

                ClassKind.OBJECT -> {
                    if (
                        d.declarations
                            .toList()
                            .filterIsInstance<KSPropertyDeclaration>()
                            .isNotEmpty()
                    ) {
                        TODO(
                            "ClassKind.OBJECT with declarations is not implemented ${className(d)}"
                        )
                    } else {
                        // TODO[tmpl] not good but the only way to handle EmptyCommandResponse for
                        // the moment
                        if (parentIsSealedClass) {
                            sb.appendLine("export interface ${className(d)} {")
                            // TODO[tmpl] depends on the jackson annotation
                            sb.appendLine("  objectType: '${className(d)}';")
                            sb.appendLine("}")
                            sb.appendLine("")
                        }
                    }
                }

                ClassKind.ANNOTATION_CLASS ->
                    TODO("ClassKind.ANNOTATION_CLASS is not implemented ${className(d)}")
            }
        } else {
            sb.appendLine("export type ${className(d)} = ${mapping.name};")
            sb.appendLine("")
        }
        return sb
    }

    fun className(d: KSDeclaration): String {
        // TODO is enough ? only KSClassDeclaration can contain inner classes ?
        val parent = d.parentDeclaration as? KSClassDeclaration
        val prefix = parent?.let { className(it) + "$" } ?: ""
        return prefix + d.simpleName.asString()
    }

    fun propertyClassMap(
        t: KSTypeReference,
        mappings: Map<String, String>,
        mapClassMapping: ClassMapping?,
    ): ClassMapping =
        ClassMapper.mapProperty(t, mappings, mapClassMapping)
            ?: ClassMapping(className(t.resolve().declaration))

    fun nullablePropertyClassName(
        t: KSTypeReference,
        mappings: Map<String, String>,
        mapClassMapping: ClassMapping?,
    ) =
        propertyClassMap(t, mappings, mapClassMapping).name.let {
            when (t.resolve().nullability) {
                Nullability.NULLABLE -> "($it | null)"
                Nullability.NOT_NULL,
                Nullability.PLATFORM -> it
            }
        }
}
