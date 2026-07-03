plugins.withId("com.android.library") {
    tasks.register("forbiddenApisCheck") {
        group = "verification"
        description = "Fails if src/main imports java.time or java.util.stream"

        val sourceDirs = listOf("src/main/java", "src/main/kotlin").map { file(it) }.filter { it.isDirectory }
        val outputFile = layout.buildDirectory.file("reports/forbiddenApis/forbidden-apis.txt")

        inputs.files(sourceDirs)
            .withPropertyName("sourceDirs")
            .withPathSensitivity(org.gradle.api.tasks.PathSensitivity.RELATIVE)
        outputs.file(outputFile)

        doLast {
            val importRegexes = listOf(
                Regex("^\\s*import\\s+java\\.time\\."),
                Regex("^\\s*import\\s+java\\.util\\.stream\\.")
            )
            val offendingLines = mutableListOf<String>()

            sourceDirs.forEach { dir ->
                dir.walkTopDown()
                    .filter { it.isFile && (it.extension == "kt" || it.extension == "java") }
                    .forEach { f ->
                        f.readLines().forEachIndexed { idx, line ->
                            if (importRegexes.any { rx -> rx.containsMatchIn(line) }) {
                                val rel = f.relativeTo(project.projectDir)
                                offendingLines += "${rel}:${idx + 1}: ${line.trim()}"
                            }
                        }
                    }
            }

            val outFile = outputFile.get().asFile
            outFile.parentFile.mkdirs()
            if (offendingLines.isNotEmpty()) {
                outFile.writeText(offendingLines.joinToString("\n"))
                val msg = buildString {
                    appendLine("Forbidden Java APIs detected in ${project.path}:")
                    appendLine(" - java.time.* or java.util.stream.* found in src/main sources")
                    appendLine()
                    offendingLines.forEach { appendLine(it) }
                }
                throw GradleException(msg)
            }
            outFile.writeText("")
        }
    }

    tasks.named("preBuild").configure {
        dependsOn("forbiddenApisCheck")
    }
}
