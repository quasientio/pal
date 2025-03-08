package net.ittera.pal.serdes.jsonrpc;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import net.ittera.pal.messages.jsonrpc.ResponseObject;

public class ResponseObjectSerializer extends JsonSerializer<ResponseObject> {

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

    if (responseObject.getRef() != null) {
      gen.writeNumberField("ref", responseObject.getRef());
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
