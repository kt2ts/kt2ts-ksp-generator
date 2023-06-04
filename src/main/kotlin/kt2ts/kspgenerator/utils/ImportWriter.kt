package kt2ts.kspgenerator.utils

import com.google.devtools.ksp.symbol.KSFile
import kotlin.io.path.pathString

object ImportWriter {

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
        return "${conf.generatedDirectory}/$dir/$file.ts"
    }

    // TODO this is hell, please refactor
    fun relativePath(filePath: String, originPath: String, conf: Kt2TsConfiguration): String {
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
            Debug.add("=> KO")
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
