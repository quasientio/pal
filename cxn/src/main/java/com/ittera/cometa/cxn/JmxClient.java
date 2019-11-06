package com.ittera.cometa.cxn;

import java.io.IOException;
import java.util.Set;
import javax.management.*;
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

  public JmxClient(String host, int port) throws IOException {
    this.serverUrl = String.format("service:jmx:rmi:///jndi/rmi://%s:%s/jmxrmi", host, port);
    connect();
  }

  public Set<ObjectName> query(String query) throws MalformedObjectNameException, IOException {
    ObjectName objectName = new ObjectName(query);
    return beanServer.queryNames(objectName, null);
  }

  private void connect() throws IOException {
    conn = JMXConnectorFactory.connect(new JMXServiceURL(serverUrl));
    conn.addConnectionNotificationListener(
        (notification, handback) -> {
          if (notification instanceof JMXConnectionNotification) {
            if (notification.getType().equals(JMXConnectionNotification.CLOSED)) {
              try {
                connect();
              } catch (IOException e) {
                logger.error("Error attempting to re-connect to: " + serverUrl, e);
              }
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
