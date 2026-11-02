package to.holepunch.bare.kit

import java.io.File

// The library of the running platform is included as a resource, which is extracted as the system
// can only load files. Without one, it's loaded from the library path instead.
internal actual fun loadLibrary() {
  val name = System.mapLibraryName("bare-kit")

  val resource =
    Native::class.java.getResourceAsStream("${host()}/$name")
      ?: return System.loadLibrary("bare-kit")

  val file = File.createTempFile("bare-kit", name)

  file.deleteOnExit()

  resource.use { input -> file.outputStream().use { input.copyTo(it) } }

  System.load(file.path)
}

private fun host(): String {
  val os = System.getProperty("os.name").lowercase()
  val arch = System.getProperty("os.arch").lowercase()

  val platform =
    when {
      os.startsWith("mac") -> "darwin"
      os.startsWith("windows") -> "win32"
      else -> os
    }

  return when (arch) {
    "aarch64",
    "arm64" -> "$platform-arm64"
    "amd64",
    "x86_64" -> "$platform-x64"
    else -> "$platform-$arch"
  }
}
