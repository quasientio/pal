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
package io.quasient.pal.common.util;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import org.junit.Test;

public class ByteSizeConverterTest {

  @Test
  public void humanReadableByteCount_bin() {
    assertThat(ByteSizeConverter.humanReadableByteCount(0, false), is("0 B"));
    assertThat(ByteSizeConverter.humanReadableByteCount(27, false), is("27 B"));
    assertThat(ByteSizeConverter.humanReadableByteCount(999, false), is("999 B"));
    assertThat(ByteSizeConverter.humanReadableByteCount(1000, false), is("1000 B"));
    assertThat(ByteSizeConverter.humanReadableByteCount(1023, false), is("1023 B"));
    assertThat(ByteSizeConverter.humanReadableByteCount(1024, false), is("1.0 KiB"));
    assertThat(ByteSizeConverter.humanReadableByteCount(1728, false), is("1.7 KiB"));
    assertThat(ByteSizeConverter.humanReadableByteCount(110592, false), is("108.0 KiB"));
    assertThat(ByteSizeConverter.humanReadableByteCount(7077888, false), is("6.8 MiB"));
    assertThat(ByteSizeConverter.humanReadableByteCount(452984832, false), is("432.0 MiB"));
    assertThat(ByteSizeConverter.humanReadableByteCount(28991029248L, false), is("27.0 GiB"));
    assertThat(ByteSizeConverter.humanReadableByteCount(1855425871872L, false), is("1.7 TiB"));
    assertThat(
        ByteSizeConverter.humanReadableByteCount(9223372036854775807L, false), is("8.0 EiB"));
  }

  @Test
  public void humanReadableByteCount_si() {
    assertThat(ByteSizeConverter.humanReadableByteCount(0, true), is("0 B"));
    assertThat(ByteSizeConverter.humanReadableByteCount(27, true), is("27 B"));
    assertThat(ByteSizeConverter.humanReadableByteCount(999, true), is("999 B"));
    assertThat(ByteSizeConverter.humanReadableByteCount(1000, true), is("1.0 kB"));
    assertThat(ByteSizeConverter.humanReadableByteCount(1023, true), is("1.0 kB"));
    assertThat(ByteSizeConverter.humanReadableByteCount(1024, true), is("1.0 kB"));
    assertThat(ByteSizeConverter.humanReadableByteCount(1728, true), is("1.7 kB"));
    assertThat(ByteSizeConverter.humanReadableByteCount(110592, true), is("110.6 kB"));
    assertThat(ByteSizeConverter.humanReadableByteCount(7077888, true), is("7.1 MB"));
    assertThat(ByteSizeConverter.humanReadableByteCount(452984832, true), is("453.0 MB"));
    assertThat(ByteSizeConverter.humanReadableByteCount(28991029248L, true), is("29.0 GB"));
    assertThat(ByteSizeConverter.humanReadableByteCount(1855425871872L, true), is("1.9 TB"));
    assertThat(ByteSizeConverter.humanReadableByteCount(9223372036854775807L, true), is("9.2 EB"));
  }
}
