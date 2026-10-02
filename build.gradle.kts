import xyz.jpenilla.resourcefactory.paper.PaperPluginYaml

plugins {
    id("java")

    alias(libs.plugins.lombok)
    alias(libs.plugins.shadow)
    alias(libs.plugins.resourceFactoryPaperConvention)
}

group = "net.inpvp"
version = System.getenv("GITHUB_REF_NAME")?.removePrefix("v") ?: "local-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.extendedclip.com/releases/")
    maven("https://repo.aikar.co/content/groups/aikar/")
    maven("https://repo.xenondevs.xyz/releases")
}

dependencies {
    compileOnly(libs.paperApi)
    compileOnly(libs.placeholderApi)
    compileOnly(libs.jspecify)

    implementation(libs.acfPaper)
    implementation(libs.configlibYaml)
    implementation(libs.jacksonDatabind)
    implementation(libs.jacksonJsr310)
    implementation(libs.hikari)
    implementation(libs.invui)

    implementation(libs.sqliteJdbc)
    implementation(libs.mariadbJdbc)
}

configurations.runtimeClasspath {
    exclude(group = "org.slf4j", module = "slf4j-api")
    exclude(group = "com.google.errorprone", module = "error_prone_annotations")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

paperPluginYaml {
    name = rootProject.name
    description = "Battlepass style daily rewards for players joining through the Dawn Client"
    authors = listOf("InPvP", "SenseiJu")
    website = "https://dawn.gg"
    apiVersion = "26.1.2"
    main = "net.inpvp.dawnrewards.DawnRewards"
    version = project.version.toString()
    dependencies {
        server("PlaceholderAPI", PaperPluginYaml.Load.BEFORE, required = false)
    }
}

tasks {
    withType<JavaCompile> {
        options.encoding = "UTF-8"
    }

    build {
        dependsOn("shadowJar")
    }

    shadowJar {
        archiveBaseName.set(rootProject.name)
        archiveVersion.set(project.version.toString())
        archiveClassifier.set("")
        duplicatesStrategy = DuplicatesStrategy.INCLUDE

        val shadePath = "net.inpvp.dawnrewards.shaded"

        relocate("co.aikar", "$shadePath.acf")
        relocate("de.exlll.configlib", "$shadePath.configlib")
        relocate("com.fasterxml.jackson", "$shadePath.jackson")
        relocate("org.snakeyaml.engine", "$shadePath.snakeyaml")
        relocate("com.zaxxer.hikari", "$shadePath.hikari")
        relocate("xyz.xenondevs", "$shadePath.invui")
        relocate("com.google.gson", "$shadePath.gson")
        relocate("org.json", "$shadePath.json")


        mergeServiceFiles()
    }
}
