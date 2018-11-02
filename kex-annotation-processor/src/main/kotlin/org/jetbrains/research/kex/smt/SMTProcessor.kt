package org.jetbrains.research.kex.smt

import org.jetbrains.research.kex.KexProcessor
import org.jetbrains.research.kex.util.unreachable
import java.io.File
import javax.annotation.processing.*
import javax.lang.model.SourceVersion
import javax.lang.model.element.Element
import javax.lang.model.element.TypeElement
import kotlin.reflect.KClass

@SupportedSourceVersion(SourceVersion.RELEASE_8)
@SupportedAnnotationTypes(
        "org.jetbrains.research.kex.smt.SMTExpr",
        "org.jetbrains.research.kex.smt.SMTMemory",
        "org.jetbrains.research.kex.smt.SMTExprFactory",
        "org.jetbrains.research.kex.smt.SMTContext",
        "org.jetbrains.research.kex.smt.SMTConverter")
@SupportedOptions("codegen.dir", "template.dir")
class SMTProcessor : KexProcessor() {
    private companion object {
        const val CODEGEN_DIR = "codegen.dir"
        const val TEMPLATE_DIR = "template.dir"
    }

    private val targetDirectory: String
        get() = processingEnv.options[CODEGEN_DIR] ?: unreachable { error("No codegen directory") }

    private val templates: String
        get() = processingEnv.options[TEMPLATE_DIR] ?: unreachable { error("No template directory") }

    override fun process(annotations: MutableSet<out TypeElement>?, roundEnv: RoundEnvironment?): Boolean {
        roundEnv?.apply {
            getElementsAnnotatedWith(SMTExpr::class.java)?.forEach {
                processAnnotation(it, SMTExpr::class, "Expr")
            }
            getElementsAnnotatedWith(SMTMemory::class.java)?.forEach {
                processAnnotation(it, SMTMemory::class, "Memory")
            }
            getElementsAnnotatedWith(SMTExprFactory::class.java)?.forEach {
                processAnnotation(it, SMTExprFactory::class, "ExprFactory")
            }
            getElementsAnnotatedWith(SMTContext::class.java)?.forEach {
                processAnnotation(it, SMTContext::class, "Context")
            }
            getElementsAnnotatedWith(SMTConverter::class.java)?.forEach {
                processAnnotation(it, SMTConverter::class, "Converter")
            }
        }
        return true
    }

    private fun <T : Annotation> processAnnotation(element: Element, annotationClass: KClass<T>, nameTemplate: String) {
        val `class` = element.simpleName.toString()
        val `package` = processingEnv.elementUtils.getPackageOf(element).toString()
        val annotation = element.getAnnotation(annotationClass.java)
                ?: unreachable { error("Element $element have no annotation $annotationClass") }

        val parameters = getAnnotationProperties(annotation, annotationClass).toMutableMap()
        val solver = parameters.getValue("solver") as String
        val newPackage = "$`package`.${solver.toLowerCase()}"
        val newClass = "$solver$nameTemplate"

        parameters["packageName"] = newPackage

        info("Generating $nameTemplate for $`class` in package $`package` with parameters $parameters")
        writeClass(newPackage, newClass, parameters, "SMT$nameTemplate")
    }

    private fun writeClass(`package`: String, `class`: String, parameters: Map<String, Any>, template: String) {
        val file = File("$targetDirectory/${`package`.replace('.', '/')}/$`class`.kt")
        if (!file.parentFile.exists()) file.parentFile.mkdirs()
        val fileWriter = file.writer()
        ClassGenerator(parameters, templates, "$template.vm").doit(fileWriter)
        fileWriter.close()
    }
}