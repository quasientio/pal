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

import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Provider;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provides a null-safe, singleton {@link Provider} for {@link PalDirectory} instances.
 *
 * <p>This class ensures that a {@link PalDirectory} is available when required, returning an {@link
 * Optional} containing the instance if properly initialized or empty otherwise. It is intended to
 * be injected wherever a {@link PalDirectory} is needed, allowing for flexible configuration and
 * null-safety.
 *
 * <p>When the system operates without a PalDirectory, this Provider is initialized with the
 * constant {@link PalDirectory#NO_URL}, ensuring that {@link #get()} returns an empty {@link
 * Optional}.
 */
public class DirectoryConnectionProvider implements Provider<Optional<PalDirectory>> {

  /** Class logger. */
  private static final Logger logger = LoggerFactory.getLogger(DirectoryConnectionProvider.class);

  // PalDirectory constructor parameters

  /**
   * The connection string used to establish a connection with the {@link PalDirectory}.
   *
   * <p>This string represents the URL of the PalDirectory service.
   */
  private final String connectionString;

  /**
   * Whether we should establish a blocking-connection with the {@link PalDirectory}. Defaults to
   * false.
   */
  private final boolean blockingConnect;

  /**
   * The namespace within the {@link PalDirectory} context.
   *
   * <p>This parameter allows for setting a custom PalDirectory namespace.
   */
  private final String namespace;

  /** Etcd connect timeout used for preflight and status checks. */
  private final Duration connectTimeout;

  /**
   * Cached instance of the {@link PalDirectory}.
   *
   * <p>Holds the initialized PalDirectory to ensure that subsequent calls to {@link #get()} return
   * the same instance, maintaining singleton behavior within this Provider.
   */
  private PalDirectory palDirectoryInstance;

  /**
   * Constructs a {@code DirectoryConnectionProvider} with the specified connection string.
   *
   * <p>This constructor is used for dependency injection, with the connection string typically
   * provided via the {@code paldir_url} configuration parameter.
   *
   * <p>Uses blocking mode by default to fail fast if etcd is unreachable, providing better error
   * messages and preventing indefinite hangs.
   *
   * @param connectionString the connection string for the {@link PalDirectory}; must not be null or
   *     empty
   */
  @Inject
  public DirectoryConnectionProvider(
      @Named("paldir_url") String connectionString,
      @Named("etcd.connect.timeout.ms") String connectTimeoutMs) {
    this(
        connectionString,
        null,
        true,
        parseTimeout(connectTimeoutMs, PalDirectory::getDefaultConnectionTimeout));
  }

  /** Backward-compatible constructor used in tests and non-Guice contexts. */
  public DirectoryConnectionProvider(String connectionString) {
    this(connectionString, null, true, PalDirectory.getDefaultConnectionTimeout());
  }

  /**
   * Constructs a {@code DirectoryConnectionProvider} with the specified connection string and
   * namespace, and blockingConnect flag.
   *
   * @param connectionString the connection string for the {@link PalDirectory}; must not be null or
   *     empty
   * @param namespace the namespace for scoping {@link PalDirectory} operations; may be {@code null}
   *     if no custom namespace is required
   * @param blockingConnect whether to establish the connection to Pal Directory in blocking mode
   */
  public DirectoryConnectionProvider(
      String connectionString, String namespace, boolean blockingConnect) {
    this(connectionString, namespace, blockingConnect, PalDirectory.getDefaultConnectionTimeout());
  }

  /** Full constructor allowing to specify connect timeout. */
  public DirectoryConnectionProvider(
      String connectionString, String namespace, boolean blockingConnect, Duration connectTimeout) {
    this.connectionString = connectionString;
    this.namespace = namespace;
    this.blockingConnect = blockingConnect;
    this.connectTimeout =
        connectTimeout == null ? PalDirectory.getDefaultConnectionTimeout() : connectTimeout;
  }

  /**
   * {@inheritDoc}
   *
   * <p>Returns an {@link Optional} containing the {@link PalDirectory} if the connection string is
   * valid; otherwise, returns an empty {@link Optional}. Initializes the {@link PalDirectory}
   * instance on the first invocation if not already initialized.
   *
   * @return an {@link Optional} describing the {@link PalDirectory}, or empty if no valid
   *     connection string is provided
   */
  @Override
  public Optional<PalDirectory> get() {
    if (connectionString.equals(PalDirectory.NO_URL)) {
      return Optional.empty();
    }

    if (palDirectoryInstance == null) {
      palDirectoryInstance =
          new PalDirectory(connectionString, namespace, blockingConnect, connectTimeout);
    }

    return Optional.of(palDirectoryInstance);
  }

  /**
   * Retrieves the connection string used by this Provider.
   *
   * @return the connection string for the {@link PalDirectory}; never {@code null}
   */
  public String getConnectionString() {
    return connectionString;
  }

  /**
   * Parses a timeout value expressed in milliseconds from a string.
   *
   * <p>If the provided string is null, blank, non-numeric, or not a positive value, the {@code
   * defaultSupplier} is used to provide a fallback {@link Duration}.
   *
   * @param millisStr the timeout in milliseconds as a string; may be null or blank
   * @param defaultSupplier supplier of the default {@link Duration} if parsing fails or value is
   *     not positive
   * @return the parsed positive {@link Duration} in milliseconds, or the supplied default duration
   */
  private static Duration parseTimeout(String millisStr, DurationSupplier defaultSupplier) {
    try {
      if (millisStr != null && !millisStr.isBlank()) {
        long ms = Long.parseLong(millisStr.trim());
        if (ms > 0) return Duration.ofMillis(ms);
      }
    } catch (Exception ex) {
      if (logger.isDebugEnabled()) {
        logger.debug("Error parsing connect timeout '{}', using default", millisStr, ex);
      }
    }
    return defaultSupplier.get();
  }

  /** Supplier interface for providing a default {@link Duration} value. */
  @FunctionalInterface
  private interface DurationSupplier {
    /**
     * Returns a default {@link Duration} value.
     *
     * @return the default duration
     */
    Duration get();
  }
}
