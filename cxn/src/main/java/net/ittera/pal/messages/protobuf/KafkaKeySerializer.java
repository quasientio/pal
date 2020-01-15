package net.ittera.pal.messages.protobuf;

import org.apache.kafka.common.serialization.StringSerializer;

/**
 * The purpose of this subclass is to not have to change package name in kafka properties after
 * mvn-shading (see issue #168 more details). Having our own serializer class means we can leave the
 * full package and class unchanged since we only relocate dependencies, not our own classes
 */
public final class KafkaKeySerializer extends StringSerializer {}
