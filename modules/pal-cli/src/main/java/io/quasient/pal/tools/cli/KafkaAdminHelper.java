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

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.function.Function;
import org.apache.kafka.clients.admin.Admin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages a cache of Kafka {@link Admin} clients keyed by bootstrap server strings.
 *
 * <p>This shared utility consolidates the duplicated {@code getAdminClientForServers} logic
 * previously found in {@code List} and {@code Remove}. It provides lazy creation and caching of
 * {@link Admin} instances, ensuring that only one client is created per unique bootstrap servers
 * string.
 *
 * <p>Callers should invoke {@link #closeResources()} when the helper is no longer needed to release
 * all cached Kafka admin connections.
 */
public class KafkaAdminHelper {

  /** Logger for this class. */
  private static final Logger logger = LoggerFactory.getLogger(KafkaAdminHelper.class);

  /** Unique client ID used for all Kafka admin connections created by this helper. */
  private static final UUID KAFKA_CLIENT_ID = UUID.randomUUID();

  /** Cache of Admin clients keyed by bootstrap server string. */
  private final Map<String, Admin> adminClientsPerServer = new HashMap<>();

  /** Factory function for creating Admin instances from properties. */
  private final Function<Properties, Admin> adminFactory;

  /** Constructs a new {@code KafkaAdminHelper} that creates real Kafka Admin clients. */
  public KafkaAdminHelper() {
    this(Admin::create);
  }

  /**
   * Constructs a new {@code KafkaAdminHelper} with a custom Admin factory.
   *
   * @param adminFactory function that creates an {@link Admin} from {@link Properties}
   */
  KafkaAdminHelper(Function<Properties, Admin> adminFactory) {
    this.adminFactory = adminFactory;
  }

  /**
   * Retrieves or creates a Kafka {@link Admin} client for the specified bootstrap servers.
   *
   * <p>If a client for the given {@code bootstrapServers} string already exists in the cache, the
   * cached instance is returned. Otherwise, a new client is created with the following
   * configuration:
   *
   * <ul>
   *   <li>{@code bootstrap.servers} — set to the provided value
   *   <li>{@code client.id} — set to a UUID unique to this helper instance
   *   <li>{@code request.timeout.ms} — 5000 ms
   *   <li>{@code default.api.timeout.ms} — 10000 ms
   * </ul>
   *
   * @param bootstrapServers the Kafka bootstrap servers to connect to
   * @return the {@link Admin} client for the given bootstrap servers
   */
  public Admin getAdminClientForServers(String bootstrapServers) {
    if (!adminClientsPerServer.containsKey(bootstrapServers)) {
      Properties props = new Properties();
      props.setProperty("bootstrap.servers", bootstrapServers);
      props.setProperty("client.id", KAFKA_CLIENT_ID.toString());
      props.setProperty("request.timeout.ms", "5000");
      props.setProperty("default.api.timeout.ms", "10000");
      adminClientsPerServer.put(bootstrapServers, adminFactory.apply(props));
      logger.debug("Created Kafka Admin client for servers: {}", bootstrapServers);
    }
    return adminClientsPerServer.get(bootstrapServers);
  }

  /**
   * Maximum time to wait for each Kafka Admin client to close. This bounds the shutdown time when
   * pending operations (e.g., deleteTopics retries) would otherwise block {@link Admin#close()}
   * indefinitely.
   */
  private static final Duration CLOSE_TIMEOUT = Duration.ofSeconds(5);

  /**
   * Closes all cached Kafka {@link Admin} clients and clears the cache.
   *
   * <p>Each client is closed with a bounded timeout to prevent indefinite blocking when pending
   * operations are retrying against an unresponsive broker.
   *
   * <p>This method should be called when the helper is no longer needed. It is safe to call on an
   * empty cache.
   */
  public void closeResources() {
    adminClientsPerServer.values().forEach(admin -> admin.close(CLOSE_TIMEOUT));
    adminClientsPerServer.clear();
  }
}
