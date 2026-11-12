pluginManagement {
  repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
  }
}

dependencyResolutionManagement {
  repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
  repositories {
    google()
    mavenCentral()
  }
}

val engine = providers.gradleProperty("bareKit.engine").get()

require(engine == "v8" || engine == "quickjs") { "Unknown engine '$engine'" }

rootProject.name = if (engine == "v8") "bare-kit-kotlin" else "bare-kit-kotlin-$engine"
