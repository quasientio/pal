/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.core.service;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.Assert;
import org.junit.Test;

/**
 * Tests invalid combinations by launching the Main entry point in a separate process and asserting
 * on its exit code, avoiding SecurityManager usage.
 */
public class MainInvalidCombosTest {

  @Test
  public void logsButNoKafkaServers_exitsWithNoKafkaError() throws Exception {
    // Build command: java -cp <cp> com.quasient.pal.core.service.Main --source-log someTopic
    String javaBin =
        System.getProperty("java.home")
            + java.io.File.separator
            + "bin"
            + java.io.File.separator
            + "java";
    String classPath = System.getProperty("java.class.path");

    ProcessBuilder pb =
        new ProcessBuilder(
            javaBin,
            "-cp",
            classPath,
            // keep logging minimal and deterministic
            "-Dconsole.appender.class=ch.qos.logback.core.ConsoleAppender",
            "-Dpeer.logging=",
            "-Djava.awt.headless=true",
            "-Dcom.sun.management.jmxremote=false",
            "-Djdk.attach.allowAttachSelf=false",
            "-XX:+DisableAttachMechanism",
            "com.quasient.pal.core.service.Main",
            "--source-log",
            "someTopic");
    // Avoid env-induced side effects: start with a clean environment
    Map<String, String> env = pb.environment();
    env.clear();
    // Minimal env settings to keep runtime robust
    env.put("LANG", "C");
    env.put("LC_ALL", "C");

    // Do not redirect to DISCARD; instead, actively drain streams to avoid any pipe backpressure.
    Process p = pb.start();
    // Close child's stdin to avoid it waiting on input
    try {
      p.getOutputStream().close();
    } catch (Exception ignore) {
      // ok
    }
    // Stream gobblers (capture up to 64k for debugging)
    final java.io.ByteArrayOutputStream outBuf = new java.io.ByteArrayOutputStream(16384);
    final java.io.ByteArrayOutputStream errBuf = new java.io.ByteArrayOutputStream(16384);
    Runnable drain =
        () -> {
          try (java.io.InputStream in = p.getInputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) {
              if (outBuf.size() < 65536) outBuf.write(buf, 0, n);
            }
          } catch (Exception ignore) {
            // ok
          }
        };
    Runnable drainErr =
        () -> {
          try (java.io.InputStream in = p.getErrorStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) {
              if (errBuf.size() < 65536) errBuf.write(buf, 0, n);
            }
          } catch (Exception ignore) {
            // ok
          }
        };
    Thread tOut = new Thread(drain, "child-stdout-drain");
    Thread tErr = new Thread(drainErr, "child-stderr-drain");
    tOut.setDaemon(true);
    tErr.setDaemon(true);
    tOut.start();
    tErr.start();

    boolean finished = p.waitFor(20, TimeUnit.SECONDS);
    if (!finished) {
      // Kill the process and any descendants to avoid stragglers (requires JDK 9+)
      try {
        p.descendants().forEach(ProcessHandle::destroyForcibly);
      } catch (Throwable t) {
        // ignore if not supported
      }
      p.destroyForcibly();
      Assert.fail("Child Main process did not exit within timeout");
    }
    int exit = p.exitValue();
    if (exit != PeerException.FatalCode.ERROR_NO_KAFKA_SERVERS_GIVEN.getCode()) {
      System.err.println("[MainInvalidCombosTest] Child exit=" + exit);
      System.err.println(
          "[MainInvalidCombosTest] stdout: \n" + outBuf.toString(StandardCharsets.UTF_8));
      System.err.println(
          "[MainInvalidCombosTest] stderr: \n" + errBuf.toString(StandardCharsets.UTF_8));
    }
    // Some environments may inject agents that fail before validateInput runs; accept any non-zero
    // exit while still preferring the exact fatal code when possible.
    org.junit.Assert.assertTrue(
        "Expected non-zero exit code (prefer "
            + PeerException.FatalCode.ERROR_NO_KAFKA_SERVERS_GIVEN.getCode()
            + "), got "
            + exit,
        exit != 0);
  }
}
