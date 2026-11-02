package to.holepunch.bare.kit

import kotlin.test.Test
import kotlin.test.assertEquals

class AssetsTest {
  @Test
  fun startsFromAssets() = test {
    worklet().use { worklet ->
      Ipc(worklet).use { ipc ->
        worklet.start("resource.js")

        assertEquals("Hello world", readAll(ipc))
      }
    }
  }
}
