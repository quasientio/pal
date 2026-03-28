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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.Test;

/** Tests for merge/override behavior in {@link ClassMetadataSerializer}. */
public class ClassMetadataSerializerMergeOverrideTest {

  @Test
  public void mergeAncestry_marksOverriddenMembers() throws Exception {
    RpcPolicy allowAll = new RpcPolicy(List.of(), RpcPolicyAction.ALLOW);
    ClassMetadataSerializer serializer = new ClassMetadataSerializer(allowAll);
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
