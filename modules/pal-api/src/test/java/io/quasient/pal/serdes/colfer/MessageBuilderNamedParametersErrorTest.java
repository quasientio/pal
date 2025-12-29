/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
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
