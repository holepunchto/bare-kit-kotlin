package to.holepunch.bare.kit

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.fold
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkletTest {
  private val instrumentation = InstrumentationRegistry.getInstrumentation()

  private fun test(block: suspend () -> Unit) = runBlocking { withTimeout(30.seconds) { block() } }

  private suspend fun readAll(ipc: IPC) =
    ipc.data.fold(ByteArray(0)) { received, data -> received + data }.decodeToString()

  private fun worklet() = Worklet(instrumentation.targetContext)

  @Test
  fun echoesIPCData() = test {
    worklet().use { worklet ->
      IPC(worklet).use { ipc ->
        worklet.start("/echo.js", ECHO)

        ipc.write("Hello world".encodeToByteArray())

        assertEquals("Hello world", readAll(ipc))

        ipc.close()

        assertEquals(0, worklet.wait())
      }
    }
  }

  @Test
  fun startsFromAssets() = test {
    worklet().use { worklet ->
      IPC(worklet).use { ipc ->
        worklet.start("resource.js")

        assertEquals("Hello world", readAll(ipc))
      }
    }
  }

  @Test
  fun endsIPCData() = test {
    worklet().use { worklet ->
      IPC(worklet).use { ipc ->
        worklet.start("/collect.js", COLLECT)

        ipc.write("Hello ".encodeToByteArray())
        ipc.write("world".encodeToByteArray())
        ipc.end()

        assertThrows(BareKitException.Closed::class.java) {
          runBlocking { ipc.write("!".encodeToByteArray()) }
        }

        assertEquals("Hello world", readAll(ipc))

        assertEquals(0, worklet.wait())
      }
    }
  }

  @Test
  fun passesArguments() = test {
    worklet().use { worklet ->
      IPC(worklet).use { ipc ->
        worklet.start("/argv.js", ARGV, listOf("foo", "bar"))

        assertEquals("""["foo","bar"]""", readAll(ipc))
      }
    }
  }

  @Test
  fun runsSource() = test {
    worklet().use { worklet ->
      IPC(worklet).use { ipc ->
        worklet.start("/app.js", "Bare.IPC.end('Hello world')".encodeToByteArray())

        assertEquals("Hello world", readAll(ipc))
      }
    }
  }

  @Test
  fun reportsTheExitCode() = test {
    worklet().use { worklet ->
      worklet.start("/exit.js", EXIT)

      assertEquals(3, worklet.wait())
    }
  }

  @Test
  fun terminates() = test {
    worklet().use { worklet ->
      IPC(worklet).use { ipc ->
        worklet.start("/echo.js", ECHO)

        worklet.terminate()
        worklet.wait()

        assertEquals("", readAll(ipc))
      }
    }
  }

  @Test
  fun failsToWriteOnceClosed() = test {
    worklet().use { worklet ->
      val ipc = IPC(worklet)

      worklet.start("/echo.js", ECHO)

      ipc.close()

      assertThrows(BareKitException.Closed::class.java) {
        runBlocking { ipc.write("Hello world".encodeToByteArray()) }
      }
    }
  }

  private companion object {
    val ECHO = "Bare.IPC.on('data', (data) => Bare.IPC.end(data))".encodeToByteArray()
    val COLLECT =
      """
        const chunks = []
        Bare.IPC.on('data', (data) => chunks.push(data)).on('end', () => Bare.IPC.end(Buffer.concat(chunks)))
      """
        .encodeToByteArray()
    val ARGV = "Bare.IPC.end(JSON.stringify(Bare.argv))".encodeToByteArray()
    val EXIT = "Bare.exit(3)".encodeToByteArray()
  }
}
