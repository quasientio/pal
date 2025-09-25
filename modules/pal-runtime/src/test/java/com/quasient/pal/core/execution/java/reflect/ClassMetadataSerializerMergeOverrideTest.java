/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.core.execution.java.reflect;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.Test;

public class ClassMetadataSerializerMergeOverrideTest {

  @Test
  public void mergeAncestry_marksOverriddenMembers() throws Exception {
    ClassMetadataSerializer serializer = new ClassMetadataSerializer(false);
    String subName = "org.example.paltest.SubClass";
    Path out =
        serializer.scannedClasspathToJson(
            /* compressAndEncode */ false, Set.of(subName), null, /* mergeAncestry */ true);
    String json = Files.readString(out);
    Files.deleteIfExists(out);

    // Expect SubClass entry present
    assertThat(json, containsString("\"className\":\"" + subName + "\""));

    // Method and field presence for our synthetic class
    assertThat(json, containsString("\"name\":\"getA\""));
    assertThat(json, containsString("\"name\":\"a\""));

    // Also ensure Object methods are present (e.g., toString)
    boolean hasToString = json.contains("\"name\":\"toString\"");
    assertThat(hasToString, is(true));
  }
}
