/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
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
 *   <li>Adding the {@code io.freefair.aspectj.post-compile-weaving} plugin to the {@code plugins}
 *       block
 *   <li>Adding the {@code pal-weave} aspect dependency
 *   <li>Adding the {@code aspectjrt} implementation dependency
 * </ul>
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

  /** The freefair AspectJ post-compile weaving plugin ID. */
  private static final String FREEFAIR_PLUGIN_ID = "io.freefair.aspectj.post-compile-weaving";

  /** The PAL weave artifact name used for idempotency detection. */
  private static final String PAL_WEAVE_ARTIFACT = "pal-weave";

  /** The AspectJ runtime artifact name used for idempotency detection. */
  private static final String ASPECTJ_RT_ARTIFACT = "aspectjrt";

  /** The PAL weave Maven coordinates prefix. */
  private static final String PAL_WEAVE_COORDS = "io.quasient.pal:pal-weave";

  /** The AspectJ runtime Maven coordinates prefix. */
  private static final String ASPECTJ_RT_COORDS = "org.aspectj:aspectjrt";

  /**
   * Patches an existing Gradle build file to add PAL AspectJ weaving configuration.
   *
   * <p>Operations performed:
   *
   * <ol>
   *   <li>Adds the freefair AspectJ plugin to the {@code plugins} block (creates block if missing)
   *   <li>Adds {@code pal-weave} as an {@code aspect} dependency (creates block if missing)
   *   <li>Adds {@code aspectjrt} as an {@code implementation} dependency
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

    content = patchPlugins(content, isKotlinDsl, result);
    content = patchDependencies(content, isKotlinDsl, config, result);

    if (!config.isDryRun()) {
      Path backupPath = buildFile.resolveSibling(fileName + ".backup");
      Files.copy(buildFile, backupPath, StandardCopyOption.REPLACE_EXISTING);
      Files.writeString(buildFile, content, StandardCharsets.UTF_8);
    }

    return result.build();
  }

  /**
   * Patches the plugins block to add the freefair AspectJ plugin if not already present.
   *
   * @param content the current file content
   * @param isKotlinDsl whether the file uses Kotlin DSL syntax
   * @param result the result builder to record actions
   * @return the possibly modified content
   */
  private String patchPlugins(String content, boolean isKotlinDsl, PatchResult.Builder result) {
    int openBrace = findBlockOpenBrace(content, "plugins");

    if (openBrace < 0) {
      String pluginLine = formatPluginLine(isKotlinDsl);
      result.addition("Added plugins block with AspectJ plugin");
      return content + "\nplugins {\n" + pluginLine + "\n}\n";
    }

    int closeBrace = findMatchingCloseBrace(content, openBrace);
    if (closeBrace < 0) {
      result.warning("Could not find closing brace for plugins block");
      return content;
    }

    String blockContent = content.substring(openBrace + 1, closeBrace);

    if (blockContent.contains(FREEFAIR_PLUGIN_ID)) {
      result.skip("AspectJ plugin already present");
      return content;
    }

    if (containsOtherAspectjPlugin(blockContent)) {
      result.warning("A different AspectJ plugin is already present");
      return content;
    }

    String pluginLine = formatPluginLine(isKotlinDsl);
    result.addition("Added AspectJ post-compile weaving plugin");
    return insertBeforeCloseBrace(content, closeBrace, pluginLine);
  }

  /**
   * Patches the dependencies block to add pal-weave and aspectjrt if not already present.
   *
   * @param content the current file content (possibly modified by plugin patching)
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
   * Checks whether the plugins block content contains an AspectJ plugin other than the freefair
   * one.
   *
   * @param blockContent the content between the plugins block braces
   * @return true if a different AspectJ plugin is present
   */
  private static boolean containsOtherAspectjPlugin(String blockContent) {
    return blockContent.contains("aspectj") && !blockContent.contains(FREEFAIR_PLUGIN_ID);
  }

  /**
   * Formats a plugin declaration line for the freefair AspectJ plugin.
   *
   * @param isKotlinDsl whether to use Kotlin DSL syntax
   * @return the formatted plugin line
   */
  private static String formatPluginLine(boolean isKotlinDsl) {
    if (isKotlinDsl) {
      return "    id(\"" + FREEFAIR_PLUGIN_ID + "\")";
    }
    return "    id '" + FREEFAIR_PLUGIN_ID + "'";
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
      return "    aspect(\"" + coords + "\")";
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
