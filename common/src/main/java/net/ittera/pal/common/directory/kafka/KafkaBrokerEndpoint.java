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

package net.ittera.pal.common.directory.kafka;

import java.util.Objects;
import javax.annotation.Nonnull;

public class KafkaBrokerEndpoint {

  @Nonnull final String protocol;
  @Nonnull private final String host;
  private final int port;

  public KafkaBrokerEndpoint(@Nonnull String protocol, @Nonnull String hostAndPort) {
    this.protocol = Objects.requireNonNull(protocol);
    Objects.requireNonNull(hostAndPort);
    this.host = hostAndPort.split(":")[0];
    this.port = Integer.parseInt(hostAndPort.split(":")[1]);
  }

  @Nonnull
  public String getProtocol() {
    return protocol;
  }

  @Nonnull
  public String getHost() {
    return host;
  }

  public int getPort() {
    return port;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    KafkaBrokerEndpoint that = (KafkaBrokerEndpoint) o;
    return port == that.port && protocol.equals(that.protocol) && host.equals(that.host);
  }

  @Override
  public int hashCode() {
    return Objects.hash(protocol, host, port);
  }

  @Override
  public String toString() {
    return String.format("%s://%s:%s", protocol, host, port);
  }

  public String toURL() {
    return String.format("%s:%s", host, port);
  }
}
