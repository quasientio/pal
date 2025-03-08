package net.ittera.pal.core.rpc;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import org.java_websocket.WebSocket;
import org.java_websocket.enums.Opcode;

public class WebSocketOutputStream extends OutputStream {
  private final WebSocket connection;
  private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
  private boolean isFirstFrame = true;
  private static final int CHUNK_SIZE = 4096;

  public WebSocketOutputStream(WebSocket conn) {
    this.connection = conn;
  }

  @Override
  public void write(int b) throws IOException {
    buffer.write(b);
    if (buffer.size() >= CHUNK_SIZE) {
      flushBuffer(false);
    }
  }

  @Override
  public void flush() throws IOException {
    flushBuffer(false);
  }

  @Override
  public void close() throws IOException {
    flushBuffer(true); // Send final fragment
    buffer.close();
  }

  private void flushBuffer(boolean isLast) {
    if (buffer.size() == 0 && !isLast) return;

    byte[] data = buffer.toByteArray();
    buffer.reset();

    //    Opcode opcode = isFirstFrame ? Opcode.TEXT : null;
    connection.sendFragmentedFrame(Opcode.TEXT, ByteBuffer.wrap(data), isLast);

    // Update state
    if (isFirstFrame) {
      isFirstFrame = false;
    }
  }
}
