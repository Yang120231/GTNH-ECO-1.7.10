
plugins {
    id("com.gtnewhorizons.gtnhconvention")
}

version = providers.gradleProperty("releaseVersion").getOrElse("0.1.0-porting")
// GTNH's generated Tags and mcmod.info read this extra property, rather than project.version.
extra["modVersion"] = version.toString()

fun gtnhDevJar(artifactId: String, version: String) =
    "com.github.GTNewHorizons:$artifactId:$version:dev"

val ae2Version: String by project
val gtnhLibVersion: String by project
val modularUi2Version: String by project

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
    implementation("it.unimi.dsi:fastutil:8.5.9")
    compileOnly("org.jetbrains:annotations:24.1.0")
    testImplementation("org.jetbrains:annotations:24.1.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.2")
    // Native adapters compile against the investigation's pinned APIs.
    compileOnly(gtnhDevJar("Applied-Energistics-2-Unofficial", ae2Version))
    compileOnly(gtnhDevJar("ModularUI2", modularUi2Version))
    // The pinned MUI2 handler API extends optional ModularUI 1 interfaces before FML strips absent-mod parents.
    compileOnly(gtnhDevJar("ModularUI", "1.3.2")) { isTransitive = false }
    // Target only native mod classes at the late-loading stage; no Minecraft refmap is needed.
    compileOnly(project.extra["mixinProviderSpec"] as String)
    testImplementation(gtnhDevJar("Applied-Energistics-2-Unofficial", ae2Version))
    testImplementation(gtnhDevJar("ModularUI2", modularUi2Version))
    testImplementation(gtnhDevJar("ModularUI", "1.3.2")) { isTransitive = false }
    testRuntimeOnly(gtnhDevJar("GTNHLib", gtnhLibVersion))

    runtimeOnlyNonPublishable(gtnhDevJar("Applied-Energistics-2-Unofficial", ae2Version))
    runtimeOnlyNonPublishable(gtnhDevJar("GTNHLib", gtnhLibVersion))
    runtimeOnlyNonPublishable(gtnhDevJar("ModularUI2", modularUi2Version))
}

tasks.test {
    useJUnitPlatform()
}

val verifyMigratedSourceCoverage by tasks.registering {
    group = "verification"
    description = "Verify every imported business and test source is included in its real JavaCompile task."
    dependsOn(tasks.named("compileJava"), tasks.named("compileTestJava"))
    inputs.file("porting/source-lock.json")
    doLast {
        val lock = groovy.json.JsonSlurper().parse(file("porting/source-lock.json")) as Map<*, *>
        val imported = lock["imported"] as List<*>
        for (value in imported) {
            val entry = value as Map<*, *>
            val kind = entry["kind"] as String
            if (kind != "business" && kind != "test") continue
            val target = file(entry["target"] as String).canonicalFile
            val taskName = if (kind == "test") "compileTestJava" else "compileJava"
            val compilation = tasks.named<JavaCompile>(taskName).get()
            check(compilation.source.files.any { it.canonicalFile == target }) {
                "Imported source excluded from $taskName: $target"
            }
            check(target.isFile) { "Missing imported source: $target" }
            if (target.name != "package-info.java") {
                val base = if (kind == "test") "src/test/java/" else "src/main/java/"
                val classPath = (entry["target"] as String).removePrefix(base).removeSuffix(".java") + ".class"
                check(compilation.destinationDirectory.file(classPath).get().asFile.isFile) {
                    "No compiled class for imported source: $target"
                }
            }
        }
    }
}

tasks.named("check") { dependsOn(verifyMigratedSourceCoverage) }
