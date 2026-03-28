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
package io.quasient.pal.cxn.directory;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

import io.etcd.jetcd.ByteSequence;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

public class PalDirectoryPathsTest {

  private PalDirectory dir;

  @Before
  public void setUp() {
    try {
      dir = new PalDirectory("http://127.0.0.1:2379", "ns-test", false);
    } catch (Throwable t) {
      // If the environment forbids class init, skip these tests
      Assume.assumeNoException("Skipping PalDirectory path tests due to env", t);
    }
  }

  @After
  public void tearDown() {
    if (dir != null) dir.close();
  }

  private static ByteSequence invokeKey(PalDirectory d, String method, Class<?>... sig)
      throws Exception {
    Method m = PalDirectory.class.getDeclaredMethod(method, sig);
    m.setAccessible(true);
    Object out = m.invoke(d, new Object[sig.length]);
    return (ByteSequence) out;
  }

  @Test
  public void peerAndLogPaths_includeNamespace() throws Exception {
    UUID peer = UUID.randomUUID();

    Method mp = PalDirectory.class.getDeclaredMethod("getPeerPath", UUID.class);
    mp.setAccessible(true);
    String peerPath = (String) mp.invoke(dir, peer);

    Method mpIn = PalDirectory.class.getDeclaredMethod("getPeerSourceLogPath", UUID.class);
    mpIn.setAccessible(true);
    String inPath = (String) mpIn.invoke(dir, peer);

    Method mpWal = PalDirectory.class.getDeclaredMethod("getPeerWALPath", UUID.class);
    mpWal.setAccessible(true);
    String walPath = (String) mpWal.invoke(dir, peer);

    Method mLogs = PalDirectory.class.getDeclaredMethod("getLogsPath");
    mLogs.setAccessible(true);
    String logs = (String) mLogs.invoke(dir);

    Method mPeers = PalDirectory.class.getDeclaredMethod("getPeersPath");
    mPeers.setAccessible(true);
    String peers = (String) mPeers.invoke(dir);

    assertThat(peerPath, containsString("ns-test/peers/" + peer));
    assertThat(inPath, containsString("ns-test/peers/" + peer + "/logs/source"));
    assertThat(walPath, containsString("ns-test/peers/" + peer + "/logs/wal"));
    assertThat(logs, containsString("ns-test/logs"));
    assertThat(peers, containsString("ns-test/peers"));

    // Keys mirror the same strings
    ByteSequence peersKey = invokeKey(dir, "getPeersPathKey");
    String peersKeyStr = new String(peersKey.getBytes(), StandardCharsets.UTF_8);
    assertThat(peersKeyStr, is(peers));
  }

  @Test
  public void peerByNamePath_includesNamespace() throws Exception {
    Method m = PalDirectory.class.getDeclaredMethod("getPeerByNamePath", String.class);
    m.setAccessible(true);
    String path = (String) m.invoke(dir, "my-peer");

    assertThat(path, is("/ns-test/peers/by-name/my-peer"));
  }
}
