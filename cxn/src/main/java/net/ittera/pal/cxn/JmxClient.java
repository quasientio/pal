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

package net.ittera.pal.cxn;

import java.io.IOException;
import java.util.Set;
import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.remote.JMXConnectionNotification;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JmxClient {
  private static final Logger logger = LoggerFactory.getLogger(JmxClient.class);

  private MBeanServerConnection beanServer;
  private JMXConnector conn;
  private final String serverUrl;

  static {
    // Disable RMI codebase download
    System.setProperty("com.sun.jndi.rmi.object.trustURLCodebase", "false");
  }

  public JmxClient(String host, int port) throws IOException {
    logger.info("Creating JMX client for {}:{}", host, port);
    this.serverUrl = String.format("service:jmx:rmi:///jndi/rmi://%s:%s/jmxrmi", host, port);
    connect();
  }

  public Set<ObjectName> query(String query) throws MalformedObjectNameException, IOException {
    ObjectName objectName = new ObjectName(query);
    return beanServer.queryNames(objectName, null);
  }

  @SuppressWarnings("BanJNDI")
  private void connect() throws IOException {
    logger.debug("Connecting to: {}", serverUrl);
    conn = JMXConnectorFactory.connect(new JMXServiceURL(serverUrl));
    conn.addConnectionNotificationListener(
        (notification, handback) -> {
          if (notification instanceof JMXConnectionNotification
              && notification.getType().equals(JMXConnectionNotification.CLOSED)) {
            try {
              connect();
            } catch (IOException e) {
              logger.error("Error attempting to re-connect to: {}", serverUrl, e);
            }
          }
        },
        null,
        null);
    beanServer = conn.getMBeanServerConnection();
  }

  public Object getValue(ObjectName objectName) throws Exception {
    return beanServer.getAttribute(objectName, "Value");
  }

  public void close() throws IOException {
    conn.close();
  }
}
