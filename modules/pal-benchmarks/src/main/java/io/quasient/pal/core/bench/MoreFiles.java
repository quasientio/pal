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
