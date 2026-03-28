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
package io.quasient.pal.serdes.colfer;

import static org.junit.Assert.assertThrows;

import io.quasient.pal.common.objects.ObjectRef;
import java.util.UUID;
import org.junit.Test;

/**
 * Focused test for argument/parameter length validation in MessageBuilder#createNamedParameters.
 */
public class MessageBuilderNamedParametersErrorTest {

  @Test
  public void buildClassMethod_mismatched_arg_lengths_throws() {
    MessageBuilder b = new MessageBuilder(UUID.randomUUID(), Boolean.toString(false));
    String[] paramTypes = new String[] {"int", "int"};
    Object[] args = new Object[] {1}; // fewer args than paramTypes
    ObjectRef[] argRefs = new ObjectRef[] {null, null};

    // With strict validation, mismatched lengths should cause IllegalArgumentException.
    assertThrows(
        IllegalArgumentException.class,
        () ->
            b.buildClassMethod(
                UUID.randomUUID(),
                "java.lang.Math",
                "max",
                paramTypes,
                this,
                ObjectRef.randomRef(),
                args,
                argRefs));
  }
}
