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
