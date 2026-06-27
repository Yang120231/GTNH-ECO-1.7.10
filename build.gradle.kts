
plugins {
    id("com.gtnewhorizons.gtnhconvention")
}

fun gtnhDevJar(artifactId: String, version: String) =
    "com.github.GTNewHorizons:$artifactId:$version:dev"

fun gtnhApiJar(artifactId: String, version: String) =
    "com.github.GTNewHorizons:$artifactId:$version:api"

val ae2Version: String by project
val structureLibVersion: String by project
val notEnoughItemsVersion: String by project
val gtnhLibVersion: String by project

dependencies {
    // Match E:\Minecraft Project\ECO-1.7.10: use explicit stable GTNH coordinates instead of the daily catalog.
    compileOnly(gtnhApiJar("Applied-Energistics-2-Unofficial", ae2Version))
    compileOnly(gtnhDevJar("Applied-Energistics-2-Unofficial", ae2Version))
    compileOnly(gtnhDevJar("StructureLib", structureLibVersion))
    compileOnly(gtnhDevJar("NotEnoughItems", notEnoughItemsVersion))

    runtimeOnlyNonPublishable(gtnhDevJar("Applied-Energistics-2-Unofficial", ae2Version))
    runtimeOnlyNonPublishable(gtnhDevJar("StructureLib", structureLibVersion))
    runtimeOnlyNonPublishable(gtnhDevJar("GTNHLib", gtnhLibVersion))
}
