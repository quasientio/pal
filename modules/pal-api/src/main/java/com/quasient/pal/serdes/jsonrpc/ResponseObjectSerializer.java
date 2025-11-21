/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.serdes.jsonrpc;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.quasient.pal.messages.jsonrpc.ResponseObject;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

/**
 * Custom serializer that converts ResponseObject instances into JSON format.
 *
 * <p>This serializer is tailored for JSON-RPC response objects. It conditionally writes fields such
 * as "type", "null", "ref", and "value" based on their availability within the ResponseObject. For
 * the "value" field, if a stream is provided via the SerializerProvider under the attribute
 * "valueInputStream", the serializer reads and writes the content from this stream using UTF-8
 * encoding; otherwise, it serializes the value as a simple string.
 */
public class ResponseObjectSerializer extends JsonSerializer<ResponseObject> {

  /**
   * {@inheritDoc}
   *
   * <p>Serializes the given ResponseObject to JSON using the provided JsonGenerator. The method
   * writes a JSON object, conditionally including the "type" and "ref" fields if they are non-null,
   * and always writing the "null" field. For the "value" field, it checks for a "valueInputStream"
   * attribute in the SerializerProvider to determine whether to stream the value or use the
   * in-memory value.
   *
   * @param responseObject the ResponseObject instance to be serialized; expected to be non-null
   * @param gen the JsonGenerator used to output JSON data
   * @param provider the SerializerProvider that may supply additional attributes required during
   *     serialization
   * @throws IOException if an I/O error occurs while generating the JSON output
   */
  @Override
  public void serialize(
      ResponseObject responseObject, JsonGenerator gen, SerializerProvider provider)
      throws IOException {
    gen.writeStartObject();

    // Write other fields (type, null, ref)
    if (responseObject.getType() != null) {
      gen.writeStringField("type", responseObject.getType());
    }
    gen.writeBooleanField("null", responseObject.getIsNull());

    Integer ref = responseObject.getRef();
    if (ref != null) {
      gen.writeNumberField("ref", ref.intValue());
    }

    // Handle the 'value' field
    gen.writeFieldName("value");
    InputStream dataStream = (InputStream) provider.getAttribute("valueInputStream");

    if (dataStream != null) {
      // Wrap InputStream as a UTF-8 Reader
      Reader reader = new InputStreamReader(dataStream, StandardCharsets.UTF_8);
      // Jackson handles quotes and escaping automatically
      gen.writeString(reader, -1); // -1 = read until EOF
    } else {
      // Fallback to default String serialization
      gen.writeString(responseObject.getValue());
    }

    gen.writeEndObject();
  }
}
