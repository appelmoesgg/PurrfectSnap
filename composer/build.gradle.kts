import org.apache.tools.ant.taskdefs.condition.Os
plugins {
    alias(libs.plugins.androidLibrary)
}

android {
    namespace = rootProject.ext["applicationId"].toString() + ".composer"
    compileSdk = 35

    sourceSets {
        getByName("main") {
            assets.srcDirs("build/assets")
        }
    }
}

tasks.register("installTypeScript", org.gradle.api.tasks.Exec::class) {
    if (Os.isFamily(Os.FAMILY_WINDOWS)) {
        commandLine("npm.cmd", "install", "typescript")
    } else {
        commandLine("npm", "install", "typescript")
    }
    doLast {
        if (!Os.isFamily(Os.FAMILY_WINDOWS)) {
            val tscScript = project.file("node_modules/.bin/tsc")
            if (tscScript.exists()) {
                tscScript.setExecutable(true)
            }
        }
    }
}

tasks.register("compileTsc", org.gradle.api.tasks.Exec::class) {
    dependsOn("installTypeScript")
    val existingNodeOptions = System.getenv("NODE_OPTIONS")?.takeIf { it.isNotBlank() }
    val nodeOptions = listOfNotNull(existingNodeOptions, "--max-old-space-size=4096").joinToString(" ")
    environment("NODE_OPTIONS", nodeOptions)
    if (Os.isFamily(Os.FAMILY_WINDOWS)) {
        commandLine("npx.cmd", "--yes", "tsc", "--project", "tsconfig.json")
    } else {
        commandLine("npx", "--yes", "tsc", "--project", "tsconfig.json")
    }
}

tasks.register("compileRollup", org.gradle.api.tasks.Exec::class) {
    dependsOn("compileTsc")
    val existingNodeOptions = System.getenv("NODE_OPTIONS")?.takeIf { it.isNotBlank() }
    val nodeOptions = listOfNotNull(existingNodeOptions, "--max-old-space-size=4096").joinToString(" ")
    environment("NODE_OPTIONS", nodeOptions)
    if (Os.isFamily(Os.FAMILY_WINDOWS)) {
        commandLine("npx.cmd", "--yes", "rollup", "--config", "rollup.config.js", "--bundleConfigAsCjs")
    } else {
        commandLine("npx", "--yes", "rollup", "--config", "rollup.config.js", "--bundleConfigAsCjs")
    }
}

tasks.register<org.gradle.api.tasks.Sync>("syncComposerAssets") {
    dependsOn("compileRollup")
    from(layout.projectDirectory.file("build/loader.js"))
    into(layout.projectDirectory.dir("build/assets/composer"))
}

tasks.named("preBuild").configure {
    dependsOn("syncComposerAssets")
}