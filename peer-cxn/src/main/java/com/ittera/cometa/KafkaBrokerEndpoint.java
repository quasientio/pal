package com.ittera.cometa;

public class KafkaBrokerEndpoint {

  private final String protocol;
  private final String host;
  private final int port;

  public KafkaBrokerEndpoint(String protocol, String hostAndport) {
    this.protocol = protocol;
    this.host = hostAndport.split(":")[0];
    this.port = Integer.parseInt(hostAndport.split(":")[1]);
  }

  public String getProtocol() {
    return protocol;
  }

  public String getHost() {
    return host;
  }

  public int getPort() {
    return port;
  }

  @Override
  public String toString() {
    return String.format("%s://%s:%s", protocol, host, port);
  }

  public String toURL() {
    return String.format("%s:%s", host, port);
  }
}
