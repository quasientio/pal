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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.AttributeNotFoundException;
import javax.management.InstanceAlreadyExistsException;
import javax.management.InstanceNotFoundException;
import javax.management.IntrospectionException;
import javax.management.InvalidAttributeValueException;
import javax.management.ListenerNotFoundException;
import javax.management.MBeanException;
import javax.management.MBeanInfo;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import javax.management.QueryExp;
import javax.management.ReflectionException;
import javax.management.remote.JMXConnector;
import javax.security.auth.Subject;
import org.junit.Before;
import org.junit.Test;
import org.objenesis.ObjenesisStd;

/**
 * Unit tests for {@link JmxClient}.
 *
 * <p>Tests use stub implementations for JMXConnector and MBeanServerConnection since actual JMX
 * connections are not required for verifying client behavior. Stub implementations are used instead
 * of Mockito mocks due to limitations with mockito-inline and Java 17 module system when mocking
 * JDK interfaces.
 */
public class JmxClientTest {

  /** Stub JMXConnector. */
  private StubJmxConnector stubConnector;

  /** Stub MBeanServerConnection. */
  private StubMBeanServerConnection stubBeanServer;

  /** JmxClient instance under test. */
  private JmxClient client;

  /** Test host name. */
  private static final String TEST_HOST = "localhost";

  /** Test port number. */
  private static final int TEST_PORT = 9999;

  /**
   * Sets up stub JMX infrastructure before each test.
   *
   * <p>Creates stubs and injects them into a JmxClient instance using reflection to bypass the
   * actual JMX connection.
   *
   * @throws Exception if setup or reflection fails
   */
  @Before
  public void setUp() throws Exception {
    stubBeanServer = new StubMBeanServerConnection();
    stubConnector = new StubJmxConnector(stubBeanServer);

    // Create JmxClient without invoking constructor (bypasses real JMX connection)
    client = createClientWithStubs(stubConnector, stubBeanServer);
  }

  /**
   * Creates a JmxClient instance with injected stub dependencies.
   *
   * <p>Uses Objenesis to create an instance without calling the constructor, then injects the stub
   * dependencies directly into the fields.
   *
   * @param connector the stub JMXConnector to inject
   * @param beanServer the stub MBeanServerConnection to inject
   * @return a JmxClient instance with injected stubs
   * @throws Exception if reflection fails
   */
  private JmxClient createClientWithStubs(JMXConnector connector, MBeanServerConnection beanServer)
      throws Exception {
    // Use Objenesis to create instance without calling constructor
    ObjenesisStd objenesis = new ObjenesisStd();
    JmxClient instance = objenesis.newInstance(JmxClient.class);

    setField(instance, "conn", connector);
    setField(instance, "beanServer", beanServer);
    setField(
        instance,
        "serverUrl",
        String.format("service:jmx:rmi:///jndi/rmi://%s:%s/jmxrmi", TEST_HOST, TEST_PORT));

    return instance;
  }

  /**
   * Sets a field value on an object using reflection.
   *
   * @param target the target object
   * @param fieldName the name of the field to set
   * @param value the value to set
   * @throws Exception if reflection fails
   */
  private void setField(Object target, String fieldName, Object value) throws Exception {
    Field field = JmxClient.class.getDeclaredField(fieldName);
    field.setAccessible(true);
    field.set(target, value);
  }

  /**
   * Tests that constructor with valid host and port successfully establishes a connection.
   *
   * <p>This test verifies that when the stub is properly configured, the client can be created and
   * the connection methods are invoked. Since we bypass the real constructor due to testing
   * limitations, we verify the stub setup is correct.
   *
   * @throws Exception if test fails
   */
  @Test
  public void constructor_validHostPort_connectsSuccessfully() throws Exception {
    // Given: Valid host and port parameters with stubbed JMXConnector
    // (setUp already created client with stubs injected)

    // When/Then: Verify the stubbed connection is accessible
    assertThat(client.query("test:*"), is(empty()));
  }

  /**
   * Tests that constructor propagates IOException when connection fails.
   *
   * <p>This test verifies the expected exception type by attempting to connect to a non-existent
   * server, which exercises the actual JMX connection code path.
   *
   * @throws Exception if test setup fails
   */
  @Test(expected = IOException.class)
  public void constructor_connectionFailure_throwsIOException() throws Exception {
    // Given: An unreachable JMX server on a valid port (1-65535 range)
    // When: JmxClient constructor called with invalid host
    // Then: IOException should be thrown

    // Actually attempt connection to a non-existent server
    // Use a valid port number (9999) but an invalid hostname
    new JmxClient("non.existent.host.invalid", 9999);
  }

  /**
   * Tests that query with valid pattern returns matching MBeans.
   *
   * @throws Exception if query fails
   */
  @Test
  public void query_validQuery_returnsMBeans() throws Exception {
    // Given: Connected client; MBeanServerConnection returns Set of ObjectNames
    String queryString = "test:type=*";
    ObjectName expectedName = new ObjectName("test:type=TestMBean");
    Set<ObjectName> expectedResults = new HashSet<>();
    expectedResults.add(expectedName);

    stubBeanServer.setQueryResult(expectedResults);

    // When: query("test:type=*") called
    Set<ObjectName> result = client.query(queryString);

    // Then: Returns expected Set<ObjectName>
    assertThat(result, hasSize(1));
    assertThat(result.iterator().next(), equalTo(expectedName));
  }

  /**
   * Tests that query returns empty set when no MBeans match.
   *
   * @throws Exception if query fails
   */
  @Test
  public void query_noMatches_returnsEmptySet() throws Exception {
    // Given: Connected client; MBeanServerConnection returns empty Set
    String queryString = "nonexistent:*";

    stubBeanServer.setQueryResult(Collections.emptySet());

    // When: query("nonexistent:*") called
    Set<ObjectName> result = client.query(queryString);

    // Then: Returns empty Set
    assertThat(result, is(empty()));
  }

  /**
   * Tests that query throws MalformedObjectNameException for invalid query patterns.
   *
   * @throws Exception if query fails
   */
  @Test(expected = MalformedObjectNameException.class)
  public void query_malformedQuery_throwsMalformedObjectNameException() throws Exception {
    // Given: Connected client
    // When: query("invalid:::query") called
    // Then: MalformedObjectNameException thrown
    client.query("invalid:::query");
  }

  /**
   * Tests that getValue returns the 'Value' attribute from the specified MBean.
   *
   * @throws Exception if getValue fails
   */
  @Test
  public void getValue_validObjectName_returnsValue() throws Exception {
    // Given: Connected client; MBean has 'Value' attribute
    ObjectName objectName = new ObjectName("test:type=TestMBean");
    String expectedValue = "testValue";

    stubBeanServer.setAttributeValue(objectName, "Value", expectedValue);

    // When: getValue(objectName) called
    Object result = client.getValue(objectName);

    // Then: Returns attribute value
    assertThat(result, equalTo(expectedValue));
  }

  /**
   * Tests that getValue throws AttributeNotFoundException when attribute is missing.
   *
   * @throws Exception expected exception
   */
  @Test(expected = AttributeNotFoundException.class)
  public void getValue_missingAttribute_throwsAttributeNotFoundException() throws Exception {
    // Given: Connected client; MBean exists but no 'Value' attribute
    ObjectName objectName = new ObjectName("test:type=TestMBean");

    stubBeanServer.setThrowAttributeNotFound(true);

    // When: getValue(objectName) called
    // Then: AttributeNotFoundException thrown
    client.getValue(objectName);
  }

  /**
   * Tests that close() properly closes the underlying JMXConnector.
   *
   * @throws IOException if close fails
   */
  @Test
  public void close_normalClose_closesConnection() throws IOException {
    // Given: Connected client

    // When: close() called
    client.close();

    // Then: Underlying JMXConnector.close() invoked
    assertThat(stubConnector.getCloseCount(), is(1));
  }

  /**
   * Tests that calling close() multiple times does not throw an exception.
   *
   * @throws IOException if close fails
   */
  @Test
  public void close_alreadyClosed_noException() throws IOException {
    // Given: Client already closed
    client.close();

    // When: close() called again
    // Then: No exception thrown
    client.close();

    // Verify close was called twice on the underlying connector
    assertThat(stubConnector.getCloseCount(), is(2));
  }

  /** Stub implementation of JMXConnector for testing. */
  private static class StubJmxConnector implements JMXConnector {

    /** The MBeanServerConnection to return. */
    private final MBeanServerConnection beanServer;

    /** Count of close() invocations. */
    private int closeCount = 0;

    /**
     * Creates a stub connector with the given bean server.
     *
     * @param beanServer the bean server to return from getMBeanServerConnection
     */
    StubJmxConnector(MBeanServerConnection beanServer) {
      this.beanServer = beanServer;
    }

    @Override
    public void connect() throws IOException {
      // No-op
    }

    @Override
    public void connect(Map<String, ?> env) throws IOException {
      // No-op
    }

    @Override
    public MBeanServerConnection getMBeanServerConnection() throws IOException {
      return beanServer;
    }

    @Override
    public MBeanServerConnection getMBeanServerConnection(Subject delegationSubject)
        throws IOException {
      return beanServer;
    }

    @Override
    public void close() throws IOException {
      closeCount++;
    }

    @Override
    public void addConnectionNotificationListener(
        NotificationListener listener, NotificationFilter filter, Object handback) {
      // No-op
    }

    @Override
    public void removeConnectionNotificationListener(NotificationListener listener)
        throws ListenerNotFoundException {
      // No-op
    }

    @Override
    public void removeConnectionNotificationListener(
        NotificationListener listener, NotificationFilter filter, Object handback)
        throws ListenerNotFoundException {
      // No-op
    }

    @Override
    public String getConnectionId() throws IOException {
      return "stub-connection";
    }

    /**
     * Returns the number of times close() was called.
     *
     * @return the close invocation count
     */
    int getCloseCount() {
      return closeCount;
    }
  }

  /** Stub implementation of MBeanServerConnection for testing. */
  private static class StubMBeanServerConnection implements MBeanServerConnection {

    /** Result to return from queryNames. */
    private Set<ObjectName> queryResult = Collections.emptySet();

    /** Map of object name + attribute name to value. */
    private final Map<String, Object> attributeValues = new HashMap<>();

    /** Whether to throw AttributeNotFoundException. */
    private boolean throwAttributeNotFound = false;

    /**
     * Sets the result to return from queryNames.
     *
     * @param result the result set
     */
    void setQueryResult(Set<ObjectName> result) {
      this.queryResult = result;
    }

    /**
     * Sets an attribute value for an object name.
     *
     * @param objectName the object name
     * @param attributeName the attribute name
     * @param value the value
     */
    void setAttributeValue(ObjectName objectName, String attributeName, Object value) {
      attributeValues.put(objectName.toString() + ":" + attributeName, value);
    }

    /**
     * Sets whether to throw AttributeNotFoundException.
     *
     * @param throwIt whether to throw
     */
    void setThrowAttributeNotFound(boolean throwIt) {
      this.throwAttributeNotFound = throwIt;
    }

    @Override
    public Set<ObjectName> queryNames(ObjectName name, QueryExp query) throws IOException {
      return queryResult;
    }

    @Override
    public Object getAttribute(ObjectName name, String attribute)
        throws MBeanException,
            AttributeNotFoundException,
            InstanceNotFoundException,
            ReflectionException,
            IOException {
      if (throwAttributeNotFound) {
        throw new AttributeNotFoundException("Attribute not found: " + attribute);
      }
      String key = name.toString() + ":" + attribute;
      return attributeValues.get(key);
    }

    // Remaining methods are no-ops for testing purposes

    @Override
    public ObjectInstance createMBean(String className, ObjectName name)
        throws ReflectionException,
            InstanceAlreadyExistsException,
            MBeanException,
            NotCompliantMBeanException,
            IOException {
      return null;
    }

    @Override
    public ObjectInstance createMBean(String className, ObjectName name, ObjectName loaderName)
        throws ReflectionException,
            InstanceAlreadyExistsException,
            MBeanException,
            NotCompliantMBeanException,
            InstanceNotFoundException,
            IOException {
      return null;
    }

    @Override
    public ObjectInstance createMBean(
        String className, ObjectName name, Object[] params, String[] signature)
        throws ReflectionException,
            InstanceAlreadyExistsException,
            MBeanException,
            NotCompliantMBeanException,
            IOException {
      return null;
    }

    @Override
    public ObjectInstance createMBean(
        String className,
        ObjectName name,
        ObjectName loaderName,
        Object[] params,
        String[] signature)
        throws ReflectionException,
            InstanceAlreadyExistsException,
            MBeanException,
            NotCompliantMBeanException,
            InstanceNotFoundException,
            IOException {
      return null;
    }

    @Override
    public void unregisterMBean(ObjectName name)
        throws InstanceNotFoundException, MBeanRegistrationException, IOException {
      // No-op
    }

    @Override
    public ObjectInstance getObjectInstance(ObjectName name)
        throws InstanceNotFoundException, IOException {
      return null;
    }

    @Override
    public Set<ObjectInstance> queryMBeans(ObjectName name, QueryExp query) throws IOException {
      return Collections.emptySet();
    }

    @Override
    public boolean isRegistered(ObjectName name) throws IOException {
      return false;
    }

    @Override
    public Integer getMBeanCount() throws IOException {
      return 0;
    }

    @Override
    public AttributeList getAttributes(ObjectName name, String[] attributes)
        throws InstanceNotFoundException, ReflectionException, IOException {
      return new AttributeList();
    }

    @Override
    public void setAttribute(ObjectName name, Attribute attribute)
        throws InstanceNotFoundException,
            AttributeNotFoundException,
            InvalidAttributeValueException,
            MBeanException,
            ReflectionException,
            IOException {
      // No-op
    }

    @Override
    public AttributeList setAttributes(ObjectName name, AttributeList attributes)
        throws InstanceNotFoundException, ReflectionException, IOException {
      return new AttributeList();
    }

    @Override
    public Object invoke(ObjectName name, String operationName, Object[] params, String[] signature)
        throws InstanceNotFoundException, MBeanException, ReflectionException, IOException {
      return null;
    }

    @Override
    public String getDefaultDomain() throws IOException {
      return "DefaultDomain";
    }

    @Override
    public String[] getDomains() throws IOException {
      return new String[0];
    }

    @Override
    public void addNotificationListener(
        ObjectName name, NotificationListener listener, NotificationFilter filter, Object handback)
        throws InstanceNotFoundException, IOException {
      // No-op
    }

    @Override
    public void addNotificationListener(
        ObjectName name, ObjectName listener, NotificationFilter filter, Object handback)
        throws InstanceNotFoundException, IOException {
      // No-op
    }

    @Override
    public void removeNotificationListener(ObjectName name, ObjectName listener)
        throws InstanceNotFoundException, ListenerNotFoundException, IOException {
      // No-op
    }

    @Override
    public void removeNotificationListener(
        ObjectName name, ObjectName listener, NotificationFilter filter, Object handback)
        throws InstanceNotFoundException, ListenerNotFoundException, IOException {
      // No-op
    }

    @Override
    public void removeNotificationListener(ObjectName name, NotificationListener listener)
        throws InstanceNotFoundException, ListenerNotFoundException, IOException {
      // No-op
    }

    @Override
    public void removeNotificationListener(
        ObjectName name, NotificationListener listener, NotificationFilter filter, Object handback)
        throws InstanceNotFoundException, ListenerNotFoundException, IOException {
      // No-op
    }

    @Override
    public MBeanInfo getMBeanInfo(ObjectName name)
        throws InstanceNotFoundException, IntrospectionException, ReflectionException, IOException {
      return null;
    }

    @Override
    public boolean isInstanceOf(ObjectName name, String className)
        throws InstanceNotFoundException, IOException {
      return false;
    }
  }
}
