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

package net.ittera.pal.common;

import java.util.Arrays;
import net.ittera.pal.common.util.Strings;
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
