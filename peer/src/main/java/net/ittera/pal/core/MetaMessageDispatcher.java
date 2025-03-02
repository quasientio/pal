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

@Singleton
public class MetaMessageDispatcher {
  private final UUID peerUuid;
  private final ClassMetadataSerializer classMetadataSerializer;
  private final MessageBuilder messageBuilder;

  @Inject
  public MetaMessageDispatcher(
      UUID peerUuid,
      ClassMetadataSerializer classMetadataSerializer,
      MessageBuilder messageBuilder) {
    this.peerUuid = peerUuid;
    this.classMetadataSerializer = classMetadataSerializer;
    this.messageBuilder = messageBuilder;
  }

  private static final Logger logger = LoggerFactory.getLogger(MetaMessageDispatcher.class);

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
          // process "process_encode"
          try {
            compressAndEncode =
                Boolean.parseBoolean((String) Unwrapper.unwrapObject(param.getValue()));
          } catch (Exception e) {
            throw new RuntimeException("Error unwrapping parameter to 'compress_encode'", e);
          }
        } else if (param.getName().equalsIgnoreCase("exclude_prefixes")
            && param.getValue() != null) {
          // process "exclude_prefixes"
          excludePrefixes = new HashSet<>();
          try {
            Collections.addAll(
                excludePrefixes, (String[]) Unwrapper.unwrapObject(param.getValue()));
          } catch (Exception e) {
            throw new RuntimeException("Error unwrapping parameter to 'exclude_prefixes'", e);
          }
        } else if (param.getName().equalsIgnoreCase("include_classes")
            && param.getValue() != null) {
          // process "include_classes"
          includeClasses = new HashSet<>();
          try {
            Collections.addAll(includeClasses, (String[]) Unwrapper.unwrapObject(param.getValue()));
          } catch (Exception e) {
            throw new RuntimeException("Error unwrapping parameter to 'includeClasses'", e);
          }
        } else if (param.getName().equalsIgnoreCase("merge_ancestry") && param.getValue() != null) {
          // process "merge_ancestry"
          try {
            mergeAncestry = Boolean.parseBoolean((String) Unwrapper.unwrapObject(param.getValue()));
          } catch (Exception e) {
            throw new RuntimeException("Error unwrapping parameter to 'merge_ancestry'", e);
          }
        } else {
          logger.warn("Ignoring parameter name={}, value={}", param.getName(), param.getValue());
        }
      }
    }

    switch (serviceType) {
      case FETCH_CLASSES_INFO:
        try {
          String scanResults =
              classMetadataSerializer.scannedClasspathToJson(
                  compressAndEncode, includeClasses, excludePrefixes, mergeAncestry);
          return messageBuilder.buildMetaMessageResponse(
              peerUuid, MetaStatusType.OK, scanResults, metaMessage.getMessageId());
        } catch (Exception e) {
          logger.error("Error scanning classes", e);
          return messageBuilder.buildMetaMessageResponse(
              peerUuid, MetaStatusType.ERROR, e.getMessage(), metaMessage.getMessageId());
        }
      default:
        String errorMessage =
            String.format(
                "Incoming Meta message w/id=%s from peer=%s ignored - no handler for service: %s",
                metaMessage.getMessageId(), metaMessage.fromPeer, serviceType.name());
        logger.error(errorMessage);
        return messageBuilder.buildMetaMessageResponse(
            peerUuid, MetaStatusType.UNSUPPORTED, errorMessage, metaMessage.getMessageId());
    }
  }
}
