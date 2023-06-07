package kt2ts.kspgenerator.utils

import java.io.InputStream
import java.nio.file.Path
import java.util.Scanner

object ShellRunner {

    fun run(directory: Path, command: String, vararg params: String) =
        doRun(directory, command, *params)

    private fun doRun(directory: Path?, command: String, vararg params: String) {
        val builder =
            ProcessBuilder().apply {
                environment().apply {
                    val addToPath = listOf("/usr/local/bin")
                    put("PATH", "${get("PATH")}:${addToPath.joinToString(separator = ":")}")
                }
                if (directory != null) {
                    directory(directory.toFile())
                }
            }
        val fullCommand = command + params.fold("") { acc, s -> "$acc $s" }
        builder.command("sh", "-c", fullCommand)
        builder.start()
    }

    fun outputThread(inputStream: InputStream, logPrefix: String): Pair<List<String>, Thread> {
        val result = mutableListOf<String>()
        val t =
            Thread {
                    val s = Scanner(inputStream)
                    while (s.hasNextLine()) {
                        val l = s.nextLine()
                        result.add(l)
                    }
                }
                .apply { start() }
        return result to t
    }
}
