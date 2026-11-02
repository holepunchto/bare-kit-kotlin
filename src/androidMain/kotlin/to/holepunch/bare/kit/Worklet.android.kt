@file:JvmName("WorkletKt")

package to.holepunch.bare.kit

import android.content.Context

/**
 * Creates a worklet.
 *
 * @param context A context of the application, whose application context is handed to the native
 *   addons of the worklet.
 * @throws BareKitException.Failed If the worklet could not be created.
 */
public fun Worklet(context: Context, options: Worklet.Options = Worklet.Options()): Worklet =
  Worklet(Native.workletCreate(options.memoryLimit, context.applicationContext))
