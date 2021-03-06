package org.jetbrains.research.kex

import com.abdullin.kthelper.logging.log
import org.jetbrains.research.kex.asm.analysis.MethodChecker
import org.jetbrains.research.kex.asm.analysis.RandomChecker
import org.jetbrains.research.kex.asm.manager.CoverageCounter
import org.jetbrains.research.kex.asm.state.PredicateStateAnalysis
import org.jetbrains.research.kex.asm.transform.LoopDeroller
import org.jetbrains.research.kex.asm.transform.RuntimeTraceCollector
import org.jetbrains.research.kex.asm.util.ClassWriter
import org.jetbrains.research.kex.config.CmdConfig
import org.jetbrains.research.kex.config.FileConfig
import org.jetbrains.research.kex.config.RuntimeConfig
import org.jetbrains.research.kex.config.kexConfig
import org.jetbrains.research.kex.generator.MethodFieldAccessDetector
import org.jetbrains.research.kex.generator.SetterDetector
import org.jetbrains.research.kex.random.easyrandom.EasyRandomDriver
import org.jetbrains.research.kex.trace.`object`.ObjectTraceManager
import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.Jar
import org.jetbrains.research.kfg.KfgConfig
import org.jetbrains.research.kfg.Package
import org.jetbrains.research.kfg.analysis.LoopSimplifier
import org.jetbrains.research.kfg.ir.Class
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.util.Flags
import org.jetbrains.research.kfg.visitor.Pipeline
import org.jetbrains.research.kfg.visitor.executePipeline
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.jar.JarEntry
import java.util.jar.JarFile
import kotlin.system.exitProcess

class InputGenerator(args: Array<String>) {
    var inputs: Map<String, Set<Array<Any?>>> = mapOf()

    val originalContext: ExecutionContext
    val analysisContext: ExecutionContext
    val jarPath: Path
    val classLoader: URLClassLoader


    val cmd = CmdConfig(args)
    val properties = cmd.getCmdValue("config", "kex.ini")
    val logName = cmd.getCmdValue("log", "kex.log")
    val config = kexConfig
    val classPath = System.getProperty("java.class.path")

    val jar: Jar
    val outputDir: Path

    val classManager: ClassManager
    val origManager: ClassManager

    lateinit var `package`: Package
    var klass: Class? = null
    var methods: Set<Method>? = null

    private sealed class AnalysisLevel {
        class PACKAGE : AnalysisLevel()
        data class CLASS(val klass: String) : AnalysisLevel()
        data class METHOD(val klass: String, val method: String) : AnalysisLevel()
    }

    init {
        kexConfig.initialize(cmd, RuntimeConfig, FileConfig(properties))
        kexConfig.initLog(logName)

        val jarName = cmd.getCmdValue("jar")
        require(jarName != null, cmd::printHelp)

        jarPath = Paths.get(jarName).toAbsolutePath()

        jar = Jar(jarPath, Package.defaultPackage)

        classManager = ClassManager(KfgConfig(flags = Flags.readAll, failOnError = false))
        origManager = ClassManager(KfgConfig(flags = Flags.readAll, failOnError = false))
        val analysisJars = listOfNotNull(
                jar,
                kexConfig.getStringValue("kex", "rtPath")?.let {
                    Jar(Paths.get(it), Package.defaultPackage)
                }
        )
        classManager.initialize(*analysisJars.toTypedArray())
        origManager.initialize(*analysisJars.toTypedArray())

        outputDir = (cmd.getCmdValue("output")?.let { Paths.get(it) }
                ?: Files.createTempDirectory(Paths.get("."), "kex-instrumented")).toAbsolutePath()

        jar.unpack(ClassManager(KfgConfig(flags = Flags.readAll, failOnError = false)), outputDir, true)

        classLoader = URLClassLoader(arrayOf(outputDir.toUri().toURL()))

        originalContext = ExecutionContext(origManager, jar.classLoader, EasyRandomDriver())
        analysisContext = ExecutionContext(classManager, classLoader, EasyRandomDriver())

        val namesOfPackages = mutableSetOf<String>()
        val enumeration = JarFile(jarPath.toFile()).entries()
        while (enumeration.hasMoreElements())
            namesOfPackages.add("${(enumeration.nextElement() as JarEntry).name.substringBefore('/')}.*")
        namesOfPackages.remove("META-INF.*")

        for (packageName in namesOfPackages)
            runPipeline(originalContext, Package.parse(packageName)) {
                +RuntimeTraceCollector(originalContext.cm)
                +ClassWriter(originalContext, outputDir)
            }
    }

    fun generateInputs(targetName: String) {
        val analysisLevel = when {
            targetName.matches(Regex("[a-zA-Z\\d]+(\\.[a-zA-Z\\d]+)*\\.\\*")) -> {
                `package` = Package.parse(targetName)
                AnalysisLevel.PACKAGE()
            }
            targetName.matches(Regex("[a-zA-Z\\d]+(\\.[a-zA-Z\\d]+)*\\.[a-zA-Z\$_\\d]+::[a-zA-Z\$_\\d]+")) -> {
                val (klassName, methodName) = targetName.split("::")
                `package` = Package.parse("${klassName.dropLastWhile { it != '.' }}*")
                AnalysisLevel.METHOD(klassName.replace('.', '/'), methodName)
            }
            targetName.matches(Regex("[a-zA-Z\\d]+(\\.[a-zA-Z\\d]+)*\\.[a-zA-Z\$_\\d]+")) -> {
                `package` = Package.parse("${targetName.dropLastWhile { it != '.' }}*")
                AnalysisLevel.CLASS(targetName.replace('.', '/'))
            }
            else -> {
                log.error("Could not parse target $targetName")
                exitProcess(1)
            }
        }
        when (analysisLevel) {
            is AnalysisLevel.CLASS -> klass = classManager[analysisLevel.klass]
            is AnalysisLevel.METHOD -> {
                klass = classManager[analysisLevel.klass]
                methods = klass!!.getMethods(analysisLevel.method)
            }
        }

        bmc(originalContext, analysisContext)

        klass = null
        methods = null
    }

    private fun updateClassPath(loader: URLClassLoader) {
        val urlClassPath = loader.urLs.joinToString(separator = ":") { "${it.path}." }
        System.setProperty("java.class.path", "$classPath:$urlClassPath")
    }

    private fun clearClassPath() {
        System.setProperty("java.class.path", classPath)
    }

    private fun bmc(originalContext: ExecutionContext, analysisContext: ExecutionContext) {
        val traceManager = ObjectTraceManager()
        val psa = PredicateStateAnalysis(analysisContext.cm)
        val cm = CoverageCounter(originalContext.cm, traceManager)

        updateClassPath(analysisContext.loader as URLClassLoader)
        runPipeline(analysisContext) {
            +RandomChecker(analysisContext, traceManager)
            +LoopSimplifier(analysisContext.cm)
            +LoopDeroller(analysisContext.cm)
            +psa
            +MethodFieldAccessDetector(analysisContext, psa)
            +SetterDetector(analysisContext)
            +MethodChecker(analysisContext, traceManager, psa)
            +cm
        }
        clearClassPath()

        val coverage = cm.totalCoverage
        log.info("Overall summary for ${cm.methodInfos.size} methods:\n" +
                "body coverage: ${String.format("%.2f", coverage.bodyCoverage)}%\n" +
                "full coverage: ${String.format("%.2f", coverage.fullCoverage)}%")

        inputs = traceManager.getMapOfInputs()
    }

    protected fun runPipeline(context: ExecutionContext, target: Package, init: Pipeline.() -> Unit) =
            executePipeline(context.cm, target, init)

    protected fun runPipeline(context: ExecutionContext, init: Pipeline.() -> Unit) = when {
        methods != null -> executePipeline(context.cm, methods!!, init)
        klass != null -> executePipeline(context.cm, klass!!, init)
        else -> executePipeline(context.cm, `package`, init)
    }
}