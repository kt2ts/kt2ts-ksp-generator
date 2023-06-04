package kt2ts.kspgenerator.utils

import com.google.devtools.ksp.symbol.KSTypeReference

object Kt2TsTypesConvertor {

    fun convert(type: KSTypeReference): String {
        val fullType = type.resolve().declaration.qualifiedName?.asString()
        return when (fullType) {
            "kotlin.String" -> "string"
            else -> fullType ?: "any"
        }
    }
}
