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
package io.quasient.pal.cxn;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import org.junit.Test;

public class JmxClientStaticInitTest {

  @Test
  public void staticBlockDisablesRmiCodebaseDownload() throws Exception {
    // Force class initialization (may already be loaded by other tests in same JVM)
    Class.forName(JmxClient.class.getName());
    // Verify the static block set the security property
    assertThat(System.getProperty("com.sun.jndi.rmi.object.trustURLCodebase"), is("false"));
  }
}
