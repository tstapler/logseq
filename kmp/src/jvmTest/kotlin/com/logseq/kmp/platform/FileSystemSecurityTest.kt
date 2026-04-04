package com.logseq.kmp.platform

import java.io.File
import java.nio.file.Paths
import kotlin.test.*

class FileSystemSecurityTest {
    private val fileSystem = PlatformFileSystem()
    private val homeDir = System.getProperty("user.home")
    
    @Test
    fun testPathTraversalBlocked() {
        // Attempt to access a path outside the whitelist using traversal
        val traversalPath = Paths.get(homeDir, "..", "some_other_user_maybe").toString()
        val content = fileSystem.readFile(traversalPath)
        assertNull(content, "Should not be able to read file via traversal outside home")
        
        val success = fileSystem.writeFile(traversalPath, "malicious")
        assertFalse(success, "Should not be able to write file via traversal outside home")
    }

    @Test
    fun testHomeDirAllowed() {
        val testFile = File(homeDir, "logseq_security_test.txt").absolutePath
        try {
            val content = "security test"
            val writeSuccess = fileSystem.writeFile(testFile, content)
            assertTrue(writeSuccess, "Should be able to write within home directory")
            
            val readContent = fileSystem.readFile(testFile)
            assertEquals(content, readContent)
        } finally {
            File(testFile).delete()
        }
    }

    @Test
    fun testExplicitGraphRootAllowed() {
        // Test that a path becomes whitelisted when used as a graph root (via createDirectory or writeFile)
        val externalDir = "/tmp/logseq_external_${System.currentTimeMillis()}"
        val testFile = "$externalDir/test.md"
        
        try {
            // Initially unauthorized if not in home (assuming /tmp is not in home)
            // Note: If home is in /tmp (some CI envs), this test might need adjustment
            val homePath = Paths.get(homeDir).toAbsolutePath().normalize()
            val tmpPath = Paths.get(externalDir).toAbsolutePath().normalize()
            if (tmpPath.startsWith(homePath)) {
                println("Skipping explicit root test as /tmp is within home")
                return
            }

            // This should add externalDir to whitelist
            val created = fileSystem.createDirectory(externalDir)
            assertTrue(created, "Should be able to create external directory (adds to whitelist)")
            
            val writeSuccess = fileSystem.writeFile(testFile, "content")
            assertTrue(writeSuccess, "Should be able to write to whitelisted external directory")
            
            // Traversal attempt from the new whitelisted root should still be blocked
            val maliciousTraversal = "$externalDir/../../etc/passwd"
            assertFalse(fileSystem.writeFile(maliciousTraversal, "malicious"), "Should block traversal even from whitelisted external root")
            
        } finally {
            File(externalDir).deleteRecursively()
        }
    }
}
