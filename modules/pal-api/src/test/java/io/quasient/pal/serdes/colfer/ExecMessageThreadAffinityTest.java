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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import com.google.gson.JsonObject;
import io.quasient.pal.messages.colfer.ClassMethodCall;
import io.quasient.pal.messages.colfer.ExecMessage;
import org.junit.Test;

/**
 * Tests for the {@code threadAffinity} field on {@link ExecMessage}.
 *
 * <p>Verifies Colfer round-trip serialization, reset behavior, and JSON deserialization of the
 * threadAffinity field.
 */
public class ExecMessageThreadAffinityTest {

  /**
   * Test specification: roundTripWithThreadAffinity
   *
   * <p>Verifies that threadAffinity is preserved through a Colfer marshal/unmarshal round-trip.
   *
   * <p>Given: ExecMessage with threadAffinity set to "fx-thread" and a classMethodCall When:
   * Serialized to bytes via marshal() then deserialized via unmarshal() Then: Deserialized message
   * has threadAffinity == "fx-thread"
   */
  @Test
  public void roundTripWithThreadAffinity() throws Exception {
    // Given: ExecMessage with threadAffinity set to "fx-thread" and a classMethodCall
    ExecMessage message = new ExecMessage();
    message.setPeerUuid("test-peer-uuid");
    message.setMessageId("test-message-id");
    message.setClassMethodCall(new ClassMethodCall());
    message.setThreadAffinity("fx-thread");

    // When: Serialized to bytes via marshal() then deserialized via unmarshal()
    byte[] buf = new byte[message.marshalFit()];
    int length = message.marshal(buf, 0);

    ExecMessage deserialized = new ExecMessage();
    deserialized.unmarshal(buf, 0, length);

    // Then: Deserialized message has threadAffinity == "fx-thread"
    assertThat(deserialized.getThreadAffinity(), is("fx-thread"));
  }

  /**
   * Test specification: roundTripWithNullThreadAffinity
   *
   * <p>Verifies that a null/default threadAffinity survives round-trip serialization.
   *
   * <p>Given: ExecMessage with threadAffinity left null (default) When: Serialized then
   * deserialized Then: Deserialized message has threadAffinity == empty string (Colfer default)
   */
  @Test
  public void roundTripWithNullThreadAffinity() throws Exception {
    // Given: ExecMessage with threadAffinity left at default (empty string in Colfer)
    ExecMessage message = new ExecMessage();
    message.setPeerUuid("test-peer-uuid");
    message.setMessageId("test-message-id");

    // When: Serialized then deserialized
    byte[] buf = new byte[message.marshalFit()];
    int length = message.marshal(buf, 0);

    ExecMessage deserialized = new ExecMessage();
    deserialized.unmarshal(buf, 0, length);

    // Then: Deserialized message has threadAffinity == empty string (Colfer text default)
    assertThat(deserialized.getThreadAffinity(), is(""));
  }

  /**
   * Test specification: resetClearsThreadAffinity
   *
   * <p>Verifies that reset() clears the threadAffinity field back to default.
   *
   * <p>Given: ExecMessage with threadAffinity set to "fx-thread" When: reset() is called Then:
   * threadAffinity is empty string (Colfer default after init())
   */
  @Test
  public void resetClearsThreadAffinity() {
    // Given: ExecMessage with threadAffinity set to "fx-thread"
    ExecMessage message = new ExecMessage();
    message.setThreadAffinity("fx-thread");
    assertThat(message.getThreadAffinity(), is("fx-thread"));

    // When: reset() is called
    message.reset();

    // Then: threadAffinity is empty string (Colfer text default after init())
    assertThat(message.getThreadAffinity(), is(""));
  }

  /**
   * Test specification: fromJsonParsesThreadAffinity
   *
   * <p>Verifies that fromJson correctly parses the threadAffinity field from a JSON object.
   *
   * <p>Given: JSON object with "threadAffinity": "fx-thread" and required fields When:
   * ExecMessage.fromJson(json) is called Then: threadAffinity == "fx-thread"
   */
  @Test
  public void fromJsonParsesThreadAffinity() {
    // Given: JSON object with "threadAffinity": "fx-thread" and required fields
    JsonObject json = new JsonObject();
    json.addProperty("peerUuid", "test-peer-uuid");
    json.addProperty("messageId", "test-message-id");
    json.addProperty("threadAffinity", "fx-thread");

    // When: ExecMessage.fromJson(json) is called
    ExecMessage message = new ExecMessage().fromJson(json);

    // Then: threadAffinity == "fx-thread"
    assertThat(message.getThreadAffinity(), is("fx-thread"));
  }

  /**
   * Test specification: fromJsonOmittedThreadAffinityIsNull
   *
   * <p>Verifies that omitting threadAffinity from JSON leaves the field at its default value.
   *
   * <p>Given: JSON object without threadAffinity field When: ExecMessage.fromJson(json) is called
   * Then: threadAffinity is empty string (Colfer default)
   */
  @Test
  public void fromJsonOmittedThreadAffinityIsNull() {
    // Given: JSON object without threadAffinity field
    JsonObject json = new JsonObject();
    json.addProperty("peerUuid", "test-peer-uuid");
    json.addProperty("messageId", "test-message-id");

    // When: ExecMessage.fromJson(json) is called
    ExecMessage message = new ExecMessage().fromJson(json);

    // Then: threadAffinity is empty string (Colfer text default, field not overwritten)
    assertThat(message.getThreadAffinity(), is(""));
  }
}
