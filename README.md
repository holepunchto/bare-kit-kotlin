# bare-kit-kotlin

Kotlin bindings for [Bare Kit](https://github.com/holepunchto/bare-kit), which runs Bare in a thread of its own within the application, known as a worklet, with an IPC to it exposed as `Bare.IPC` inside the worklet.

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
worklet.close()
```

Where `app.bundle` is a Bare bundle using `Bare.IPC`:

```js
Bare.IPC.on('data', (data) => Bare.IPC.end(data))
```

Collecting `data` yields the data that the worklet writes to `Bare.IPC` until it ends its side. As `Bare.IPC` keeps the worklet alive, `end()` ends the data sent to it so that it may exit on its own, and `wait()` returns its exit code. Writes fail with `BareKitException.Closed` once the IPC is ended or closed, and with `BareKitException.PeerClosed` once the worklet has closed its side. Closing a worklet terminates it, so keep it open for as long as it should run. Once closed, `terminate()` does nothing and its other methods throw `IllegalStateException`.

These bindings are a thin layer over [Bare Kit](https://github.com/holepunchto/bare-kit), whose README covers the [lifecycle](https://github.com/holepunchto/bare-kit#lifecycle), [IPC](https://github.com/holepunchto/bare-kit#ipc), [suspension](https://github.com/holepunchto/bare-kit#suspension), [addons](https://github.com/holepunchto/bare-kit#addons), and [error handling](https://github.com/holepunchto/bare-kit#error-handling) of a worklet. In Kotlin terms:

- If `source` is `null`, `start()` reads `filename` at runtime, so the worklet may be a module next to its `node_modules` rather than a bundle.
- Bare Kit is loaded as a shared library, so native addons resolve the symbols of Bare from it as is.
- Bare Kit calls back on threads of its own, which the bindings turn into suspending calls and a `Flow`, so a worklet and its IPC may be used from any dispatcher.
- An uncaught error in the worklet aborts the process, the application included, so report errors over `Bare.IPC` as described in [error handling](https://github.com/holepunchto/bare-kit#error-handling).

## Platforms

The library supports Android and the JVM on Linux, macOS, and Windows. `Worklet`, `Ipc`, and `BareKitException` are common code, so code shared between Android and the JVM can use them, while each platform creates its worklets.

On Android, a relative filename names an asset of the application, and the application context of `context` is handed to the native addons of the worklet as `bare.android.context.v1`. Native addons linked with <https://github.com/holepunchto/bare-link> are loaded by the system linker, so include them in the `jniLibs` of the application.

On the JVM, the library includes Bare Kit for Linux, macOS, and Windows on arm64 and x64, and extracts it to a temporary file when first used. Elsewhere, it loads `bare-kit` from `java.library.path` instead. On macOS, a relative filename names a resource of the application bundle, while on Linux and Windows, pass the path of the bundle or its source instead.

## License

Apache-2.0
