package net.ittera.pal.cxn;

import static org.junit.Assert.assertEquals;

import net.ittera.pal.common.KafkaBrokerInfo;
import org.junit.Test;

public class KafkaBrokerInfoTest {

  @Test
  public void parseFromJSON_properString_newBrokerInfo() throws Exception {

    String brokerInfoAsJSON =
        "{"
            + "\"listener_security_protocol_map\":"
            + "	{"
            + "\"INSIDE\": \"PLAINTEXT\","
            + "\"OUTSIDE\":\"SSL\""
            + "},"
            + "\"endpoints\":"
            + "["
            + "\"INSIDE://1dc826cd8d9b:9092\","
            + "\"OUTSIDE://172.20.0.1:9094\""
            + "],"
            + "\"jmx_port\":10121,"
            + "\"host\":\"1dc826cd8d9b\","
            + "\"timestamp\":\"1526204010258\","
            + "\"port\":9092,"
            + "\"version\":4"
            + "}"
            + "}";

    KafkaBrokerInfo brokerInfo = KafkaBrokerInfo.parseFromJSON(brokerInfoAsJSON);

    assertEquals(10121, brokerInfo.getJmxPort());
    assertEquals(9092, brokerInfo.getPort());
    assertEquals("1dc826cd8d9b", brokerInfo.getHost());
    assertEquals(4, brokerInfo.getVersion());
    assertEquals("1526204010258", brokerInfo.getTimestamp());

    assertEquals(2, brokerInfo.getEndpoints().length);
    assertEquals("1dc826cd8d9b", brokerInfo.getEndpoints()[0].getHost());
    assertEquals(9092, brokerInfo.getEndpoints()[0].getPort());
    assertEquals("PLAINTEXT", brokerInfo.getEndpoints()[0].getProtocol());

    assertEquals("172.20.0.1", brokerInfo.getEndpoints()[1].getHost());
    assertEquals(9094, brokerInfo.getEndpoints()[1].getPort());
    assertEquals("SSL", brokerInfo.getEndpoints()[1].getProtocol());
  }
}
