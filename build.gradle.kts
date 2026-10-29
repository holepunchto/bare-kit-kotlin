import java.net.URI
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

version = System.getenv("VERSION") ?: "0.1.0"

layout.buildDirectory = file("build/gradle")

kotlin {
  explicitApi()

  @OptIn(org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation::class) abiValidation()

  compilerOptions { allWarningsAsErrors = true }

  android {
    namespace = "to.holepunch.bare.kit"
    compileSdk = 36
    minSdk = 29
    withDeviceTest { instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }
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

// The native library is prebuilt for every ABI, either into `prebuilds/` by
// `bare-make install --prefix prebuilds` with `-PbareKit.local`, or as the
// prebuilds of the release.
abstract class DownloadPrebuilds : DefaultTask() {
  @get:Input abstract val url: Property<String>

  @get:Input abstract val checksum: Property<String>

  @get:OutputDirectory abstract val output: DirectoryProperty

  @get:Inject abstract val archives: ArchiveOperations

  @get:Inject abstract val files: FileSystemOperations

  @TaskAction
  fun download() {
    val archive = temporaryDir.resolve("prebuilds.zip")

    URI(url.get()).toURL().openStream().use { input ->
      archive.outputStream().use { input.copyTo(it) }
    }

    val digest =
      MessageDigest.getInstance("SHA-256").digest(archive.readBytes()).joinToString("") {
        "%02x".format(it)
      }

    if (digest != checksum.get()) {
      throw GradleException("Prebuilds checksum mismatch: expected ${checksum.get()}, got $digest")
    }

    files.sync {
      from(archives.zipTree(archive)) {
        include("prebuilds/**")
        eachFile {
          relativePath = RelativePath(true, *relativePath.segments.drop(1).toTypedArray())
        }
        includeEmptyDirs = false
      }
      into(output)
    }
  }
}

val downloadPrebuilds =
  if (providers.gradleProperty("bareKit.local").isPresent) null
  else
    tasks.register<DownloadPrebuilds>("downloadPrebuilds") {
      url = providers.gradleProperty("bareKit.prebuilds")
      checksum = providers.gradleProperty("bareKit.prebuilds.checksum")
      output = layout.buildDirectory.dir("prebuilds")
    }

androidComponents {
  onVariants { variant ->
    val jniLibs = variant.sources.jniLibs ?: return@onVariants

    if (downloadPrebuilds == null) jniLibs.addStaticSourceDirectory("prebuilds")
    else jniLibs.addGeneratedSourceDirectory(downloadPrebuilds, DownloadPrebuilds::output)
  }
}

tasks.register("lint") {
  group = "verification"
  description = "Checks the formatting, the ABI, and the documentation."
  dependsOn("ktfmtCheck", "checkKotlinAbi", "dokkaGenerate")
}

tasks.register("test") {
  group = "verification"
  description = "Runs the tests on a connected device."
  dependsOn("connectedAndroidDeviceTest")
}

tasks.assemble { dependsOn("assembleAndroidDeviceTest") }

tasks.check { dependsOn("lint") }
