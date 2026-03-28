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
package io.quasient.pal.tools.cli;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Properties;
import java.util.function.Function;
import org.apache.kafka.clients.admin.Admin;
import org.junit.Test;

/**
 * Unit test specifications for {@code KafkaAdminHelper}.
 *
 * <p>KafkaAdminHelper is a shared utility that manages a cache of Kafka {@code Admin} clients keyed
 * by bootstrap server strings. It consolidates the duplicated {@code getAdminClientForServers()}
 * logic from {@code List} and {@code Remove}.
 */
public class KafkaAdminHelperTest {

  /**
   * Creates a KafkaAdminHelper that returns mock Admin instances instead of real Kafka connections.
   *
   * @return a KafkaAdminHelper with a mock factory
   */
  private static KafkaAdminHelper createHelperWithMockFactory() {
    return new KafkaAdminHelper(props -> mock(Admin.class));
  }

  // ==================== Client Creation Tests ====================

  /**
   * Tests that a new Admin client is created on first call.
   *
   * <p>Verifies that when the cache is empty, {@code getAdminClientForServers} creates and returns
   * a new Admin client.
   */
  @Test
  public void getAdminClient_createsNewClient_whenCacheEmpty() {
    // Given: empty cache
    KafkaAdminHelper helper = createHelperWithMockFactory();

    // When
    Admin admin = helper.getAdminClientForServers("localhost:29092");

    // Then: creates and returns new Admin client
    assertThat(admin, is(notNullValue()));
  }

  /**
   * Tests that a cached Admin client is returned on subsequent calls.
   *
   * <p>Verifies that when {@code getAdminClientForServers} has already been called for a given
   * bootstrap servers string, the same Admin instance is returned on subsequent calls.
   */
  @Test
  public void getAdminClient_returnsCachedClient_onSecondCall() {
    // Given: already called once for "localhost:29092"
    KafkaAdminHelper helper = createHelperWithMockFactory();
    Admin first = helper.getAdminClientForServers("localhost:29092");

    // When
    Admin second = helper.getAdminClientForServers("localhost:29092");

    // Then: returns same Admin instance
    assertThat(second, is(sameInstance(first)));
  }

  /**
   * Tests that different bootstrap server strings produce separate Admin clients.
   *
   * <p>Verifies that when clients for different server strings are requested, each gets its own
   * Admin instance and the cache contains two entries.
   */
  @Test
  public void getAdminClient_createsSeparateClients_forDifferentServers() {
    // Given: client for "server-a:9092" exists
    KafkaAdminHelper helper = createHelperWithMockFactory();
    Admin adminA = helper.getAdminClientForServers("server-a:9092");

    // When
    Admin adminB = helper.getAdminClientForServers("server-b:9092");

    // Then: creates new client; they are different instances
    assertThat(adminB, is(notNullValue()));
    assertThat(adminB, is(not(sameInstance(adminA))));
  }

  // ==================== Resource Cleanup Tests ====================

  /**
   * Tests that all cached clients are closed on cleanup.
   *
   * <p>Verifies that when {@code closeResources} is called, all cached Admin clients are closed.
   */
  @Test
  public void closeResources_closesAllCachedClients() {
    // Given: 2 cached Admin clients
    Admin mockAdminA = mock(Admin.class);
    Admin mockAdminB = mock(Admin.class);
    @SuppressWarnings("unchecked")
    Function<Properties, Admin> factory = mock(Function.class);
    when(factory.apply(any())).thenReturn(mockAdminA).thenReturn(mockAdminB);

    KafkaAdminHelper helper = new KafkaAdminHelper(factory);
    helper.getAdminClientForServers("server-a:9092");
    helper.getAdminClientForServers("server-b:9092");

    // When
    helper.closeResources();

    // Then: both clients are closed with a bounded timeout
    verify(mockAdminA).close(any(Duration.class));
    verify(mockAdminB).close(any(Duration.class));
  }

  /**
   * Tests that cleanup on an empty cache is safe.
   *
   * <p>Verifies that calling {@code closeResources} when no clients have been created does not
   * throw any exceptions.
   */
  @Test
  public void closeResources_onEmptyCache_doesNothing() {
    // Given: empty cache
    KafkaAdminHelper helper = createHelperWithMockFactory();

    // When: closeResources() — should not throw
    helper.closeResources();
  }
}
