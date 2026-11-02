package to.holepunch.bare.kit

import androidx.test.platform.app.InstrumentationRegistry

internal actual fun worklet() = Worklet(InstrumentationRegistry.getInstrumentation().targetContext)
