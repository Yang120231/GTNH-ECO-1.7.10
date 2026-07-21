
plugins {
    id("com.gtnewhorizons.gtnhconvention")
}

version = "7.2.0"

fun gtnhDevJar(artifactId: String, version: String) =
    "com.github.GTNewHorizons:$artifactId:$version:dev"

fun gtnhApiJar(artifactId: String, version: String) =
    "com.github.GTNewHorizons:$artifactId:$version:api"

val ae2Version: String by project
val structureLibVersion: String by project
val notEnoughItemsVersion: String by project
val gtnhLibVersion: String by project
val modularUi2Version: String by project
val gregTechVersion: String by project
val wailaVersion: String by project

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.2")
    testCompileOnly(gtnhDevJar("Applied-Energistics-2-Unofficial", ae2Version))
    testRuntimeOnly(gtnhDevJar("Applied-Energistics-2-Unofficial", ae2Version))

    // Match E:\Minecraft Project\ECO-1.7.10: use explicit stable GTNH coordinates instead of the daily catalog.
    compileOnly(gtnhApiJar("Applied-Energistics-2-Unofficial", ae2Version))
    compileOnly(gtnhDevJar("Applied-Energistics-2-Unofficial", ae2Version))
    compileOnly(gtnhDevJar("StructureLib", structureLibVersion))
    compileOnly(gtnhDevJar("NotEnoughItems", notEnoughItemsVersion))
    compileOnly(gtnhDevJar("ModularUI2", modularUi2Version))
    compileOnly(gtnhDevJar("GT5-Unofficial", gregTechVersion))
    compileOnly(gtnhDevJar("waila", wailaVersion))

    runtimeOnlyNonPublishable(gtnhDevJar("Applied-Energistics-2-Unofficial", ae2Version))
    runtimeOnlyNonPublishable(gtnhDevJar("StructureLib", structureLibVersion))
    runtimeOnlyNonPublishable(gtnhDevJar("GTNHLib", gtnhLibVersion))
    runtimeOnlyNonPublishable(gtnhDevJar("ModularUI2", modularUi2Version))
    runtimeOnlyNonPublishable(gtnhDevJar("GT5-Unofficial", gregTechVersion))
    runtimeOnlyNonPublishable(gtnhDevJar("waila", wailaVersion))
}

tasks.test {
    useJUnitPlatform()
}
