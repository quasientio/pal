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

package com.quasient.pal.cxn;

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

/**
 * Client for connecting to a JMX server, allowing querying of MBeans and retrieval of attribute
 * values. Manages the JMX connection and handles automatic reconnection upon connection closure.
 */
public class JmxClient {

  /** Logger instance for the JmxClient class. */
  private static final Logger logger = LoggerFactory.getLogger(JmxClient.class);

  /** Connection to the MBean server used for querying MBeans. */
  private MBeanServerConnection beanServer;

  /** JMXConnector used to establish and manage the connection to the JMX server. */
  private JMXConnector conn;

  /** URL of the JMX server to connect to, formatted as a JMX service URL. */
  private final String serverUrl;

  static {
    // Disable RMI codebase download
    System.setProperty("com.sun.jndi.rmi.object.trustURLCodebase", "false");
  }

  /**
   * Constructs a JmxClient for the specified host and port and establishes a connection to the JMX
   * server.
   *
   * @param host the hostname of the JMX server.
   * @param port the port number of the JMX server.
   * @throws IOException if an I/O error occurs during the connection.
   */
  public JmxClient(String host, int port) throws IOException {
    logger.info("Creating JMX client for {}:{}", host, port);
    this.serverUrl = String.format("service:jmx:rmi:///jndi/rmi://%s:%s/jmxrmi", host, port);
    connect();
  }

  /**
   * Queries the MBean server for MBeans matching the specified query.
   *
   * @param query the JMX query string to filter MBeans.
   * @return a set of ObjectName instances representing the MBeans that match the query.
   * @throws MalformedObjectNameException if the query string is not a valid ObjectName.
   * @throws IOException if an I/O error occurs during the query.
   */
  public Set<ObjectName> query(String query) throws MalformedObjectNameException, IOException {
    ObjectName objectName = new ObjectName(query);
    return beanServer.queryNames(objectName, null);
  }

  /**
   * Establishes a connection to the JMX server and sets up a listener for connection notifications.
   *
   * @throws IOException if an I/O error occurs while connecting to the JMX server.
   */
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

  /**
   * Retrieves the 'Value' attribute of the specified MBean.
   *
   * @param objectName the ObjectName of the MBean.
   * @return the value of the 'Value' attribute.
   * @throws Exception if an error occurs while accessing the attribute.
   */
  public Object getValue(ObjectName objectName) throws Exception {
    return beanServer.getAttribute(objectName, "Value");
  }

  /**
   * Closes the connection to the JMX server.
   *
   * @throws IOException if an I/O error occurs while closing the connection.
   */
  public void close() throws IOException {
    conn.close();
  }
}
