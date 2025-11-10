import java.io.File
import java.util.Locale
import java.util.Properties
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.Sync

data class CargoTarget(
    val triple: String,
    val abi: String,
    val toolchainPrefix: String,
    val apiLevel: Int
) {
    val envSuffix: String = triple.replace('-', '_')
    val envSuffixUpper: String = envSuffix.uppercase(Locale.ROOT)
    val taskSuffix: String = abi.split('-', '_').joinToString("") { fragment ->
        fragment.replaceFirstChar { ch -> ch.titlecase(Locale.ROOT) }
    }
}

plugins {
    alias(libs.plugins.androidLibrary)
}

val nativeBuildHash = rootProject.ext.get("buildHash").toString()
val nativeLibFileName = "lib${nativeBuildHash}.so"

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}

val ndkHomePath = (System.getenv("ANDROID_NDK_HOME")?.trimEnd('/', '\\')
    ?: localProperties.getProperty("ndk.dir")?.trimEnd('/', '\\')
    ?: error("Unable to locate the Android NDK. Set ANDROID_NDK_HOME or define ndk.dir in local.properties"))
    .replace("\\:", ":")
val ndkHome = File(ndkHomePath)
require(ndkHome.exists()) { "Configured NDK directory $ndkHome does not exist" }

val osName = System.getProperty("os.name").lowercase(Locale.ROOT)
val osArch = System.getProperty("os.arch").lowercase(Locale.ROOT)
val hostTag = when {
    osName.contains("windows") -> "windows-x86_64"
    osName.contains("mac") && osArch.contains("arm") -> "darwin-arm64"
    osName.contains("mac") -> "darwin-x86_64"
    else -> "linux-x86_64"
}

val toolchainBin = File(ndkHome, "toolchains/llvm/prebuilt/$hostTag/bin")
require(toolchainBin.exists()) { "Unable to locate NDK toolchain under $toolchainBin" }

val isWindowsHost = hostTag.startsWith("windows")
val clangSuffix = if (isWindowsHost) ".cmd" else ""
val llvmArExecutable = File(toolchainBin, if (isWindowsHost) "llvm-ar.exe" else "llvm-ar")
require(llvmArExecutable.exists()) { "Unable to find llvm-ar executable at ${llvmArExecutable.absolutePath}" }
val toolchainPath = listOf(toolchainBin.absolutePath, System.getenv("PATH") ?: "")
    .filter { it.isNotBlank() }
    .joinToString(File.pathSeparator)

val cargoTargets = listOf(
    CargoTarget(
        triple = "aarch64-linux-android",
        abi = "arm64-v8a",
        toolchainPrefix = "aarch64-linux-android",
        apiLevel = 28,
    ),
    CargoTarget(
        triple = "armv7-linux-androideabi",
        abi = "armeabi-v7a",
        toolchainPrefix = "armv7a-linux-androideabi",
        apiLevel = 28,
    ),
)

fun clangExecutableFor(target: CargoTarget): File {
    val executable = File(toolchainBin, "${target.toolchainPrefix}${target.apiLevel}-clang$clangSuffix")
    require(executable.exists()) { "Unable to find clang executable at ${executable.absolutePath}" }
    return executable
}

fun clangPlusPlusExecutableFor(target: CargoTarget): File {
    val executable = File(toolchainBin, "${target.toolchainPrefix}${target.apiLevel}-clang++$clangSuffix")
    require(executable.exists()) { "Unable to find clang++ executable at ${executable.absolutePath}" }
    return executable
}

android {
    namespace = rootProject.ext["applicationId"].toString() + ".nativelib"
    compileSdk = 36

    ndkVersion = ndkHome.name

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        buildConfigField("String", "NATIVE_NAME", "\"$nativeBuildHash\".toString()")
        minSdk = 28
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    sourceSets["main"].jniLibs.srcDir("build/rustJniLibs/android")
}

// Register rustup tasks
val rustupTasks = cargoTargets.map { target ->
    tasks.register<Exec>("rustup${target.taskSuffix}") {
        workingDir = file("rust")
        commandLine("rustup", "target", "add", target.triple)
    }
}

// Ensure rustup task ordering
rustupTasks.forEachIndexed { index, task ->
    if (index > 0) {
        task.configure {
            mustRunAfter(rustupTasks[index - 1])
        }
    }
}

// Register sync & cargo build tasks
val syncTasks = cargoTargets.mapIndexed { index, target ->
    val rustupTask = rustupTasks[index]
    val cargoTask = tasks.register<Exec>("cargoBuild${target.taskSuffix}") {
        group = "build"
        dependsOn(rustupTask)
        workingDir = file("rust")
        commandLine("cargo", "build", "--release", "--target", target.triple)
        val clang = clangExecutableFor(target).absolutePath
        val clangxx = clangPlusPlusExecutableFor(target).absolutePath
        environment("CC", clang)
        environment("CXX", clangxx)
        environment("CC_${target.envSuffix}", clang)
        environment("CXX_${target.envSuffix}", clangxx)
        environment("CC_${target.envSuffixUpper}", clang)
        environment("CXX_${target.envSuffixUpper}", clangxx)
        environment("CARGO_TARGET_${target.envSuffixUpper}_LINKER", clang)
        environment("AR", llvmArExecutable.absolutePath)
        environment("AR_${target.envSuffix}", llvmArExecutable.absolutePath)
        environment("AR_${target.envSuffixUpper}", llvmArExecutable.absolutePath)
        environment("CARGO_TARGET_${target.envSuffixUpper}_AR", llvmArExecutable.absolutePath)
        environment("PATH", toolchainPath)
    }

    tasks.register<Sync>("syncNative${target.taskSuffix}") {
        dependsOn(cargoTask)
        val outputLibName = nativeLibFileName
        from(layout.projectDirectory.file("rust/target/${target.triple}/release/libsnapenhance.so")) {
            rename { outputLibName }
        }
        into(layout.buildDirectory.dir("rustJniLibs/android/${target.abi}"))
    }
}

tasks.named("preBuild").configure {
    syncTasks.forEach { dependsOn(it) }
}