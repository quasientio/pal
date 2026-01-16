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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.ZMQ.Socket;
import org.zeromq.ZMQException;
import zmq.ZError;

/**
 * Utility class to send 1-frame replies through a ZMQ socket without propagating errors. More
 * specifically, it swallows ETERM / EINTR – the two errors that appear when the ZContext/socket is
 * being torn down, and logs other errors with at DEBUG level.
 */
public final class ZmqUtils {

  /** Logger instance */
  private static final Logger logger = LoggerFactory.getLogger(ZmqUtils.class);

  /** static helper, no public ctor */
  private ZmqUtils() {}

  /**
   * Best-effort send of a single‐frame string reply.
   *
   * @param sock the REP / REQ / whatever socket – may be {@code null}
   * @param msg frame to send; {@code null} is ignored
   * @return {@code true} if send() reported success, {@code false} otherwise.
   */
  public static boolean safeSend(Socket sock, String msg) {
    if (sock == null || msg == null) {
      return false;
    }
    try {
      return sock.send(msg, 0); // 0 = default (blocking if HWM reached)
    } catch (ZMQException ex) {
      // Ignore common “shutdown” errors, log the rest at DEBUG to avoid noise in prod
      int err = ex.getErrorCode();
      if (err != ZError.ETERM && err != ZError.EINTR) {
        logger.debug("ZMQ send failed (err={}): {}", err, msg, ex);
      }
    } catch (Exception ex) {
      logger.debug("Unexpected exception in safeSend: {}", msg, ex);
    }
    return false;
  }

  /**
   * Overload for single-frame byte[] reply.
   *
   * @param sock the REP / REQ / whatever socket – may be {@code null}
   * @param frame bytes to send; {@code null} is ignored
   * @return {@code true} if send() reported success, {@code false} otherwise.
   */
  public static boolean safeSend(Socket sock, byte[] frame) {
    if (sock == null || frame == null) {
      return false;
    }
    try {
      return sock.send(frame, 0);
    } catch (ZMQException ex) {
      int err = ex.getErrorCode();
      if (err != ZError.ETERM && err != ZError.EINTR) {
        logger.debug("ZMQ send failed (err={}): {} bytes", err, frame.length, ex);
      }
    } catch (Exception ex) {
      logger.debug("Unexpected exception in safeSend ({} bytes)", frame.length, ex);
    }
    return false;
  }
}
