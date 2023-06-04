package kt2ts.kspgenerator.utils

object Debug {

    val sb = StringBuilder()

    fun add(s: String?) {
        sb.appendLine(s)
    }
}
