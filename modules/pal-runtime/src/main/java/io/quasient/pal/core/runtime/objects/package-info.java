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
/**
 * Remote object reference management and lifecycle.
 *
 * <p>When objects are passed across peer boundaries, they are assigned an {@link
 * io.quasient.pal.common.objects.ObjectRef} and stored in an {@link ObjectLookupStore}. This
 * enables transparent remote method invocation on objects that live on remote peers.
 *
 * <ul>
 *   <li>{@link ObjectLookupStore} - Interface for object storage and lookup
 *   <li>{@link ConcurrentHashMapObjectLookupStore} - Thread-safe implementation
 *   <li>{@link ObjectLookupStoreCleaner} - Removes unreferenced objects
 * </ul>
 */
package io.quasient.pal.core.runtime.objects;
