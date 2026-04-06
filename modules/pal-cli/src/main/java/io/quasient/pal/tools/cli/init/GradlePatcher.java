/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.quasient.pal.tools.cli.init;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Text-based patcher for existing Gradle build files ({@code build.gradle} or {@code
 * build.gradle.kts}).
 *
 * <p>Adds PAL AspectJ weaving configuration to an existing Gradle project by:
 *
 * <ul>
 *   <li>Adding {@code aspectjTools} and {@code aspect} to the {@code configurations} block
 *   <li>Adding the {@code aspectjtools}, {@code pal-weave}, and {@code aspectjrt} dependencies
 *   <li>Appending a {@code weaveClasses} task that runs AspectJ weaving after tests
 * </ul>
 *
 * <p>The {@code weaveClasses} task uses {@code mustRunAfter test} so that unit tests always run
 * against unwoven classes. The {@code jar} task depends on {@code weaveClasses} so the packaged
 * artifact contains woven bytecode.
 *
 * <p>The patcher is idempotent: it checks for existing entries before adding and skips items that
 * are already present. It creates a backup of the original file before modification (unless in
 * dry-run mode).
 *
 * <p>Detects Groovy DSL vs Kotlin DSL by file extension ({@code .gradle} vs {@code .gradle.kts})
 * and uses the appropriate syntax for each.
 *
 * @since 1.0.0
 */
public final class GradlePatcher {

  /** The PAL weave artifact name used for idempotency detection. */
  private static final String PAL_WEAVE_ARTIFACT = "pal-weave";

  /** The AspectJ runtime artifact name used for idempotency detection. */
  private static final String ASPECTJ_RT_ARTIFACT = "aspectjrt";

  /** The AspectJ compiler tools artifact name used for idempotency detection. */
  private static final String ASPECTJ_TOOLS_ARTIFACT = "aspectjtools";

  /** The PAL weave Maven coordinates prefix. */
  private static final String PAL_WEAVE_COORDS = "io.quasient.pal:pal-weave";

  /** The AspectJ runtime Maven coordinates prefix. */
  private static final String ASPECTJ_RT_COORDS = "org.aspectj:aspectjrt";

  /** The AspectJ compiler tools Maven coordinates prefix. */
  private static final String ASPECTJ_TOOLS_COORDS = "org.aspectj:aspectjtools";

  /** Marker string for the weave task, used for idempotency detection. */
  private static final String WEAVE_TASK_MARKER = "weaveClasses";

  /** Groovy DSL weave task definition, appended to the end of the build file. */
  private static final String GROOVY_WEAVE_TASK =
      """

      // PAL AspectJ weaving — runs after tests so unit tests use unwoven classes
      tasks.register('weaveClasses', JavaExec) {
          dependsOn classes
          mustRunAfter test
          mainClass = 'org.aspectj.tools.ajc.Main'
          classpath = configurations.aspectjTools
          args = [
              '-inpath', sourceSets.main.output.classesDirs.asPath,
              '-aspectpath', configurations.aspect.asPath,
              '-d', sourceSets.main.java.destinationDirectory.get().asFile.path,
              '-classpath', sourceSets.main.compileClasspath.asPath,
              '-source', sourceCompatibility.toString(),
              '-target', targetCompatibility.toString(),
          ]
      }

      tasks.named('jar') {
          dependsOn weaveClasses
      }
      """;

  /** Kotlin DSL weave task definition, appended to the end of the build file. */
  private static final String KOTLIN_WEAVE_TASK =
      """

      // PAL AspectJ weaving — runs after tests so unit tests use unwoven classes
      tasks.register<JavaExec>("weaveClasses") {
          dependsOn("classes")
          mustRunAfter("test")
          mainClass.set("org.aspectj.tools.ajc.Main")
          classpath = configurations["aspectjTools"]
          args = listOf(
              "-inpath", sourceSets["main"].output.classesDirs.asPath,
              "-aspectpath", configurations["aspect"].asPath,
              "-d", sourceSets["main"].java.destinationDirectory.get().asFile.path,
              "-classpath", sourceSets["main"].compileClasspath.asPath,
              "-source", java.sourceCompatibility.toString(),
              "-target", java.targetCompatibility.toString(),
          )
      }

      tasks.named("jar") {
          dependsOn("weaveClasses")
      }
      """;

  /**
   * Patches an existing Gradle build file to add PAL AspectJ weaving configuration.
   *
   * <p>Operations performed:
   *
   * <ol>
   *   <li>Checks for an existing AspectJ plugin — if found, warns and returns without modification
   *   <li>Adds {@code aspectjTools} and {@code aspect} configurations (creates block if missing)
   *   <li>Adds {@code aspectjtools}, {@code pal-weave}, and {@code aspectjrt} dependencies (creates
   *       block if missing)
   *   <li>Appends the {@code weaveClasses} task and {@code jar} dependency
   *   <li>Creates a backup at {@code <filename>.backup} before writing changes
   * </ol>
   *
   * <p>When {@code dryRun=true} in the config, no files are modified and no backup is created, but
   * the returned {@link PatchResult} still reports what would have been changed.
   *
   * @param config the init configuration
   * @param buildFile the path to the existing Gradle build file
   * @return a result describing what was added, skipped, or warned about
   * @throws IOException if an I/O error occurs during file reading or writing
   */
  public PatchResult patch(InitConfig config, Path buildFile) throws IOException {
    String content = Files.readString(buildFile, StandardCharsets.UTF_8);
    Path fileNamePath = buildFile.getFileName();
    String fileName = fileNamePath != null ? fileNamePath.toString() : "";
    boolean isKotlinDsl = fileName.endsWith(".kts");
    PatchResult.Builder result = PatchResult.builder().dryRun(config.isDryRun());

    validateBraces(content);

    if (hasExistingAspectjPlugin(content)) {
      result.warning("Existing AspectJ plugin detected; please add pal-weave dependency manually");
      return result.build();
    }

    content = patchConfigurations(content, isKotlinDsl, result);
    content = patchDependencies(content, isKotlinDsl, config, result);
    content = appendWeaveTask(content, isKotlinDsl, result);

    if (!config.isDryRun()) {
      Path backupPath = buildFile.resolveSibling(fileName + ".backup");
      Files.copy(buildFile, backupPath, StandardCopyOption.REPLACE_EXISTING);
      Files.writeString(buildFile, content, StandardCharsets.UTF_8);
    }

    return result.build();
  }

  /**
   * Checks whether the plugins block contains an existing AspectJ plugin that would conflict with
   * the manual weave task.
   *
   * @param content the file content to check
   * @return true if an AspectJ plugin is present in the plugins block
   */
  private boolean hasExistingAspectjPlugin(String content) {
    int openBrace = findBlockOpenBrace(content, "plugins");
    if (openBrace >= 0) {
      int closeBrace = findMatchingCloseBrace(content, openBrace);
      if (closeBrace >= 0) {
        String pluginsContent = content.substring(openBrace + 1, closeBrace);
        return pluginsContent.contains("aspectj");
      }
    }
    return false;
  }

  /**
   * Patches the configurations block to add {@code aspectjTools} and {@code aspect} entries.
   * Creates the block before the dependencies block if it does not exist.
   *
   * @param content the current file content
   * @param isKotlinDsl whether the file uses Kotlin DSL syntax
   * @param result the result builder to record actions
   * @return the possibly modified content
   */
  private String patchConfigurations(
      String content, boolean isKotlinDsl, PatchResult.Builder result) {
    if (content.contains(ASPECTJ_TOOLS_ARTIFACT)) {
      result.skip("aspectjTools configuration already present");
      return content;
    }

    String entries = formatConfigEntries(isKotlinDsl);
    int openBrace = findBlockOpenBrace(content, "configurations");

    if (openBrace >= 0) {
      int closeBrace = findMatchingCloseBrace(content, openBrace);
      if (closeBrace < 0) {
        result.warning("Could not find closing brace for configurations block");
        return content;
      }
      result.addition("Added aspectjTools and aspect configurations");
      return insertBeforeCloseBrace(content, closeBrace, entries);
    }

    String configBlock = "configurations {\n" + entries + "\n}";
    int depsStart = findBlockKeywordStart(content, "dependencies");
    if (depsStart >= 0) {
      result.addition("Added configurations block with aspectjTools and aspect");
      return content.substring(0, depsStart) + configBlock + "\n\n" + content.substring(depsStart);
    }

    result.addition("Added configurations block with aspectjTools and aspect");
    return content + "\n" + configBlock + "\n";
  }

  /**
   * Patches the dependencies block to add aspectjtools, pal-weave, and aspectjrt if not already
   * present.
   *
   * @param content the current file content (possibly modified by configuration patching)
   * @param isKotlinDsl whether the file uses Kotlin DSL syntax
   * @param config the init configuration providing version strings
   * @param result the result builder to record actions
   * @return the possibly modified content
   */
  private String patchDependencies(
      String content, boolean isKotlinDsl, InitConfig config, PatchResult.Builder result) {
    int openBrace = findBlockOpenBrace(content, "dependencies");
    boolean needsBlock = openBrace < 0;

    String blockContent = "";
    if (!needsBlock) {
      int closeBrace = findMatchingCloseBrace(content, openBrace);
      if (closeBrace >= 0) {
        blockContent = content.substring(openBrace + 1, closeBrace);
      }
    }

    StringBuilder newDeps = new StringBuilder();

    if (!blockContent.contains(ASPECTJ_TOOLS_ARTIFACT)) {
      newDeps.append(formatAspectjToolsDep(isKotlinDsl, config.getAspectjVersion())).append("\n");
      result.addition("Added aspectjtools tools dependency");
    } else {
      result.skip("aspectjtools dependency already present");
    }

    if (!blockContent.contains(PAL_WEAVE_ARTIFACT)) {
      newDeps.append(formatPalWeaveDep(isKotlinDsl, config.getPalVersion())).append("\n");
      result.addition("Added pal-weave aspect dependency");
    } else {
      result.skip("pal-weave dependency already present");
    }

    if (!blockContent.contains(ASPECTJ_RT_ARTIFACT)) {
      newDeps.append(formatAspectjRtDep(isKotlinDsl, config.getAspectjVersion())).append("\n");
      result.addition("Added aspectjrt implementation dependency");
    } else {
      result.skip("aspectjrt dependency already present");
    }

    if (config.isPalClient() && !blockContent.contains("pal-client")) {
      newDeps.append(formatPalClientDep(isKotlinDsl, config.getPalVersion())).append("\n");
      result.addition("Added pal-client implementation dependency");
    }

    if (newDeps.length() == 0) {
      return content;
    }

    if (needsBlock) {
      return content + "\ndependencies {\n" + newDeps + "}\n";
    }

    openBrace = findBlockOpenBrace(content, "dependencies");
    int closeBrace = findMatchingCloseBrace(content, openBrace);
    return insertBeforeCloseBrace(content, closeBrace, newDeps.toString().stripTrailing());
  }

  /**
   * Appends the {@code weaveClasses} task definition to the end of the file if not already present.
   *
   * @param content the current file content
   * @param isKotlinDsl whether the file uses Kotlin DSL syntax
   * @param result the result builder to record actions
   * @return the possibly modified content
   */
  private String appendWeaveTask(String content, boolean isKotlinDsl, PatchResult.Builder result) {
    if (content.contains(WEAVE_TASK_MARKER)) {
      result.skip("weaveClasses task already present");
      return content;
    }

    result.addition("Added weaveClasses task for post-test AspectJ weaving");
    String taskText = isKotlinDsl ? KOTLIN_WEAVE_TASK : GROOVY_WEAVE_TASK;
    return content.stripTrailing() + taskText;
  }

  /**
   * Validates that braces are balanced in the Gradle build file content. Throws if unbalanced,
   * preventing corruption of malformed input.
   *
   * @param content the file content to validate
   * @throws IOException if braces are unbalanced
   */
  private static void validateBraces(String content) throws IOException {
    int depth = 0;
    for (int i = 0; i < content.length(); i++) {
      char c = content.charAt(i);
      if (c == '{') {
        depth++;
      } else if (c == '}') {
        depth--;
        if (depth < 0) {
          throw new IOException("Invalid Gradle build file: unexpected closing brace");
        }
      }
    }
    if (depth != 0) {
      throw new IOException("Invalid Gradle build file: unbalanced braces");
    }
  }

  /**
   * Formats configuration entries for the configurations block.
   *
   * @param isKotlinDsl whether to use Kotlin DSL syntax
   * @return the formatted entries
   */
  private static String formatConfigEntries(boolean isKotlinDsl) {
    if (isKotlinDsl) {
      return "    create(\"aspectjTools\")\n    create(\"aspect\")";
    }
    return "    aspectjTools\n    aspect";
  }

  /**
   * Formats an aspectjtools tools dependency line.
   *
   * @param isKotlinDsl whether to use Kotlin DSL syntax
   * @param aspectjVersion the AspectJ version string
   * @return the formatted dependency line
   */
  private static String formatAspectjToolsDep(boolean isKotlinDsl, String aspectjVersion) {
    String coords = ASPECTJ_TOOLS_COORDS + ":" + aspectjVersion;
    if (isKotlinDsl) {
      return "    add(\"aspectjTools\", \"" + coords + "\")";
    }
    return "    aspectjTools '" + coords + "'";
  }

  /**
   * Formats a pal-weave aspect dependency line.
   *
   * @param isKotlinDsl whether to use Kotlin DSL syntax
   * @param palVersion the PAL version string
   * @return the formatted dependency line
   */
  private static String formatPalWeaveDep(boolean isKotlinDsl, String palVersion) {
    String coords = PAL_WEAVE_COORDS + ":" + palVersion;
    if (isKotlinDsl) {
      return "    add(\"aspect\", \"" + coords + "\")";
    }
    return "    aspect '" + coords + "'";
  }

  /**
   * Formats an aspectjrt implementation dependency line.
   *
   * @param isKotlinDsl whether to use Kotlin DSL syntax
   * @param aspectjVersion the AspectJ version string
   * @return the formatted dependency line
   */
  private static String formatAspectjRtDep(boolean isKotlinDsl, String aspectjVersion) {
    String coords = ASPECTJ_RT_COORDS + ":" + aspectjVersion;
    if (isKotlinDsl) {
      return "    implementation(\"" + coords + "\")";
    }
    return "    implementation '" + coords + "'";
  }

  /**
   * Formats the {@code pal-client} implementation dependency line.
   *
   * @param isKotlinDsl whether the project uses Kotlin DSL
   * @param palVersion the PAL version string
   * @return the formatted dependency line
   */
  private static String formatPalClientDep(boolean isKotlinDsl, String palVersion) {
    String coords = "io.quasient.pal:pal-client:" + palVersion;
    if (isKotlinDsl) {
      return "    implementation(\"" + coords + "\")";
    }
    return "    implementation '" + coords + "'";
  }

  /**
   * Finds the index of the opening brace of a named block (e.g., {@code plugins}, {@code
   * dependencies}).
   *
   * @param content the file content to search
   * @param blockName the block name to find
   * @return the index of the opening brace, or {@code -1} if not found
   */
  private static int findBlockOpenBrace(String content, String blockName) {
    Pattern p = Pattern.compile("\\b" + Pattern.quote(blockName) + "\\s*\\{");
    Matcher m = p.matcher(content);
    if (m.find()) {
      return m.end() - 1;
    }
    return -1;
  }

  /**
   * Finds the starting index of a named block keyword (e.g., the position of 'dependencies' in
   * 'dependencies {').
   *
   * @param content the file content to search
   * @param blockName the block name to find
   * @return the index of the keyword start, or {@code -1} if not found
   */
  private static int findBlockKeywordStart(String content, String blockName) {
    Pattern p = Pattern.compile("\\b" + Pattern.quote(blockName) + "\\s*\\{");
    Matcher m = p.matcher(content);
    if (m.find()) {
      return m.start();
    }
    return -1;
  }

  /**
   * Finds the index of the closing brace that matches the opening brace at the given index.
   *
   * @param content the file content
   * @param openBraceIndex the index of the opening brace
   * @return the index of the matching closing brace, or {@code -1} if not found
   */
  private static int findMatchingCloseBrace(String content, int openBraceIndex) {
    int depth = 0;
    for (int i = openBraceIndex; i < content.length(); i++) {
      char c = content.charAt(i);
      if (c == '{') {
        depth++;
      } else if (c == '}') {
        depth--;
        if (depth == 0) {
          return i;
        }
      }
    }
    return -1;
  }

  /**
   * Inserts new lines before the closing brace at the given index.
   *
   * @param content the file content
   * @param closeBraceIndex the index of the closing brace
   * @param newLines the lines to insert
   * @return the modified content
   */
  private static String insertBeforeCloseBrace(
      String content, int closeBraceIndex, String newLines) {
    String before = content.substring(0, closeBraceIndex);
    String after = content.substring(closeBraceIndex);
    if (!before.endsWith("\n")) {
      before += "\n";
    }
    return before + newLines + "\n" + after;
  }
}
