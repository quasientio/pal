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

  /**
   * Use of this flag prevents sending FIN twice, which causes the client to receive a second, empty
   * message *
   */
  private boolean finalFrameSent = false;

  private static final int CHUNK_SIZE = 4096;

  public WebSocketOutputStream(WebSocket conn) {
    this.connection = conn;
  }

  @Override
  public void write(int b) {
    buffer.write(b);
    if (buffer.size() >= CHUNK_SIZE) {
      flushBuffer(false);
    }
  }

  @Override
  public void flush() {
    flushBuffer(false);
  }

  @Override
  public void close() throws IOException {
    try {
      if (!finalFrameSent) {
        flushBuffer(true);
      }
    } finally {
      buffer.close();
    }
  }

  private void flushBuffer(boolean isLast) {
    // If there's no data and is not the final frame, skip sending
    if (buffer.size() == 0 && !isLast) {
      return;
    }

    byte[] data = buffer.toByteArray();
    buffer.reset();

    // Use TEXT for all frames; let the library handle fragmentation internally
    connection.sendFragmentedFrame(Opcode.TEXT, ByteBuffer.wrap(data), isLast);

    if (isLast) {
      finalFrameSent = true;
    }
  }
}
