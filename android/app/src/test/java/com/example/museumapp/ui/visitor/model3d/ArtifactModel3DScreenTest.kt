package com.example.museumapp.ui.visitor.model3d

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Regression coverage for the pre-Filament file validation that fixed a real physical-device
 * crash: a missing/empty cached GLB reaching modelLoader.createModelInstance() aborted the
 * whole process natively (SIGABRT inside libfilament-jni.so) rather than throwing a catchable
 * Kotlin exception. validateCachedModelFile() must reject those cases before Filament ever sees
 * the file.
 */
class ArtifactModel3DScreenTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun missingFileIsRejected() {
        val missing = File(tempFolder.root, "does-not-exist.glb")

        val error = validateCachedModelFile(missing)

        assertEquals("Downloaded 3D model is missing or empty.", error)
    }

    @Test
    fun emptyFileIsRejected() {
        val empty = tempFolder.newFile("empty.glb")

        val error = validateCachedModelFile(empty)

        assertEquals("Downloaded 3D model is missing or empty.", error)
    }

    @Test
    fun nonEmptyFileIsAccepted() {
        val real = tempFolder.newFile("model.glb")
        real.writeBytes(byteArrayOf(1, 2, 3, 4))

        val error = validateCachedModelFile(real)

        assertNull(error)
    }

    @Test
    fun directoryIsRejected() {
        // Guards against a future cache-path mistake (e.g. pointing at the version directory
        // instead of the .glb file) being treated as a valid model.
        val directory = tempFolder.newFolder("not-a-file.glb")

        val error = validateCachedModelFile(directory)

        assertEquals("Downloaded 3D model is missing or empty.", error)
    }
}
