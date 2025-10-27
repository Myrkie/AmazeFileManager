package com.amaze.filemanager.filesystem.root

import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import com.amaze.filemanager.fileoperations.exceptions.ShellNotRunningException
import com.amaze.filemanager.filesystem.root.base.IRootCommand
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.FileOutputStream

object ReadRootFileCommand : IRootCommand() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Reads a file as root and writes it to the provided FileDescriptor
     *
     * @param path path to the file
     * @param mode ParcelFileDescriptor mode (read or write)
     * @param signal optional cancellation signal
     */
    @Throws(ShellNotRunningException::class)
    fun readFile(path: String, mode: Int, signal: CancellationSignal? = null): ParcelFileDescriptor {
        return when (mode) {
            ParcelFileDescriptor.MODE_READ_ONLY -> {
                val pipe = ParcelFileDescriptor.createPipe()

                val job = scope.launch {
                    try {
                        val processOutput = runShellCommandToList("cat \"${path}\"")

                        FileOutputStream(pipe[1].fileDescriptor).use { output ->
                            processOutput.forEach { line ->
                                output.write((line + "\n").toByteArray())
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        pipe[1].close()
                    }
                }

                signal?.setOnCancelListener { job.cancel() }
                pipe[0]
            }

            else -> throw IllegalArgumentException("Unsupported mode $mode")
        }
    }
}
