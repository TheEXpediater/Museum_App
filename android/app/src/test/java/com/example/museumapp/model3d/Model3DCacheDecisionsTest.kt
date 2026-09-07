package com.example.museumapp.model3d

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class Model3DCacheDecisionsTest {
    @Test
    fun needsDownloadWhenFileIsAbsent() {
        val missing = File(Files.createTempDirectory("model3d_cache").toFile(), "1.glb")
        assertFalse(missing.exists())
        assertTrue(Model3DCacheDecisions.needsDownload(missing, expectedSha256 = "a".repeat(64)))
    }

    @Test
    fun needsDownloadWhenExistingFileReferenceIsNull() {
        assertTrue(Model3DCacheDecisions.needsDownload(null, expectedSha256 = "a".repeat(64)))
    }

    @Test
    fun needsDownloadWhenFileIsEmpty() {
        val file = File.createTempFile("model3d", ".glb")
        try {
            assertTrue(Model3DCacheDecisions.needsDownload(file, expectedSha256 = "a".repeat(64)))
        } finally {
            file.delete()
        }
    }

    @Test
    fun noDownloadWhenExistingFileMatchesExpectedSha() {
        val file = File.createTempFile("model3d", ".glb")
        try {
            file.writeBytes("glb file bytes".toByteArray())
            val actualSha = Sha256.of(file)
            assertFalse(Model3DCacheDecisions.needsDownload(file, expectedSha256 = actualSha))
            // Case-insensitive comparison, since backends may send either case of hex.
            assertFalse(Model3DCacheDecisions.needsDownload(file, expectedSha256 = actualSha.uppercase()))
        } finally {
            file.delete()
        }
    }

    @Test
    fun needsDownloadWhenExistingFileShaMismatches() {
        val file = File.createTempFile("model3d", ".glb")
        try {
            file.writeBytes("glb file bytes".toByteArray())
            assertTrue(Model3DCacheDecisions.needsDownload(file, expectedSha256 = "0".repeat(64)))
        } finally {
            file.delete()
        }
    }

    @Test
    fun staleVersionFilesFindsOtherGlbFilesButKeepsCurrentAndNonGlbFiles() {
        val dir = Files.createTempDirectory("model3d_cache").toFile()
        try {
            val current = File(dir, "3.glb").apply { writeText("v3") }
            val stale1 = File(dir, "1.glb").apply { writeText("v1") }
            val stale2 = File(dir, "2.glb").apply { writeText("v2") }
            val partFile = File(dir, "4.glb.part").apply { writeText("partial") }

            val stale = Model3DCacheDecisions.staleVersionFiles(dir, keepFileName = current.name)

            assertEquals(setOf(stale1, stale2), stale.toSet())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun cleaningUpStaleVersionsAfterASuccessfulDownloadDeletesOnlyThem() {
        val dir = Files.createTempDirectory("model3d_cache").toFile()
        try {
            val newlyDownloaded = File(dir, "2.glb").apply { writeText("v2") }
            val stale = File(dir, "1.glb").apply { writeText("v1") }
            val unrelatedPartFile = File(dir, "3.glb.part").apply { writeText("in-progress") }

            Model3DCacheDecisions.staleVersionFiles(dir, keepFileName = newlyDownloaded.name).forEach { it.delete() }

            assertTrue(newlyDownloaded.exists())
            assertFalse(stale.exists())
            assertTrue(unrelatedPartFile.exists())
        } finally {
            dir.deleteRecursively()
        }
    }
}
