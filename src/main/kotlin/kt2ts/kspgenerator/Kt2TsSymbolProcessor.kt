package kt2ts.kspgenerator

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.validate
import java.nio.file.Files
import java.nio.file.Paths
import java.time.LocalDateTime
import kotlin.io.path.absolutePathString
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.notExists
import kotlin.io.path.pathString
import kt2ts.annotation.GenerateTypescript
import kt2ts.kspgenerator.utils.ClassMapper
import kt2ts.kspgenerator.utils.ClassMapper.ClassMapping
import kt2ts.kspgenerator.utils.ClassParser
import kt2ts.kspgenerator.utils.ClassWriter
import kt2ts.kspgenerator.utils.ImportWriter.absolutePath
import kt2ts.kspgenerator.utils.ImportWriter.generatedFileExtention
import kt2ts.kspgenerator.utils.ImportWriter.kotlinToTsFile
import kt2ts.kspgenerator.utils.ImportWriter.relativePath
import kt2ts.kspgenerator.utils.Kt2TsConfiguration
import kt2ts.kspgenerator.utils.ShellRunner
import kt2ts.kspgenerator.utils.prettyPrint

// TODO[tmpl] use exceptions and catch them for debug report ?
// TODO[tmpl] clean !!
// TODO[tmpl] in TS empty interfaces are useless
// TODO[tmpl]
// https://docs.gradle.org/current/userguide/custom_plugins.html#sec:writing_tests_for_your_plugin
// TODO[tmpl] see ExtensionContainer to extend the plugin
// TODO[tmpl] problem if an objectType in CommandResponse... should be smarter
// support Jackson annotations
class Kt2TsSymbolProcessor(
    val codeGenerator: CodeGenerator,
    val logger: KSPLogger,
    val options: Map<String, String>,
) : SymbolProcessor {
    override fun process(resolver: Resolver): List<KSAnnotated> {
        val startTime = System.currentTimeMillis()
        val symbols =
            resolver
                .getSymbolsWithAnnotation(GenerateTypescript::class.java.name)
                .filterIsInstance<KSClassDeclaration>()
        processSymbols(symbols.toList(), resolver.getAllFiles().toList(), startTime)
        val unableToProcess = symbols.filterNot { it.validate() }.toList()
        return unableToProcess
    }

    fun processSymbols(
        symbols: List<KSClassDeclaration>,
        modifiedFiles: List<KSFile>,
        startTime: Long,
    ) {
        if (symbols.isEmpty()) {
            return
        }
        val configuration = Kt2TsConfiguration.init(options)
        val debugReport = if (configuration.debugFile != null) StringBuilder() else null
        debugReport?.appendLine("<html><body><pre>")
        debugReport?.appendLine("Start generation ${LocalDateTime.now()}")
        debugReport?.appendLine("<h1>Configuration</h1>")
        debugReport?.appendLine(configuration.prettyPrint())
        debugReport?.apply {
            appendLine("<h1>Initial symbols selection</h1>")
            symbols
                .sortedBy { it.qualifiedName?.asString() }
                .forEach { appendLine("${it.qualifiedName?.asString()}") }
        }
        //        val visitor = Kt2TsVisitor()
        // TODO[tmpl] add exceptions: mapped classes in configuration
        //        val parsingResult =
        //            symbols.fold(emptySet<ClassParser.Parsed>()) { acc, declaration ->
        //                declaration.accept(visitor, acc)
        //            }
        val mapClassMapping = let {
            if (configuration.mapClass != null && configuration.mapClassFile != null) {
                ClassMapping(configuration.mapClass, configuration.mapClassFile)
            } else {
                ClassMapping("Record", null)
            }
        }
        val parsingResult =
            symbols
                .fold(emptySet<ClassParser.Parsed>()) { acc, declaration ->
                    ClassParser.parse(
                        declaration.asStarProjectedType(),
                        acc,
                        configuration.mappings,
                        mapClassMapping,
                    )
                }
                .filter { it.file in modifiedFiles }
        debugReport?.apply {
            appendLine("<h1>Class list (${parsingResult.size} items)</h1>")
            parsingResult.forEach { appendLine(it.type.declaration.simpleName.asString()) }
        }
        val filesSelection = parsingResult.mapNotNull { it.type.declaration.containingFile }.toSet()
        debugReport?.apply {
            appendLine("<h1>Files list (${filesSelection.size} items)</h1>")
            filesSelection.forEach { appendLine(it.filePath) }
        }
        //        val typesSelection = parsingResult.map { it.declaration }
        //        val importsMap = parsingResult.associateBy { it.declaration }
        val parsingResultMap = parsingResult.associateBy { it.type.declaration }
        val tempDir = Files.createTempDirectory("kt2ts-")
        debugReport?.apply {
            appendLine("<h1>Temp dir </h1>")
            appendLine(tempDir.absolutePathString())
        }
        val result =
            filesSelection
                .map { ksFile ->
                    val fileDeclarations =
                        ksFile.declarations.toList().flatMap { d ->
                            val innerDeclarations =
                                if (d is KSClassDeclaration) {
                                    d.declarations.toList()
                                } else emptyList()
                            listOf(d) + innerDeclarations
                        }
                    ksFile to fileDeclarations.mapNotNull { parsingResultMap[it] }
                }
                .map { (ksFile, parsed) ->
                    val file = tempDir.resolve(kotlinToTsFile(ksFile, configuration))
                    //                debugReport?.appendLine("$file")
                    file.parent.toFile().mkdirs()
                    // TODO un imports writer...
                    val imports = let {
                        val dependenciesImportsMapped =
                            parsed.flatMap {
                                val d = it.type.declaration as KSClassDeclaration
                                if (d.classKind == ClassKind.ENUM_CLASS) {
                                    emptySequence()
                                } else {
                                    d.declarations
                                        .filterIsInstance<KSPropertyDeclaration>()
                                        .mapNotNull {
                                            ClassMapper.mapProperty(
                                                it.type,
                                                configuration.mappings,
                                                mapClassMapping,
                                            )
                                        }
                                }
                            }
                        val dependenciesImports =
                            parsed
                                .flatMap {
                                    val d = it.type.declaration as KSClassDeclaration
                                    if (d.classKind == ClassKind.ENUM_CLASS) {
                                        emptyList()
                                    } else {
                                        it.dependencies
                                    }
                                }
                                .toSet()
                                .mapNotNull { t -> t.resolve().declaration as? KSClassDeclaration }
                                .mapNotNull { d ->
                                    d.containingFile?.let {
                                        ClassMapper.ClassMapping(
                                            ClassWriter.className(d),
                                            kotlinToTsFile(it, configuration),
                                        )
                                    }
                                }
                        val classImports =
                            parsed
                                .mapNotNull { it.type.declaration as? KSClassDeclaration }
                                .mapNotNull {
                                    ClassMapper.mapClass(
                                        it,
                                        configuration.nominalStringMappings,
                                        configuration.nominalStringImport,
                                    )
                                }
                        dependenciesImportsMapped + dependenciesImports + classImports
                    }
                    val sb = StringBuilder()
                    val tsFile = kotlinToTsFile(ksFile, configuration)
                    imports
                        .groupBy { it.tsFile }
                        .toList()
                        .mapNotNull { p -> p.first?.let { it to p.second } }
                        .filter { it.first != tsFile }
                        .sortedBy { it.first }
                        .forEach { (file, imports) ->
                            val i =
                                imports
                                    .map {
                                        val i = it.name.indexOf("<")
                                        if (i != -1) {
                                            it.name.substring(0, i)
                                        } else {
                                            it.name
                                        }
                                    }
                                    .distinct()
                                    .sorted()
                                    .joinToString(separator = ", ")
                            val from =
                                if (configuration.absoluteImport) {
                                    absolutePath(file, configuration)
                                } else {
                                    relativePath(file, tsFile, configuration)
                                }
                            sb.appendLine("import { $i } from '$from';")
                        }
                    sb.appendLine("")
                    //                val keepDeclarations = parsed.map { it.type.declaration }
                    // [doc] restarting from file here (instead of using directly parsed) permits
                    // order conservation
                    parsed.forEach {
                        sb.append(
                            ClassWriter.toTs(
                                it,
                                configuration.mappings,
                                configuration.nominalStringMappings,
                                configuration.nominalStringImport,
                                mapClassMapping,
                            )
                        )
                    }
                    Files.write(file, sb.toString().toByteArray())
                    ksFile to file
                }
        //        debugReport?.let {
        //            typesSelection.map {
        // debugReport.appendLine("${it.qualifiedName?.asString()}")
        // }
        //        }
        debugReport?.appendLine("<h1>Format</h1>")
        if (
            configuration.prettierBinary != null &&
                configuration.clientDirectory.resolve(configuration.prettierBinary).notExists() &&
                configuration.prettierDependencyInstall != null
        ) {
            ShellRunner.run(configuration.clientDirectory, configuration.prettierDependencyInstall)
        }
        result.forEach { (ksFile, path) ->
            // works if packages are ok
            val destination =
                configuration.srcDirectory.resolve(kotlinToTsFile(ksFile, configuration))
            destination.parent.createDirectories()

            // TODO[fmk] format before writing file to avoid triggering webpack hot reload, useless
            // temporary diffs...
            // TODO a plugin option to fail kotlin build in this case
            // TODO a plugin option to fail kotlin build in this case
            configuration.prettierBinary?.let {
                val formatResult =
                    ShellRunner.run(
                        configuration.clientDirectory,
                        configuration.nodeBinary ?: "node",
                        configuration.prettierBinary,
                        "--config",
                        "package.json",
                        "--write",
                        path.absolutePathString(),
                    )
                if (formatResult.exitCode != 0) {
                    Files.write(
                        path,
                        ("// [WARN] could not format files, is node_modules installed ?\n" +
                                Files.readString(path))
                            .toByteArray(Charsets.UTF_8),
                    )
                    debugReport?.appendLine("<pre>Failed format ${path.fileName}</pre>")
                    debugReport?.appendLine("<pre>Output: ${formatResult.output}</pre>")
                    debugReport?.appendLine("<pre>Error output: ${formatResult.errorOutput}</pre>")
                }
            }
            // file is written once at the end of the process to avoid triggering webpack hot reload
            // serveral times
            ShellRunner.run("mv", path.absolutePathString(), destination.absolutePathString())
        }
        // Delete generated files which does not exist anymore in Kotlin
        // TODO shoul not be here ! if no modif for kotlin compiler, we must do this check
        // => we won't be able to with no modifiedFile =s
        configuration.srcDirectory.resolve(configuration.generatedDirectory).let {
            debugReport?.appendLine("<h1>Removed files</h1>")
            val kotlinSrc =
                Paths.get(
                    modifiedFiles.first().let {
                        it.filePath
                            .dropLastWhile { it != '/' }
                            .dropLast(it.packageName.asString().length + 1)
                    }
                )
            it.toFile().walk().forEach {
                if (it.extension == generatedFileExtention) {
                    val relativeFilePath =
                        it.absolutePath.let {
                            it.drop(configuration.srcDirectory.pathString.length + 1)
                                .drop(configuration.generatedDirectory.length + 1)
                        }
                    val kotlinFilePath =
                        kotlinSrc
                            .resolve(configuration.dropPackage.replace(".", "/"))
                            .resolve(relativeFilePath.dropLastWhile { it != '.' } + "kt")
                    if (!kotlinFilePath.exists()) {
                        debugReport?.appendLine(it.absolutePath)
                        it.delete()
                    }
                }
            }
        }
        debugReport?.appendLine("<h1>Report</h1>")
        debugReport?.appendLine("Finished generation ${LocalDateTime.now()}")
        debugReport?.appendLine("Took ${System.currentTimeMillis() - startTime}ms")
        debugReport?.appendLine("</pre></body></html>")
        debugReport?.let {
            if (configuration.debugFile == null) {
                throw RuntimeException()
            }
            configuration.debugFile.parentFile.mkdirs()
            configuration.debugFile.writeText(debugReport.toString())
        }
    }
}
