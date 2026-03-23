/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.tools.cli.init;

/**
 * Enumerates deployment modes for a PAL project.
 *
 * <p>Each mode determines which infrastructure components (Chronicle Queue, etcd, Kafka) are
 * configured during project initialization.
 *
 * @since 1.0.0
 */
public enum DeploymentMode {

  /** Local-only deployment using Chronicle Queue. No external infrastructure required. */
  LOCAL {
    @Override
    public boolean isLocal() {
      return true;
    }

    @Override
    public boolean isDistributed() {
      return false;
    }
  },

  /** Distributed deployment using etcd and Kafka. */
  DISTRIBUTED {
    @Override
    public boolean isLocal() {
      return false;
    }

    @Override
    public boolean isDistributed() {
      return true;
    }
  },

  /** Both local and distributed configurations are generated. */
  BOTH {
    @Override
    public boolean isLocal() {
      return true;
    }

    @Override
    public boolean isDistributed() {
      return true;
    }
  };

  /**
   * Returns {@code true} if this mode includes local (Chronicle Queue) configuration.
   *
   * @return whether local configuration is included
   */
  public abstract boolean isLocal();

  /**
   * Returns {@code true} if this mode includes distributed (etcd + Kafka) configuration.
   *
   * @return whether distributed configuration is included
   */
  public abstract boolean isDistributed();
}
