/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.core.bench;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.EnumSet;

/**
 * Helper that uses {@link Files} to implement recursive deletion of all contents within a
 * directory.
 */
public final class MoreFiles {

  /** Avoid instantiation of utility class */
  private MoreFiles() {}

  /**
   * Deletes all entries inside {@code dir} but leaves {@code dir} itself. Does not follow symlinks;
   * symlinks in {@code dir} are deleted as links.
   *
   * @param dir the target path
   */
  public static void deleteDirectoryContents(Path dir) throws IOException {
    if (!Files.exists(dir)) return;
    if (!Files.isDirectory(dir, LinkOption.NOFOLLOW_LINKS)) {
      throw new NotDirectoryException(dir.toString());
    }

    Files.walkFileTree(
        dir,
        EnumSet.noneOf(FileVisitOption.class),
        Integer.MAX_VALUE,
        new SimpleFileVisitor<>() {
          @Override
          public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
              throws IOException {
            Files.deleteIfExists(file);
            return FileVisitResult.CONTINUE;
          }

          @Override
          public FileVisitResult postVisitDirectory(Path directory, IOException exc)
              throws IOException {
            if (exc != null) throw exc;
            // Delete child directories, but keep the root 'dir' itself.
            if (!directory.equals(dir)) {
              Files.deleteIfExists(directory);
            }
            return FileVisitResult.CONTINUE;
          }
        });
  }
}
