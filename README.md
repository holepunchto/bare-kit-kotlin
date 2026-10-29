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

val worklet = Worklet(context)
val ipc = Ipc(worklet)

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

The application context of `context` is handed to the native addons of the worklet as `bare.android.context.v1`. Closing a worklet terminates it, so keep it open for as long as it should run. Native addons linked with <https://github.com/holepunchto/bare-link> are loaded by the system linker, so include them in the `jniLibs` of the application.

## License

Apache-2.0
