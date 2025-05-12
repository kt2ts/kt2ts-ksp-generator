package kt2ts.kspgenerator.utils

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSNode
import com.google.devtools.ksp.visitor.KSEmptyVisitor

// TODO should be used ?
class Kt2TsVisitor : KSEmptyVisitor<Set<ClassParser.Parsed>, Set<ClassParser.Parsed>>() {

    override fun defaultHandler(node: KSNode, data: Set<ClassParser.Parsed>) = data

    override fun visitClassDeclaration(d: KSClassDeclaration, data: Set<ClassParser.Parsed>) =
        TODO() // ClassParser.parse(d.asStarProjectedType(), data)
}
