package com.ittera.cometa.messages.protobuf;

import org.apache.kafka.common.serialization.StringDeserializer;

/**
 * The purpose of this subclass is to not have to change package name in kafka properties
 * after mvn-shading (see issue #168 more details). Having our own deserializer class means we can
 * leave the full package and class unchanged since we only relocate dependencies, not our own classes
 */
public final class KafkaKeyDeserializer extends StringDeserializer {
}
