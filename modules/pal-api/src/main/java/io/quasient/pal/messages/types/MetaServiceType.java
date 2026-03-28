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
package io.quasient.pal.messages.types;

import java.util.Locale;

/**
 * Represents the types of meta services available within the PAL runtime.
 *
 * <p>Each enum constant corresponds to a specific meta service identified by a unique byte ID.
 */
public enum MetaServiceType {

  /** Indicates a request to fetch class information. */
  FETCH_CLASSES_INFO((byte) 1);

  /** Lookup table mapping byte IDs to their corresponding MetaServiceType. */
  private static final MetaServiceType[] LOOKUP = new MetaServiceType[256];

  static {
    for (MetaServiceType type : values()) {
      LOOKUP[type.id & 0xFF] = type;
    }
  }

  /** The unique identifier for the meta service type. */
  private final byte id;

  /**
   * Constructs a MetaServiceType with the specified byte identifier.
   *
   * @param id the byte identifier associated with this meta service type
   */
  MetaServiceType(byte id) {
    this.id = id;
  }

  /**
   * Retrieves the byte identifier of this meta service type.
   *
   * @return the byte ID representing this meta service type
   */
  public byte getId() {
    return id;
  }

  /**
   * Obtains the MetaServiceType corresponding to the given byte ID.
   *
   * @param id the byte identifier of the desired MetaServiceType
   * @return the MetaServiceType associated with the specified ID, or {@code null} if no matching
   *     type exists
   */
  public static MetaServiceType fromId(byte id) {
    return LOOKUP[id & 0xFF];
  }

  /**
   * Provides the JSON-compatible name of this meta service type in lowercase.
   *
   * @return the JSON name of this MetaServiceType
   */
  public String getJsonName() {
    return name().toLowerCase(Locale.ROOT);
  }

  /**
   * Parses the provided JSON name to retrieve the corresponding MetaServiceType.
   *
   * @param jsonName the JSON name representing a MetaServiceType
   * @return the MetaServiceType matching the given JSON name, or {@code null} if no match is found
   */
  public static MetaServiceType fromJsonName(String jsonName) {
    for (MetaServiceType type : values()) {
      if (type.getJsonName().equalsIgnoreCase(jsonName)) {
        return type;
      }
    }
    return null;
  }
}
