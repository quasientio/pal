/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.core.transport.zmq;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Test;
import org.zeromq.ZMQ.Socket;
import org.zeromq.ZMQException;
import zmq.ZError;

/**
 * Unit tests for {@link ZmqUtils}.
 *
 * <p>Tests the safeSend utility methods for both String and byte[] payloads.
 */
public class ZmqUtilsTest {

  // ===== safeSend(Socket, String) Tests =====

  /** Tests that null socket returns false without throwing. */
  @Test
  public void safeSendString_nullSocket_returnsFalse() {
    boolean result = ZmqUtils.safeSend(null, "message");

    assertThat(result, is(false));
  }

  /** Tests that null message returns false without throwing. */
  @Test
  public void safeSendString_nullMessage_returnsFalse() {
    Socket socket = mock(Socket.class);

    boolean result = ZmqUtils.safeSend(socket, (String) null);

    assertThat(result, is(false));
    verify(socket, never()).send(any(String.class), anyInt());
  }

  /** Tests that both null socket and null message returns false. */
  @Test
  public void safeSendString_nullSocketAndMessage_returnsFalse() {
    boolean result = ZmqUtils.safeSend(null, (String) null);

    assertThat(result, is(false));
  }

  /** Tests successful send returns true. */
  @Test
  public void safeSendString_successfulSend_returnsTrue() {
    Socket socket = mock(Socket.class);
    when(socket.send("test message", 0)).thenReturn(true);

    boolean result = ZmqUtils.safeSend(socket, "test message");

    assertThat(result, is(true));
    verify(socket).send("test message", 0);
  }

  /** Tests that socket.send returning false is propagated. */
  @Test
  public void safeSendString_sendReturnsFalse_returnsFalse() {
    Socket socket = mock(Socket.class);
    when(socket.send("message", 0)).thenReturn(false);

    boolean result = ZmqUtils.safeSend(socket, "message");

    assertThat(result, is(false));
  }

  /** Tests that ETERM exception is swallowed silently. */
  @Test
  public void safeSendString_etermException_returnsFalseQuietly() {
    Socket socket = mock(Socket.class);
    when(socket.send(any(String.class), eq(0))).thenThrow(new ZMQException(ZError.ETERM));

    boolean result = ZmqUtils.safeSend(socket, "message");

    assertThat(result, is(false));
  }

  /** Tests that EINTR exception is swallowed silently. */
  @Test
  public void safeSendString_eintrException_returnsFalseQuietly() {
    Socket socket = mock(Socket.class);
    when(socket.send(any(String.class), eq(0))).thenThrow(new ZMQException(ZError.EINTR));

    boolean result = ZmqUtils.safeSend(socket, "message");

    assertThat(result, is(false));
  }

  /** Tests that other ZMQ exceptions are logged and return false. */
  @Test
  public void safeSendString_otherZmqException_returnsFalseWithLogging() {
    Socket socket = mock(Socket.class);
    // Use a different error code (not ETERM or EINTR)
    when(socket.send(any(String.class), eq(0))).thenThrow(new ZMQException(ZError.ENOTSOCK));

    boolean result = ZmqUtils.safeSend(socket, "message");

    assertThat(result, is(false));
  }

  /** Tests that generic exceptions are caught and return false. */
  @Test
  public void safeSendString_genericException_returnsFalse() {
    Socket socket = mock(Socket.class);
    when(socket.send(any(String.class), eq(0))).thenThrow(new RuntimeException("unexpected"));

    boolean result = ZmqUtils.safeSend(socket, "message");

    assertThat(result, is(false));
  }

  // ===== safeSend(Socket, byte[]) Tests =====

  /** Tests that null socket returns false for byte array. */
  @Test
  public void safeSendBytes_nullSocket_returnsFalse() {
    boolean result = ZmqUtils.safeSend(null, new byte[] {1, 2, 3});

    assertThat(result, is(false));
  }

  /** Tests that null byte array returns false. */
  @Test
  public void safeSendBytes_nullFrame_returnsFalse() {
    Socket socket = mock(Socket.class);

    boolean result = ZmqUtils.safeSend(socket, (byte[]) null);

    assertThat(result, is(false));
    verify(socket, never()).send(any(byte[].class), anyInt());
  }

  /** Tests that both null socket and null byte array returns false. */
  @Test
  public void safeSendBytes_nullSocketAndFrame_returnsFalse() {
    boolean result = ZmqUtils.safeSend(null, (byte[]) null);

    assertThat(result, is(false));
  }

  /** Tests successful byte array send returns true. */
  @Test
  public void safeSendBytes_successfulSend_returnsTrue() {
    Socket socket = mock(Socket.class);
    byte[] data = new byte[] {1, 2, 3, 4, 5};
    when(socket.send(data, 0)).thenReturn(true);

    boolean result = ZmqUtils.safeSend(socket, data);

    assertThat(result, is(true));
    verify(socket).send(data, 0);
  }

  /** Tests that socket.send returning false is propagated for bytes. */
  @Test
  public void safeSendBytes_sendReturnsFalse_returnsFalse() {
    Socket socket = mock(Socket.class);
    byte[] data = new byte[] {1, 2, 3};
    when(socket.send(data, 0)).thenReturn(false);

    boolean result = ZmqUtils.safeSend(socket, data);

    assertThat(result, is(false));
  }

  /** Tests that ETERM exception is swallowed for byte send. */
  @Test
  public void safeSendBytes_etermException_returnsFalseQuietly() {
    Socket socket = mock(Socket.class);
    when(socket.send(any(byte[].class), eq(0))).thenThrow(new ZMQException(ZError.ETERM));

    boolean result = ZmqUtils.safeSend(socket, new byte[] {1, 2, 3});

    assertThat(result, is(false));
  }

  /** Tests that EINTR exception is swallowed for byte send. */
  @Test
  public void safeSendBytes_eintrException_returnsFalseQuietly() {
    Socket socket = mock(Socket.class);
    when(socket.send(any(byte[].class), eq(0))).thenThrow(new ZMQException(ZError.EINTR));

    boolean result = ZmqUtils.safeSend(socket, new byte[] {1, 2, 3});

    assertThat(result, is(false));
  }

  /** Tests that other ZMQ exceptions are logged for byte send. */
  @Test
  public void safeSendBytes_otherZmqException_returnsFalseWithLogging() {
    Socket socket = mock(Socket.class);
    when(socket.send(any(byte[].class), eq(0))).thenThrow(new ZMQException(ZError.ENOTSOCK));

    boolean result = ZmqUtils.safeSend(socket, new byte[] {1, 2, 3});

    assertThat(result, is(false));
  }

  /** Tests that generic exceptions are caught for byte send. */
  @Test
  public void safeSendBytes_genericException_returnsFalse() {
    Socket socket = mock(Socket.class);
    when(socket.send(any(byte[].class), eq(0))).thenThrow(new RuntimeException("unexpected"));

    boolean result = ZmqUtils.safeSend(socket, new byte[] {1, 2, 3});

    assertThat(result, is(false));
  }

  /** Tests with empty byte array. */
  @Test
  public void safeSendBytes_emptyArray_sendsSuccessfully() {
    Socket socket = mock(Socket.class);
    byte[] emptyData = new byte[0];
    when(socket.send(emptyData, 0)).thenReturn(true);

    boolean result = ZmqUtils.safeSend(socket, emptyData);

    assertThat(result, is(true));
    verify(socket).send(emptyData, 0);
  }

  /** Tests with empty string. */
  @Test
  public void safeSendString_emptyString_sendsSuccessfully() {
    Socket socket = mock(Socket.class);
    when(socket.send("", 0)).thenReturn(true);

    boolean result = ZmqUtils.safeSend(socket, "");

    assertThat(result, is(true));
    verify(socket).send("", 0);
  }
}
