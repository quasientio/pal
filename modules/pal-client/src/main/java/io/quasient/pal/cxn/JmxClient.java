/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.quasient.pal.cxn;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
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
@SuppressFBWarnings(
    value = "CT_CONSTRUCTOR_THROW",
    justification = "JMX connection failure should propagate - no sensitive state to protect")
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
