package to.holepunch.bare.kit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.fold
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

internal expect fun worklet(): Worklet

internal fun test(block: suspend () -> Unit) = runBlocking { withTimeout(30.seconds) { block() } }

internal suspend fun readAll(ipc: Ipc) =
  ipc.data.fold(ByteArray(0)) { received, data -> received + data }.decodeToString()

class WorkletTest {
  @Test
  fun echoesIpcData() = test {
    worklet().use { worklet ->
      Ipc(worklet).use { ipc ->
        worklet.start("/echo.js", ECHO)

        ipc.write("Hello world".encodeToByteArray())

        assertEquals("Hello world", readAll(ipc))

        ipc.close()

        assertEquals(0, worklet.wait())
      }
    }
  }

  @Test
  fun endsIpcData() = test {
    worklet().use { worklet ->
      Ipc(worklet).use { ipc ->
        worklet.start("/collect.js", COLLECT)

        ipc.write("Hello ".encodeToByteArray())
        ipc.write("world".encodeToByteArray())
        ipc.end()

        assertFailsWith<BareKitException.Closed> { ipc.write("!".encodeToByteArray()) }

        assertEquals("Hello world", readAll(ipc))

        assertEquals(0, worklet.wait())
      }
    }
  }

  @Test
  fun passesArguments() = test {
    worklet().use { worklet ->
      Ipc(worklet).use { ipc ->
        worklet.start("/argv.js", ARGV, listOf("foo", "bar"))

        assertEquals("""["foo","bar"]""", readAll(ipc))
      }
    }
  }

  @Test
  fun runsSource() = test {
    worklet().use { worklet ->
      Ipc(worklet).use { ipc ->
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
      Ipc(worklet).use { ipc ->
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
      val ipc = Ipc(worklet)

      worklet.start("/echo.js", ECHO)

      ipc.close()

      assertFailsWith<BareKitException.Closed> { ipc.write("Hello world".encodeToByteArray()) }
    }
  }

  @Test
  fun failsToWriteOnceTheWorkletExits() = test {
    worklet().use { worklet ->
      Ipc(worklet).use { ipc ->
        worklet.start("/exit.js", EXIT)
        worklet.wait()

        assertFailsWith<BareKitException.PeerClosed> {
          ipc.write("Hello world".encodeToByteArray())
        }
      }
    }
  }

  @Test
  fun ignoresTerminateOnceClosedAndRejectsOtherCalls() = test {
    val worklet = worklet()

    worklet.start("/exit.js", EXIT)
    worklet.wait()
    worklet.close()
    worklet.terminate()

    assertFailsWith<IllegalStateException> { worklet.suspend() }
    assertFailsWith<IllegalStateException> { Ipc(worklet) }
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
