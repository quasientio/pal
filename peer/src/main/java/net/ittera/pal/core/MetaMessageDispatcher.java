/*
   Copyright (c) 2020 Contributors listed in the AUTHORS file

   This file is part of PAL, the friendly java runtime.

   PAL is free software: you can redistribute it and/or modify
   it under the terms of the GNU General Public License as published by
   the Free Software Foundation, either version 3 of the License, or
   (at your option) any later version.

   PAL is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
   GNU General Public License for more details.

   You should have received a copy of the GNU General Public License
   along with this program. If not, see <http://www.gnu.org/licenses/>.
*/

package net.ittera.pal.core;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.ittera.pal.core.rpc.meta.java.ClassMetadataSerializer;
import net.ittera.pal.messages.colfer.MetaMessage;
import net.ittera.pal.messages.colfer.Parameter;
import net.ittera.pal.messages.types.MetaServiceType;
import net.ittera.pal.messages.types.MetaStatusType;
import net.ittera.pal.serdes.Unwrapper;
import net.ittera.pal.serdes.colfer.MessageBuilder;
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

    // parse given params
    Parameter[] params = metaMessage.getParams();
    if (params != null) {
      for (Parameter param : params) {
        if (param.getName() == null || param.getValue() == null) {
          continue;
        }
        if (param.getName().equalsIgnoreCase("compress_encode") && param.getValue() != null) {
          // process "compress_encode"
          try {
            compressAndEncode = (Boolean) Unwrapper.unwrapObject(param.getValue());
          } catch (Exception e) {
            throw new RuntimeException("Error unwrapping parameter 'compress_encode'", e);
          }
        } else if (param.getName().equalsIgnoreCase("exclude_prefixes")
            && param.getValue() != null) {
          // process "exclude_prefixes"
          excludePrefixes = new HashSet<>();
          try {
            Collections.addAll(
                excludePrefixes, (String[]) Unwrapper.unwrapObject(param.getValue()));
          } catch (Exception e) {
            throw new RuntimeException("Error unwrapping parameter 'exclude_prefixes'", e);
          }
        } else if (param.getName().equalsIgnoreCase("include_classes")
            && param.getValue() != null) {
          // process "include_classes"
          includeClasses = new HashSet<>();
          try {
            Collections.addAll(includeClasses, (String[]) Unwrapper.unwrapObject(param.getValue()));
          } catch (Exception e) {
            throw new RuntimeException("Error unwrapping parameter 'includeClasses'", e);
          }
        } else if (param.getName().equalsIgnoreCase("merge_ancestry") && param.getValue() != null) {
          // process "merge_ancestry"
          try {
            mergeAncestry = (Boolean) Unwrapper.unwrapObject(param.getValue());
          } catch (Exception e) {
            throw new RuntimeException("Error unwrapping parameter 'merge_ancestry'", e);
          }
        } else {
          logger.warn("Ignoring parameter name={}, value={}", param.getName(), param.getValue());
        }
      }
    }

    switch (serviceType) {
      case FETCH_CLASSES_INFO:
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
      default:
        String errorMessage =
            String.format(
                "Incoming Meta message w/id=%s from peer=%s ignored - no handler for service: %s",
                metaMessage.getMessageId(), metaMessage.fromPeer, serviceType.name());
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
