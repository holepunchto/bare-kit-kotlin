@file:JvmName("WorkletKt")

package to.holepunch.bare.kit

/**
 * Creates a worklet.
 *
 * @throws BareKitException.Failed If the worklet could not be created.
 */
public fun Worklet(options: Worklet.Options = Worklet.Options()): Worklet =
  Worklet(Native.workletCreate(options.memoryLimit, null))
