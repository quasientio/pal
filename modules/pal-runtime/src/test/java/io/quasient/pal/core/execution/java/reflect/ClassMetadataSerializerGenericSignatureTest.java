/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.core.execution.java.reflect;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.Test;

public class ClassMetadataSerializerGenericSignatureTest {

  @Test
  public void includesGenericParameterTypesInMethodSignatures() throws Exception {
    ClassMetadataSerializer serializer = new ClassMetadataSerializer(false);
    String cls = "org.example.paltest.GenericMethods";
    Path out = serializer.scannedClasspathToJson(false, Set.of(cls), null, false);
    String json = Files.readString(out);
    Files.deleteIfExists(out);

    // Presence checks: class entry, method name, and a generic parameter with List<String>
    assertThat(json, containsString("\"className\":\"" + cls + "\""));
    assertThat(json, containsString("\"name\":\"echo\""));
    assertThat(json, containsString("java.util.List<java.lang.String>"));
  }
}
