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
package io.quasient.pal.core.dispatcher;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.quasient.pal.common.util.UuidUtils;
import io.quasient.pal.core.execution.java.reflect.ClassMetadataSerializer;
import io.quasient.pal.messages.colfer.MetaMessage;
import io.quasient.pal.messages.colfer.Obj;
import io.quasient.pal.messages.types.MetaServiceType;
import io.quasient.pal.messages.types.MetaStatusType;
import io.quasient.pal.serdes.Unwrapper;
import io.quasient.pal.serdes.colfer.MessageBuilder;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Dispatcher responsible for processing incoming meta messages and generating corresponding
 * responses.
 *
 * <p>This class interprets the service type and parameters from a received meta message, uses the
 * provided metadata serializer to scan and serialize the classpath based on the parameters, and
 * constructs an appropriate response using the message builder. It is managed as a singleton with
 * its dependencies injected.
 */
@Singleton
public class MetaMessageDispatcher {

  /** Unique identifier representing the local peer, used in constructing meta message responses. */
  private final UUID peerUuid;

  /** Serializer for converting scanned class metadata into a JSON representation. */
  private final ClassMetadataSerializer classMetadataSerializer;

  /** Builder responsible for constructing meta message responses. */
  private final MessageBuilder messageBuilder;

  /** Logger instance. */
  private static final Logger logger = LoggerFactory.getLogger(MetaMessageDispatcher.class);

  /**
   * Constructs a MetaMessageDispatcher with the specified peer identifier, class metadata
   * serializer, and message builder.
   *
   * @param peerUuid Unique identifier for the local peer.
   * @param classMetadataSerializer Utility to serialize class metadata into a JSON format.
   * @param messageBuilder Component that builds meta message responses.
   */
  @Inject
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification =
          "MessageBuilder is a Guice-managed singleton; storing the injected reference"
              + " is standard DI practice and does not expose internal representation.")
  public MetaMessageDispatcher(
      UUID peerUuid,
      ClassMetadataSerializer classMetadataSerializer,
      MessageBuilder messageBuilder) {
    this.peerUuid = peerUuid;
    this.classMetadataSerializer = classMetadataSerializer;
    this.messageBuilder = messageBuilder;
  }

  /**
   * Processes an incoming meta message by evaluating its service type and associated parameters,
   * executing the relevant handling logic, and constructing an appropriate response message.
   *
   * <p>For messages with a service type of FETCH_CLASSES_INFO, the method scans the classpath and
   * serializes class metadata into a JSON representation. It supports configurable parameters such
   * as whether to compress and encode the results, include specific classes, exclude classes based
   * on prefixes, or merge class ancestry. Unrecognized parameters are logged as warnings.
   *
   * @param metaMessage The incoming meta message containing the service identifier and optional
   *     parameter values.
   * @return A meta message response reflecting the result of processing, which may indicate
   *     success, error, or an unsupported service.
   * @throws RuntimeException If an error occurs while unwrapping any parameter value.
   */
  public MetaMessage incomingMetaMessage(MetaMessage metaMessage) {
    final MetaServiceType serviceType = MetaServiceType.fromId(metaMessage.getService());

    // set parameter defaults
    boolean compressAndEncode = true;
    Set<String> excludePrefixes = null;
    Set<String> includeClasses = null;
    boolean mergeAncestry = false;

    // parse given params (positional: 0=compress_encode, 1=exclude_prefixes,
    // 2=include_classes, 3=merge_ancestry)
    Obj[] params = metaMessage.getParams();
    if (params != null) {
      if (params.length > 0 && params[0] != null && !params[0].getIsNull()) {
        try {
          compressAndEncode = (Boolean) Unwrapper.unwrapObject(params[0]);
        } catch (Exception e) {
          throw new RuntimeException("Error unwrapping meta param at index 0 (compress_encode)", e);
        }
      }
      if (params.length > 1 && params[1] != null && !params[1].getIsNull()) {
        excludePrefixes = new HashSet<>();
        try {
          Collections.addAll(excludePrefixes, (String[]) Unwrapper.unwrapObject(params[1]));
        } catch (Exception e) {
          throw new RuntimeException(
              "Error unwrapping meta param at index 1 (exclude_prefixes)", e);
        }
      }
      if (params.length > 2 && params[2] != null && !params[2].getIsNull()) {
        includeClasses = new HashSet<>();
        try {
          Collections.addAll(includeClasses, (String[]) Unwrapper.unwrapObject(params[2]));
        } catch (Exception e) {
          throw new RuntimeException("Error unwrapping meta param at index 2 (include_classes)", e);
        }
      }
      if (params.length > 3 && params[3] != null && !params[3].getIsNull()) {
        try {
          mergeAncestry = (Boolean) Unwrapper.unwrapObject(params[3]);
        } catch (Exception e) {
          throw new RuntimeException("Error unwrapping meta param at index 3 (merge_ancestry)", e);
        }
      }
    }

    // Handle null serviceType (unknown service ID)
    if (serviceType == null) {
      String errorMessage =
          String.format(
              "Incoming Meta message w/id=%s from peer=%s ignored - unknown service ID: %d",
              metaMessage.getMessageId(),
              UuidUtils.toString(metaMessage.fromPeer),
              metaMessage.getService());
      logger.error(errorMessage);
      // Build response manually since we don't have a valid MetaServiceType
      MetaMessage response = new MetaMessage();
      response.setFromPeer(UuidUtils.toBytes(peerUuid));
      response.setMessageId(UUID.randomUUID().toString());
      response.setResponseToId(metaMessage.getMessageId());
      response.setService(metaMessage.getService()); // Preserve original service ID
      response.setStatus(MetaStatusType.UNSUPPORTED.getId());
      response.setBody(errorMessage);
      return response;
    }

    switch (serviceType) {
      case FETCH_CLASSES_INFO -> {
        try {
          Path scanResultPath =
              classMetadataSerializer.scannedClasspathToJson(
                  compressAndEncode, includeClasses, excludePrefixes, mergeAncestry);
          return messageBuilder.buildMetaMessageResponse(
              peerUuid,
              serviceType,
              MetaStatusType.OK,
              scanResultPath.toString(),
              metaMessage.getMessageId());
        } catch (Exception e) {
          logger.error("Error scanning classes", e);
          return messageBuilder.buildMetaMessageResponse(
              peerUuid,
              serviceType,
              MetaStatusType.ERROR,
              e.getMessage(),
              metaMessage.getMessageId());
        }
      }
      default -> {
        String errorMessage =
            String.format(
                "Incoming Meta message w/id=%s from peer=%s ignored - no handler for service: %s",
                metaMessage.getMessageId(),
                UuidUtils.toString(metaMessage.fromPeer),
                serviceType.name());
        logger.error(errorMessage);
        return messageBuilder.buildMetaMessageResponse(
            peerUuid,
            serviceType,
            MetaStatusType.UNSUPPORTED,
            errorMessage,
            metaMessage.getMessageId());
      }
    }
  }
}
