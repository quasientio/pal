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

import io.quasient.pal.core.rpc.policy.RpcPolicy;
import io.quasient.pal.core.rpc.policy.RpcPolicyAction;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.Test;

/** Tests for generic signature handling in {@link ClassMetadataSerializer}. */
public class ClassMetadataSerializerGenericSignatureTest {

  @Test
  public void includesGenericParameterTypesInMethodSignatures() throws Exception {
    RpcPolicy allowAll = new RpcPolicy(List.of(), RpcPolicyAction.ALLOW);
    ClassMetadataSerializer serializer = new ClassMetadataSerializer(allowAll);
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
