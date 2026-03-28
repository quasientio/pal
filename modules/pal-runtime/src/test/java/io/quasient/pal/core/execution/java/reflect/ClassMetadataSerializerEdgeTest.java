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
package io.quasient.pal.core.execution.java.reflect;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

import io.quasient.pal.core.rpc.policy.RpcPolicy;
import io.quasient.pal.core.rpc.policy.RpcPolicyAction;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import org.junit.Test;

/** Edge-case tests for {@link ClassMetadataSerializer}. */
public class ClassMetadataSerializerEdgeTest {

  /** An allow-all policy that permits all members on all channels. */
  private static final RpcPolicy ALLOW_ALL_POLICY = new RpcPolicy(List.of(), RpcPolicyAction.ALLOW);

  @Test
  public void compressedSingleClassMerged_containsArrayList() throws Exception {
    ClassMetadataSerializer serializer = new ClassMetadataSerializer(ALLOW_ALL_POLICY);
    Path out =
        serializer.scannedClasspathToJson(
            /* compressAndEncode */ true,
            Set.of("java.util.ArrayList"),
            null,
            /* mergeAncestry */ true);

    // read, base64-decode and gunzip
    byte[] encoded = Files.readAllBytes(out);
    Files.deleteIfExists(out);
    byte[] gz = Base64.getDecoder().decode(encoded);
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try (GZIPInputStream gzis = new GZIPInputStream(new ByteArrayInputStream(gz))) {
      gzis.transferTo(baos);
    }
    String json = baos.toString(StandardCharsets.UTF_8);

    // simple checks
    assertThat(json, containsString("\"className\":\"java.util.ArrayList\""));
    // "methods" and "fields" arrays are present (merged ancestry)
    assertThat(json, containsString("\"methods\":"));
    assertThat(json, containsString("\"fields\":"));
  }

  @Test
  public void allowAllPolicy_includesNonPublicMethods() throws Exception {
    ClassMetadataSerializer serializer = new ClassMetadataSerializer(ALLOW_ALL_POLICY);
    Path out =
        serializer.scannedClasspathToJson(
            /* compressAndEncode */ false, Set.of("java.util.ArrayList"), null, false);
    String json = Files.readString(out);
    Files.deleteIfExists(out);

    // With allow-all policy, non-public methods are included.
    // modifiers=0 indicates package-private in ClassGraph's modifier encoding.
    boolean hasNonPublic = json.contains("\"modifiers\":0");
    assertThat(hasNonPublic, is(true));
  }
}
