package com.ittera.cometa;

import com.ittera.cometa.common.util.Strings;
import java.util.Arrays;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * This class and KafkaBrokerEndpoint are modelled after Kafka's data nodes in zookeeper. More
 * details: https://cwiki.apache.org/confluence/display/KAFKA/Kafka+data+structures+in+Zookeeper
 *
 * <p>About multiple listener configurations:
 * https://cwiki.apache.org/confluence/display/KAFKA/Multiple+Listeners+for+Kafka+Brokers
 */
public class KafkaBrokerInfo {

  private final int version;
  private final String host;
  private final int port;
  private final int jmxPort;
  private final KafkaBrokerEndpoint[] endpoints;
  private final String timestamp;

  public KafkaBrokerInfo(
      int version,
      String host,
      int port,
      int jmxPort,
      KafkaBrokerEndpoint[] endpoints,
      String timestamp) {
    this.version = version;
    this.host = host;
    this.port = port;
    this.jmxPort = jmxPort;
    this.endpoints = Arrays.copyOf(endpoints, endpoints.length);
    this.timestamp = timestamp;
  }

  public int getVersion() {
    return version;
  }

  public String getHost() {
    return host;
  }

  public int getPort() {
    return port;
  }

  public int getJmxPort() {
    return jmxPort;
  }

  public KafkaBrokerEndpoint[] getEndpoints() {
    return Arrays.copyOf(endpoints, endpoints.length);
  }

  public String getTimestamp() {
    return timestamp;
  }

  public static KafkaBrokerInfo parseFromJSON(String jsonInfo) {

    JSONObject json = new JSONObject(jsonInfo);
    JSONArray endpointsArray = json.getJSONArray("endpoints");
    JSONObject protocolMap = json.getJSONObject("listener_security_protocol_map");
    KafkaBrokerEndpoint[] endpoints = new KafkaBrokerEndpoint[endpointsArray.length()];

    for (int i = 0; i < endpoints.length; i++) {
      String protocolKey = Strings.stringBefore(endpointsArray.getString(i), "://");
      String hostAndPort = Strings.stringAfter(endpointsArray.getString(i), "://");
      endpoints[i] = new KafkaBrokerEndpoint(protocolMap.getString(protocolKey), hostAndPort);
    }

    return new KafkaBrokerInfo(
        json.getInt("version"),
        json.getString("host"),
        json.getInt("port"),
        json.getInt("jmx_port"),
        endpoints,
        json.getString("timestamp"));
  }
}
