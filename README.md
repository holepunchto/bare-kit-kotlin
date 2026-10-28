# bare-kit-kotlin

Kotlin bindings for [Bare Kit](https://github.com/holepunchto/bare-kit), which runs Bare in a thread of its own within the application, known as a worklet, with an IPC to it exposed as `Bare.IPC` inside the worklet.

## Installation

Add JitPack to the repositories of your project:

```groovy
dependencyResolutionManagement {
  repositories {
    google()
    mavenCentral()
    maven { url "https://jitpack.io" }
  }
}
```

Then add the package to the dependencies of your module:

```groovy
dependencies {
  implementation "com.github.holepunchto.bare-kit-kotlin:bare-kit-kotlin:v0.1.0"
}
```

## Usage

```kotlin
import to.holepunch.bare.kit.IPC
import to.holepunch.bare.kit.Worklet

val worklet = Worklet(context)
val ipc = IPC(worklet)

worklet.start("app.bundle")

ipc.write("Hello world".encodeToByteArray())

ipc.data.collect { data -> println(data.decodeToString()) }

ipc.close()

worklet.wait()
```

Where `app.bundle` is an asset of the application, here a Bare bundle using `Bare.IPC`:

```js
Bare.IPC.on("data", (data) => Bare.IPC.end(data));
```

The application context of `context` is handed to the native addons of the worklet as `bare.android.context.v1`. Closing a worklet terminates it, so keep it open for as long as it should run. Native addons linked with <https://github.com/holepunchto/bare-link> are loaded by the system linker, so include them in the `jniLibs` of the application:

```console
bare-link --preset android --out app/src/main/addons app.js
```

## Release

1. Run the Prebuild workflow with the tag of the release, such as `v0.1.0`. It compiles the native library for every ABI and creates a draft release with the prebuilds, whose SHA-256 digest the release lists.
2. Update `bareKit.prebuilds` and `bareKit.prebuilds.checksum` in `gradle.properties` as part of the release.
3. Push the tag. The Publish workflow checks that `gradle.properties` declares the checksum of the prebuilds of the draft release, publishes it, and builds the release on JitPack.

To build against prebuilds compiled locally with `bare-make`, installed in `prebuilds/` by `bare-make install --prefix prebuilds`, pass `-PbareKit.local` to Gradle.

## License

Apache-2.0
