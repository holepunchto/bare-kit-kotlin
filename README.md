# bare-kit-kotlin

Kotlin bindings for [Bare Kit](https://github.com/holepunchto/bare-kit), which runs Bare in a thread of its own within the application, known as a worklet, with an IPC to it exposed as `Bare.IPC` inside the worklet. They support Android and the JVM on Linux, macOS, and Windows.

```groovy
repositories {
  maven { url "https://jitpack.io" }
}

dependencies {
  implementation "com.github.holepunchto.bare-kit-kotlin:bare-kit-kotlin:<tag>"
}
```

## Usage

```kotlin
import to.holepunch.bare.kit.Ipc
import to.holepunch.bare.kit.Worklet

val worklet = Worklet(context) // Or `Worklet()` on the JVM
val ipc = Ipc(worklet)

worklet.start("app.bundle")

ipc.write("Hello world".encodeToByteArray())

ipc.data.collect { data -> println(data.decodeToString()) }

ipc.close()

worklet.wait()
```

Where `app.bundle` is an asset of the application on Android, or a resource of the application bundle on macOS, here a Bare bundle using `Bare.IPC`:

```js
Bare.IPC.on("data", (data) => Bare.IPC.end(data));
```

On Linux and Windows, pass the path of the bundle or its source instead.

On Android, the application context of `context` is handed to the native addons of the worklet as `bare.android.context.v1`. Closing a worklet terminates it, so keep it open for as long as it should run. Once closed, `terminate()` does nothing and its other methods throw `IllegalStateException`. `Worklet`, `Ipc`, and `BareKitException` are common code, so code shared between Android and the JVM can use them, while each platform creates its worklets.

On Android, native addons linked with <https://github.com/holepunchto/bare-link> are loaded by the system linker, so include them in the `jniLibs` of the application. On the JVM, the library includes Bare Kit for Linux, macOS, and Windows on arm64 and x64, and extracts it to a temporary file when first used. Elsewhere, it loads `bare-kit` from `java.library.path` instead.

## License

Apache-2.0
