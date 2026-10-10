package com.pixelsave.app

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class NoBitmapInAppTest {

    private val forbiddenTokens = listOf(
        "Bitmap",
        "BitmapFactory",
        "ImageDecoder",
        ".compress(",
        "decodeStream",
        "decodeFile",
        "decodeByteArray",
        "decodeResource"
    )

    @Test
    fun appMainSourcesContainNoBitmapDecodeOrEncodePath() {
        val roots = listOf(
            File("src/main/java"),
            File("app/src/main/java"),
            File("src/main/kotlin"),
            File("app/src/main/kotlin")
        ).filter { it.isDirectory }
        assertTrue("No main source directory found from ${File(".").absolutePath}", roots.isNotEmpty())

        val violations = mutableListOf<String>()
        roots.forEach { root ->
            root.walkTopDown()
                .filter { it.isFile && (it.extension == "kt" || it.extension == "java") }
                .forEach { file ->
                    val text = file.readText()
                    forbiddenTokens.forEach { token ->
                        if (text.contains(token)) {
                            violations.add("${file.path} contains \"$token\"")
                        }
                    }
                }
        }
        assertTrue(
            "Image decoding/re-encoding found in app main sources: $violations",
            violations.isEmpty()
        )
    }
}
