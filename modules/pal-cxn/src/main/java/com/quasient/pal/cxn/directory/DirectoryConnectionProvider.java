/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.cxn.directory;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Provider;
import java.util.Optional;

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

  // PalDirectory constructor parameters

  /**
   * The connection string used to establish a connection with the {@link PalDirectory}.
   *
   * <p>This string represents the URL of the PalDirectory service.
   */
  private final String connectionString;

  /**
   * The namespace within the {@link PalDirectory} context.
   *
   * <p>This parameter allows for setting a custom PalDirectory namespace.
   */
  private final String namespace;

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
   * @param connectionString the connection string for the {@link PalDirectory}; must not be null or
   *     empty
   */
  @Inject
  public DirectoryConnectionProvider(@Named("paldir_url") String connectionString) {
    this(connectionString, null);
  }

  /**
   * Constructs a {@code DirectoryConnectionProvider} with the specified connection string and
   * namespace.
   *
   * @param connectionString the connection string for the {@link PalDirectory}; must not be null or
   *     empty
   * @param namespace the namespace for scoping {@link PalDirectory} operations; may be {@code null}
   *     if no custom namespace is required
   */
  public DirectoryConnectionProvider(String connectionString, String namespace) {
    this.connectionString = connectionString;
    this.namespace = namespace;
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
      palDirectoryInstance = new PalDirectory(connectionString, namespace);
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
}
