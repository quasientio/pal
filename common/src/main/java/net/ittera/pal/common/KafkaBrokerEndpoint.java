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
