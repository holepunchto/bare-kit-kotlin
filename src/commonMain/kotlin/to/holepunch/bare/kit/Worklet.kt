package to.holepunch.bare.kit

import kotlinx.coroutines.CompletableDeferred

/**
 * A Bare runtime running on a thread of its own within the process, created with the `Worklet()`
 * function of the platform.
 *
 * Closing a worklet terminates it, so keep it open for as long as it should run. Use [Ipc] to
 * communicate with it. Once closed, [terminate] does nothing and the other methods throw
 * [IllegalStateException].
 */
public class Worklet internal constructor(internal val handle: Long) : AutoCloseable {
  /** The options of a worklet. */
  public data class Options(
    /**
     * The memory limit of each JavaScript heap in bytes, or 0 to infer the limit from the amount of
     * physical memory of the device.
     */
    public val memoryLimit: Long = 0
  )

  // The native worklet is freed once closed, so every call into it holds the lock.
  private val lock = Any()
  private var closed = false
  private val exited = CompletableDeferred<Int>()

  /**
   * Starts the worklet, running [filename] with [arguments] as `Bare.argv`.
   *
   * On Android, a relative [filename] names an asset of the application. The assets listed in
   * `<name>.assets`, one `/<path>` per line, are extracted from `<name>/<path>` before
   * `<name>.bundle` first runs. On macOS, it names a resource of the application bundle.
   *
   * @param filename The path or URL of the module to run.
   * @param source The source of the module, or `null` to read it from [filename].
   * @param arguments The arguments passed to the module as `Bare.argv`.
   * @throws BareKitException.Failed If the worklet could not be started, such as after [teardown].
   */
  public fun start(
    filename: String,
    source: ByteArray? = null,
    arguments: List<String> = emptyList(),
  ) {
    withHandle { Native.workletStart(it, this, filename, source, arguments.toTypedArray()) }
  }

  /**
   * Suspends the worklet, emitting `suspend` on `Bare`.
   *
   * @param linger How long in milliseconds the worklet may keep running before it idles, or -1 for
   *   as long as the platform allows.
   */
  public fun suspend(linger: Int = -1): Unit = withHandle { Native.workletSuspend(it, linger) }

  /** Resumes a suspended worklet, emitting `resume` on `Bare`. */
  public fun resume(): Unit = withHandle { Native.workletResume(it) }

  /**
   * Wakes up a suspended worklet, emitting `wakeup` on `Bare`.
   *
   * @param deadline How long in milliseconds the worklet may run before it idles again, or -1 for
   *   as long as the platform allows.
   */
  public fun wakeup(deadline: Int = -1): Unit = withHandle { Native.workletWakeup(it, deadline) }

  /** Terminates the worklet unless it is closed. Use [wait] to wait for it to exit. */
  public fun terminate(): Unit = synchronized(lock) { if (!closed) Native.workletTerminate(handle) }

  /**
   * Waits for a started worklet to exit, whether on its own or because it was terminated.
   *
   * @return The exit code of the worklet.
   */
  public suspend fun wait(): Int = exited.await()

  /** Terminates the worklet if it is still running and releases it. Closing twice is a no-op. */
  public override fun close(): Unit =
    synchronized(lock) {
      if (closed) return

      closed = true

      Native.workletDestroy(handle)
    }

  internal fun <T> withHandle(block: (Long) -> T): T =
    synchronized(lock) {
      check(!closed) { "The worklet is closed" }

      block(handle)
    }

  // Called from native code on the worklet thread.
  @Suppress("unused")
  private fun onExit(code: Int) {
    exited.complete(code)
  }

  /** The state shared by the worklets of the process. */
  public companion object {
    /** Trades performance for memory. Must be configured before starting the first worklet. */
    public fun optimizeForMemory(enabled: Boolean): Unit = Native.optimizeForMemory(enabled)

    /**
     * Tears down the state shared by the worklets of the process. Every worklet must have been
     * terminated or closed first, and those still exiting are waited for. Must be called at most
     * once, after which starting a worklet fails. A process that runs worklets for as long as it
     * lives need not call it.
     */
    public fun teardown(): Unit = Native.teardown()
  }
}
