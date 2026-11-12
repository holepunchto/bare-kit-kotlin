import java.net.URI
import java.security.DigestInputStream
import java.security.MessageDigest
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.android.kotlin.multiplatform.library)
  alias(libs.plugins.dokka)
  alias(libs.plugins.ktfmt)
  id("maven-publish")
}

group = "com.github.holepunchto"

version = System.getenv("VERSION") ?: "0.2.5"

layout.buildDirectory = file("build/gradle")

kotlin {
  explicitApi()

  @OptIn(org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation::class) abiValidation()

  compilerOptions { allWarningsAsErrors = true }

  jvm { compilerOptions { jvmTarget = JvmTarget.JVM_11 } }

  android {
    namespace = "to.holepunch.bare.kit"
    compileSdk = 36
    minSdk = 29
    withDeviceTestBuilder { sourceSetTreeName = "test" }
      .configure { instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }
    androidResources { enable = true }
    compilerOptions { jvmTarget = JvmTarget.JVM_11 }
    optimization {
      consumerKeepRules.apply {
        publish = true
        file("consumer-rules.pro")
      }
    }
  }

  sourceSets {
    commonMain.dependencies { api(libs.kotlinx.coroutines.core) }
    commonTest.dependencies { implementation(kotlin("test")) }
    getByName("androidDeviceTest").dependencies {
      implementation(libs.androidx.test.runner)
      implementation(libs.androidx.test.junit)
    }
  }
}

dokka {
  dokkaSourceSets.configureEach { reportUndocumented = true }
  dokkaPublications.html { failOnWarning = true }
}

ktfmt { googleStyle() }

// The native library is prebuilt for every engine, Android ABI, and desktop
// platform, either into `prebuilds/<engine>/` by `bare-make install --prefix
// prebuilds` with `-PbareKit.local`, or as the prebuilds of the release.
abstract class DownloadPrebuilds : DefaultTask() {
  @get:Input abstract val url: Property<String>

  @get:Input abstract val engine: Property<String>

  @get:Input abstract val checksum: Property<String>

  @get:OutputDirectory abstract val android: DirectoryProperty

  @get:OutputDirectory abstract val jvm: DirectoryProperty

  @get:Inject abstract val archives: ArchiveOperations

  @get:Inject abstract val files: FileSystemOperations

  @TaskAction
  fun download() {
    val archive = temporaryDir.resolve("prebuilds.zip")

    val sha256 = MessageDigest.getInstance("SHA-256")

    DigestInputStream(URI(url.get()).toURL().openStream(), sha256).use { input ->
      archive.outputStream().use { input.copyTo(it) }
    }

    val digest = sha256.digest().joinToString("") { "%02x".format(it) }

    if (digest != checksum.get()) {
      throw GradleException("Prebuilds checksum mismatch: expected ${checksum.get()}, got $digest")
    }

    for ((target, output) in listOf("android" to android, "jvm" to jvm)) {
      files.sync {
        from(archives.zipTree(archive)) {
          include("prebuilds/${engine.get()}/$target/**")
          eachFile {
            relativePath = RelativePath(true, *relativePath.segments.drop(3).toTypedArray())
          }
          includeEmptyDirs = false
        }
        into(output)
      }
    }
  }
}

val engine = providers.gradleProperty("bareKit.engine").get()

val downloadPrebuilds =
  if (providers.gradleProperty("bareKit.local").isPresent) null
  else
    tasks.register<DownloadPrebuilds>("downloadPrebuilds") {
      url = providers.gradleProperty("bareKit.prebuilds")
      checksum = providers.gradleProperty("bareKit.prebuilds.checksum")
      engine = providers.gradleProperty("bareKit.engine")
      android = layout.buildDirectory.dir("prebuilds/android")
      jvm = layout.buildDirectory.dir("prebuilds/jvm")
    }

androidComponents {
  onVariants { variant ->
    val jniLibs = variant.sources.jniLibs ?: return@onVariants

    if (downloadPrebuilds == null) jniLibs.addStaticSourceDirectory("prebuilds/$engine/android")
    else jniLibs.addGeneratedSourceDirectory(downloadPrebuilds, DownloadPrebuilds::android)
  }
}

tasks.named<ProcessResources>("jvmProcessResources") {
  from(downloadPrebuilds?.flatMap { it.jvm } ?: "prebuilds/$engine/jvm") {
    into("to/holepunch/bare/kit")
  }
  from("consumer-rules.pro") {
    into("META-INF/proguard")
    rename { "bare-kit-kotlin.pro" }
  }
}

tasks.named<Test>("jvmTest") { useJUnitPlatform() }

tasks.register("lint") {
  group = "verification"
  description = "Checks the formatting, the ABI, and the documentation."
  dependsOn("ktfmtCheck", "checkKotlinAbi", "dokkaGenerate")
}

tasks.register("test") {
  group = "verification"
  description = "Runs the tests on the host."
  dependsOn("jvmTest")
}

tasks.assemble { dependsOn("assembleAndroidDeviceTest") }

tasks.check { dependsOn("lint") }
