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
package io.quasient.pal.core.transport.websocket;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import org.java_websocket.WebSocket;
import org.java_websocket.enums.Opcode;

/**
 * An OutputStream implementation that sends data over a WebSocket connection using fragmented
 * frames.
 *
 * <p>Data written to this stream is accumulated in an internal buffer and sent via the underlying
 * WebSocket connection when the buffer reaches a predefined chunk size or when flush/close is
 * invoked. The stream automatically manages the framing of data and ensures the final frame is sent
 * only once.
 */
@SuppressFBWarnings(
    value = "EI_EXPOSE_REP2",
    justification = "Stream wrapper - WebSocket connection intentionally shared")
public class WebSocketOutputStream extends OutputStream {

  /** The underlying WebSocket connection used to transmit data frames. */
  private final WebSocket connection;

  /**
   * A buffer that accumulates bytes written to the stream until they are sent as a WebSocket frame.
   */
  private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

  /**
   * Flag indicating whether the final frame (with FIN flag) has been sent. This prevents the
   * transmission of a second, empty final frame.
   */
  private boolean finalFrameSent = false;

  /** The maximum number of bytes to accumulate before automatically sending a fragment. */
  private static final int CHUNK_SIZE = 4096;

  /**
   * Constructs a new WebSocketOutputStream for the specified WebSocket connection.
   *
   * @param conn the WebSocket connection to which data frames will be sent; must be non-null.
   */
  public WebSocketOutputStream(WebSocket conn) {
    this.connection = conn;
  }

  /**
   * {@inheritDoc}
   *
   * <p>Writes a single byte to the internal buffer. If the buffer size reaches the defined chunk
   * size, the accumulated data is flushed as a fragmented WebSocket frame.
   *
   * @param b the byte to be written.
   */
  @Override
  public void write(int b) {
    buffer.write(b);
    if (buffer.size() >= CHUNK_SIZE) {
      flushBuffer(false);
    }
  }

  /**
   * {@inheritDoc}
   *
   * <p>Flushes any buffered data by sending it as a WebSocket fragmented frame. This method does
   * not force a send if the buffer is empty.
   */
  @Override
  public void flush() {
    flushBuffer(false);
  }

  /**
   * {@inheritDoc}
   *
   * <p>Flushes any remaining data as the final WebSocket fragmented frame (if not already sent) and
   * releases any resources associated with the internal buffer.
   *
   * @throws IOException if an I/O error occurs while closing the internal buffer.
   */
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

  /**
   * Flushes the internal buffer by sending its content as a WebSocket fragmented frame.
   *
   * <p>If the buffer is empty and the frame is not marked as final, no frame is sent. The frame is
   * transmitted using a TEXT opcode, relying on the WebSocket library to manage fragmentation
   * internally. When the frame sent is the final one, it marks the stream as closed for further
   * final frame transmissions.
   *
   * @param isLast {@code true} if the data being flushed represents the final frame of the
   *     connection, {@code false} otherwise.
   */
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
