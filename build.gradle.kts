
import java.util.zip.ZipFile
import java.security.MessageDigest
import groovy.json.JsonOutput
import groovy.json.JsonSlurper

plugins {
    id("com.gtnewhorizons.gtnhconvention")
}

version = providers.gradleProperty("releaseVersion").getOrElse("7.3.4")
// GTNH's generated Tags and mcmod.info read this extra property, rather than project.version.
extra["modVersion"] = version.toString()

fun gtnhDevJar(artifactId: String, version: String) =
    "com.github.GTNewHorizons:$artifactId:$version:dev"

val ae2Version: String by project
val gtnhLibVersion: String by project
val modularUi2Version: String by project
val guideNhVersion = "1.3.54"
val structureLibVersion = "1.4.47"
// Pinned to the GTNH 2.9.0 modpack set, together with AE2/AE2FC/MUI2.
val gregTechVersion = "5.09.54.264"
val ae2fcVersion = "1.5.116-gtnh"
val gtRuntime = providers.gradleProperty("gtRuntime").map(String::toBoolean).getOrElse(true)

repositories {
    exclusiveContent {
        forRepository { mavenCentral() }
        filter {
            includeModule("org.jetbrains", "annotations")
            includeGroup("org.mockito")
            includeGroup("net.bytebuddy")
            includeGroup("org.objenesis")
        }
    }
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.mockito:mockito-core:5.12.0")
    testCompileOnly("com.github.GTNewHorizons:jabel-javac-plugin:1.0.2-GTNH")
    implementation("it.unimi.dsi:fastutil:8.5.18")
    compileOnly("org.jetbrains:annotations:24.1.0")
    testImplementation("org.jetbrains:annotations:24.1.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.2")
    // Native adapters compile against the investigation's pinned APIs.
    compileOnly(gtnhDevJar("Applied-Energistics-2-Unofficial", ae2Version))
    compileOnly(gtnhDevJar("AE2FluidCraft-Rework", ae2fcVersion)) { isTransitive = false }
    compileOnly(gtnhDevJar("ModularUI2", modularUi2Version))
    compileOnly(gtnhDevJar("NotEnoughItems", "2.8.171-GTNH"))
    compileOnly(gtnhDevJar("GuideNH", guideNhVersion)) { isTransitive = false }
    // The pinned MUI2 handler API extends optional ModularUI 1 interfaces before FML strips absent-mod parents.
    compileOnly(gtnhDevJar("ModularUI", "1.3.4")) { isTransitive = false }
    // The native GT bridge is loaded only after FML confirms gregtech is installed.
    compileOnly(gtnhDevJar("GT5-Unofficial", gregTechVersion)) { isTransitive = false }
    // StructureLib is a hard dependency: its hologram projector builds ECO multiblocks and feeds NEI previews.
    compileOnly(gtnhDevJar("StructureLib", structureLibVersion)) { isTransitive = false }
    // GTNHLib is always present through AE2 and MUI2; compile against the version they resolve to.
    compileOnly(gtnhDevJar("GTNHLib", gtnhLibVersion)) { isTransitive = false }
    // The WAILA provider is reached only through WAILA's own IMC callback.
    compileOnly(gtnhDevJar("waila", "1.19.34")) { isTransitive = false }
    // Target only native mod classes at the late-loading stage; no Minecraft refmap is needed.
    compileOnly(project.extra["mixinProviderSpec"] as String)
    testImplementation(project.extra["mixinProviderSpec"] as String)
    testImplementation(gtnhDevJar("Applied-Energistics-2-Unofficial", ae2Version))
    testImplementation(gtnhDevJar("AE2FluidCraft-Rework", ae2fcVersion)) { isTransitive = false }
    testImplementation(gtnhDevJar("NotEnoughItems", "2.8.171-GTNH"))
    testImplementation(gtnhDevJar("ModularUI2", modularUi2Version))
    testImplementation(gtnhDevJar("ModularUI", "1.3.4")) { isTransitive = false }
    testRuntimeOnly(gtnhDevJar("GTNHLib", gtnhLibVersion))
    testImplementation(gtnhDevJar("StructureLib", structureLibVersion)) { isTransitive = false }
    // Bytecode contracts for optional GT monitoring; tests do not initialize GregTech.
    testRuntimeOnly(gtnhDevJar("GT5-Unofficial", gregTechVersion)) { isTransitive = false }

    runtimeOnlyNonPublishable(gtnhDevJar("Applied-Energistics-2-Unofficial", ae2Version)) {
        // Select one explicitly remapped CoFHCore implementation for GT's Thermal dependency.
        if (gtRuntime) exclude(group = "curse.maven", module = "cofh-core-69162")
    }
    runtimeOnlyNonPublishable(gtnhDevJar("GTNHLib", gtnhLibVersion))
    runtimeOnlyNonPublishable(gtnhDevJar("ModularUI2", modularUi2Version))
    runtimeOnlyNonPublishable(gtnhDevJar("StructureLib", structureLibVersion)) { isTransitive = false }
    // Required even without GregTech: ECO recipe transfer uses AE2FC's native fluid packets.
    runtimeOnlyNonPublishable(gtnhDevJar("AE2FluidCraft-Rework", ae2fcVersion)) { isTransitive = false }
    runtimeOnlyNonPublishable(gtnhDevJar("WirelessCraftingTerminal", "1.12.17")) { isTransitive = false }
    if (gtRuntime) {
        runtimeOnlyNonPublishable(gtnhDevJar("GT5-Unofficial", gregTechVersion)) { isTransitive = false }
        // Explicitly select IC2 2.2.827, the native artifact used by GTNH's dependency substitution.
        runtimeOnlyNonPublishable(rfg.deobf("curse.maven:ic2-242638:2353971"))
        runtimeOnlyNonPublishable(gtnhDevJar("ModularUI", "1.3.4")) { isTransitive = false }
        runtimeOnlyNonPublishable(gtnhDevJar("NotEnoughItems", "2.8.171-GTNH"))
        runtimeOnlyNonPublishable(gtnhDevJar("waila", "1.19.34")) { isTransitive = false }
        runtimeOnlyNonPublishable(gtnhDevJar("Postea", "1.2.7")) { isTransitive = false }
        // GT 5.09.54 replaced NotEnoughIds with FalsePattern's Chunk API and EndlessIDs (GTNH 2.9.0 set).
        runtimeOnlyNonPublishable("com.falsepattern:chunkapi-mc1.7.10:0.8.4:dev") { isTransitive = false }
        runtimeOnlyNonPublishable("com.falsepattern:endlessids-mc1.7.10:1.7.4:dev") { isTransitive = false }
        runtimeOnlyNonPublishable("com.falsepattern:falsepatternlib-mc1.7.10:1.12.2:dev") { isTransitive = false }
        // GT++ and GoodGenerator in the original GT bundle require real Thermal fluids at init.
        runtimeOnlyNonPublishable(rfg.deobf("curse.maven:cofh-core-69162:2388751"))
        runtimeOnlyNonPublishable(rfg.deobf("curse.maven:thermal-foundation-222880:2388753"))
    }
}

// In-game acceptance harnesses run only from development launches; they compile against main and never ship in the jar.
val acceptance: SourceSet = sourceSets.create("acceptance") {
    compileClasspath += sourceSets.main.get().output + sourceSets.main.get().compileClasspath
    runtimeClasspath += output + compileClasspath
}
afterEvaluate {
    val main = tasks.compileJava.get()
    tasks.named<JavaCompile>(acceptance.compileJavaTaskName) {
        javaCompiler.set(main.javaCompiler)
        sourceCompatibility = main.sourceCompatibility
        targetCompatibility = main.targetCompatibility
        options.release.set(main.options.release)
        options.encoding = main.options.encoding
        options.annotationProcessorPath = main.options.annotationProcessorPath
        options.compilerArgs.addAll(main.options.compilerArgs)
    }
}
tasks.withType<JavaExec>().matching { it.name.startsWith("run") }.configureEach {
    dependsOn(acceptance.classesTaskName)
    classpath(acceptance.output)
}

// GuideNH discovers language-prefixed Markdown pages in its native resource folder.
// Keep one editable guide source and package its images/structures alongside both languages.
tasks.processResources {
    from("src/main/resources/assets/neoecoae/ae2guide") {
        into("assets/neoecoae/guidenh")
        eachFile {
            val sourcePath = relativeSourcePath.pathString
            path = "assets/neoecoae/guidenh/" +
                (if (sourcePath.startsWith("neoecoae_intro/")) "_en_us/$sourcePath" else sourcePath)
        }
        includeEmptyDirs = false
    }
}

// The pinned GT++ bundle reads Thermal fluids in preInit, but omits this load-order edge.
// Use Forge's development config instead of patching either external mod or registering fake fluids.
tasks.withType<JavaExec>().matching { it.name.matches(Regex("run(Client|Server)(17|21|25)?")) }.configureEach {
    if (name.startsWith("runClient") && providers.gradleProperty("transmitterRenderAcceptance").map(String::toBoolean).getOrElse(false)) {
        systemProperty("neoecoae.transmitterRenderAcceptance", "true")
        systemProperty("neoecoae.renderLabel", providers.gradleProperty("transmitterRenderLabel").getOrElse("after"))
        workingDir = file("run/transmitter-render-acceptance")
        doFirst {
            workingDir.mkdirs()
            val options = workingDir.resolve("options.txt")
            if (!options.exists()) options.writeText("lang:en_US\noverrideWidth:1280\noverrideHeight:900\npauseOnLostFocus:false\nrenderDistance:4\nmipmapLevels:0\n")
        }
    }
    if (name.startsWith("runClient") && providers.gradleProperty("launchAcceptance").map(String::toBoolean).getOrElse(false)) {
        systemProperty("neoecoae.launchAcceptance", "true")
        workingDir = file("run/launch-acceptance")
        doFirst {
            workingDir.mkdirs()
            val options = workingDir.resolve("options.txt")
            if (!options.exists()) options.writeText("lang:zh_CN\n")
        }
    }
    doFirst {
        if (gtRuntime) {
            val dependencyFile = workingDir.resolve("config/injectedDependencies.json")
            val entries = if (dependencyFile.isFile) {
                JsonSlurper().parse(dependencyFile) as List<*>
            } else emptyList<Any>()
            val dependencies = entries.toMutableList()
            val hasThermalOrder = entries.filterIsInstance<Map<*, *>>().any { entry ->
                entry["modId"] == "miscutils" && (entry["deps"] as? List<*>)
                    .orEmpty().filterIsInstance<Map<*, *>>().any { it["type"] == "after" && it["target"] == "ThermalFoundation" }
            }
            if (!hasThermalOrder) {
                dependencies.add(mapOf("modId" to "miscutils", "deps" to listOf(mapOf("type" to "after", "target" to "ThermalFoundation"))))
                dependencyFile.parentFile.mkdirs()
                dependencyFile.writeText(JsonOutput.prettyPrint(JsonOutput.toJson(dependencies)) + "\n")
            }
        }
    }
}

val verifyGtRuntimeProfile by tasks.registering {
    group = "verification"
    description = "Verify the native GregTech development runtime."
    doLast {
        check(gtRuntime) { "The native GregTech development runtime is disabled by -PgtRuntime=false" }
        val runtime = configurations.runtimeClasspath.get()
        val artifacts = runtime.resolvedConfiguration.resolvedArtifacts
        // RFG replaces IC2's module artifact with a transformed file dependency. Inspect real JAR
        // contents too, so dependency substitution cannot produce a false positive or negative.
        val clientRuntime = (tasks.named("runClient").get() as JavaExec).classpath.files
        val jars = clientRuntime.filter { it.isFile && it.extension == "jar" }
        val requiredClasses = mapOf(
            "GT5-Unofficial" to "gregtech/GTMod.class",
            "StructureLib" to "com/gtnewhorizon/structurelib/StructureLib.class",
            "industrialcraft-2" to "ic2/core/IC2.class",
            "ModularUI" to "com/gtnewhorizons/modularui/ModularUI.class",
            "NotEnoughItems" to "codechicken/nei/NEIModContainer.class",
            "AE2FluidCraft-Rework" to "com/glodblock/github/FluidCraft.class",
            "WirelessCraftingTerminal" to "net/p455w0rd/wirelesscraftingterminal/common/WirelessCraftingTerminal.class",
            "waila" to "mcp/mobius/waila/Waila.class",
            "Postea" to "com/gtnewhorizons/postea/utility/BlockInfo.class",
            "chunkapi-mc1.7.10" to "com/falsepattern/chunk/api/DataManager.class",
            "endlessids-mc1.7.10" to "com/falsepattern/endlessids/EndlessIDs.class",
            "falsepatternlib-mc1.7.10" to "com/falsepattern/lib/StableAPI.class",
            "CoFHCore" to "cofh/CoFHCore.class",
            "ThermalFoundation" to "cofh/thermalfoundation/ThermalFoundation.class"
        )
        val verified = mutableListOf<Map<String, String>>()
        for ((required, entry) in requiredClasses) {
            val matches = jars.filter { jar -> ZipFile(jar).use { it.getEntry(entry) != null } }
            check(matches.size == 1) {
                "Expected one native $required runtime implementation, found ${matches.map { it.name }}"
            }
            val jar = matches.single()
            if (required == "industrialcraft-2") {
                val metadata = ZipFile(jar).use { archive ->
                    archive.getInputStream(archive.getEntry("mcmod.info")).reader().use { JsonSlurper().parse(it) }
                } as List<*>
                val ic2 = metadata.map { it as Map<*, *> }.single { it["modid"] == "IC2" }
                check(ic2["version"] == "2.2.827-experimental") { "Unexpected native IC2 runtime: ${ic2["version"]}" }
            }
            val sha256 = MessageDigest.getInstance("SHA-256").digest(jar.readBytes())
                .joinToString("") { "%02x".format(it) }
            verified.add(mapOf("module" to required, "required_class" to entry, "file" to jar.name,
                "path" to jar.absolutePath, "sha256" to sha256))
            logger.lifecycle("NEOECOAE_GT_RUNTIME_CLASS {} {} sha256={}", entry, jar.name, sha256)
        }
        val modules = artifacts.map { it.moduleVersion.id }.distinct()
            .filter { it.name in setOf("GT5-Unofficial", "StructureLib", "industrialcraft-2", "ModularUI", "NotEnoughItems", "AE2FluidCraft-Rework", "WirelessCraftingTerminal", "waila", "GTNHLib", "Applied-Energistics-2-Unofficial", "ModularUI2", "Postea", "chunkapi-mc1.7.10", "endlessids-mc1.7.10", "falsepatternlib-mc1.7.10") }
            .sortedBy { it.name }
        for ((name, version) in mapOf(
            "Applied-Energistics-2-Unofficial" to ae2Version,
            "ModularUI2" to modularUi2Version,
            "GT5-Unofficial" to gregTechVersion,
            "StructureLib" to structureLibVersion,
            "GTNHLib" to gtnhLibVersion,
            "ModularUI" to "1.3.4",
            "NotEnoughItems" to "2.8.171-GTNH",
            "AE2FluidCraft-Rework" to ae2fcVersion,
            "WirelessCraftingTerminal" to "1.12.17",
            "waila" to "1.19.34",
            "Postea" to "1.2.7",
            "chunkapi-mc1.7.10" to "0.8.4",
            "endlessids-mc1.7.10" to "1.7.4",
            "falsepatternlib-mc1.7.10" to "1.12.2"
        )) {
            check(modules.filter { it.name == name }.map { it.version }.toSet() == setOf(version)) {
                "Native GT profile changed pinned module $name:$version"
            }
        }
        modules.forEach { logger.lifecycle("NEOECOAE_GT_RUNTIME_ARTIFACT {}", it) }
        val report = layout.buildDirectory.file("reports/native-gt-runtime.json").get().asFile
        report.parentFile.mkdirs()
        report.writeText(JsonOutput.prettyPrint(JsonOutput.toJson(mapOf("verified_jars" to verified, "resolved_modules" to modules.map { it.toString() }))) + "\n")
    }
}

tasks.test {
    useJUnitPlatform()
    systemProperty("planner.challenge.enabled", providers.gradleProperty("plannerChallenge").getOrElse("false"))
    systemProperty("planner.challenge.profile", providers.gradleProperty("plannerChallengeProfile").getOrElse("default"))
}

// Development names hide collisions between IInventory's remapped methods and custom interfaces.
val verifyReobfuscatedInventory by tasks.registering(Test::class) {
    group = "verification"
    description = "Exercise the item-handler adapter from the actual release JAR."
    dependsOn(tasks.named("reobfJar"), tasks.named("testClasses"))
    inputs.files(tasks.named("reobfJar"))
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform()
    filter {
        includeTestsMatching("cn.dancingsnow.neoecoae.platform.inventory.AppEngInternalInventoryTest.releaseHandlerResolvesTheRemappedNativeGetter")
    }
    doFirst {
        systemProperty("neoecoae.reobfJar", tasks.named("reobfJar").get().outputs.files.singleFile.absolutePath)
    }
}

tasks.check {
    dependsOn(verifyReobfuscatedInventory)
}
