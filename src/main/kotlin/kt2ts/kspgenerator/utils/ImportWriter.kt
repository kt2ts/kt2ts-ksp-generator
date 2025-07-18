package kt2ts.kspgenerator.utils

import com.google.devtools.ksp.symbol.KSFile
import kotlin.io.path.pathString

object ImportWriter {

    val generatedFileExtention = "generated.ts"

    // TODO name
    fun kotlinToTsFile(ksFile: KSFile, conf: Kt2TsConfiguration): String {
        val dir =
            ksFile.packageName
                .asString()
                .replace(conf.dropPackage, "")
                // TODO what ??
                //                .replace("..", ".")
                .replace(".", "/")
                .removePrefix("/")
        val file = ksFile.fileName.removeSuffix(".kt")
        return "${conf.generatedDirectory}/$dir/$file.$generatedFileExtention"
    }

    fun absolutePath(filePath: String, conf: Kt2TsConfiguration): String {
        if (filePath.first() == '@') {
            // we consider it's a lib path
            return filePath
        }
        val f = removeExtension(cleanPath(filePath, conf))
        return (conf.absoluteImportPrefix?.let {
            if (it != "" && !it.endsWith("/")) {
                "$it/"
            } else {
                it
            }
        } ?: "") + f
    }

    // TODO this is hell, please refactor
    fun relativePath(filePath: String, originPath: String, conf: Kt2TsConfiguration): String {
        // TODO how do we make a difference between a lib and a path ??
        if (filePath.first() == '@') {
            // we consider it's a lib path
            return filePath
        }
        val f = cleanPath(filePath, conf)
        val o = cleanPath(originPath, conf)
        try {
            var originRoot = o
            while (!f.startsWith(originRoot)) {
                val i = originRoot.lastIndexOf("/")
                if (i == -1) {
                    originRoot = ""
                    break
                }
                originRoot = originRoot.substring(0, i)
            }
            val origin =
                if (o != originRoot && originRoot != "") {
                    o.substring(originRoot.length + 1)
                } else o
            val depth = origin.count { it == '/' }
            val start =
                if (depth == 0) "./" else (1..depth).map { "../" }.joinToString(separator = "")
            val target =
                if (f != originRoot && originRoot != "") {
                    f.substring(originRoot.length + 1)
                } else f
            return start + removeExtension(target)
        } catch (e: Exception) {
            return ""
        }
    }

    fun removeExtension(path: String): String {
        val i = path.lastIndexOf(".")
        val i2 = path.lastIndexOf("/")
        return if (i != -1 && i > i2) {
            path.substring(0, i)
        } else {
            path
        }
    }

    fun cleanPath(path: String, conf: Kt2TsConfiguration) =
        path
            .let {
                if (it.startsWith(conf.clientDirectory.pathString))
                    it.substring(conf.clientDirectory.pathString.length)
                else it
            }
            .let {
                if (it.startsWith("/")) {
                    it.drop(1)
                } else {
                    it
                }
            }
}
