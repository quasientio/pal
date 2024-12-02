package net.ittera.pal.serdes.jsonrpc;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.collection.IsEmptyCollection.empty;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import net.ittera.pal.messages.jsonrpc.Argument;
import net.ittera.pal.messages.jsonrpc.JsonRpcRequest;
import org.junit.Test;

public class JsonRpcMessageFactoryTest {

  @Test
  public void buildConstructorCallWithoutArgs() {
    String id = UUID.randomUUID().toString();
    JsonRpcRequest request = JsonRpcMessageFactory.buildConstructorCall(id, "SomeClass", null);
    assertNotNull(request);
    assertThat(request.getId(), is(id));
    assertThat(request.getMethod(), is("new"));
    assertNull(request.getParams().getMethod());
    assertNull(request.getParams().getField());
    assertNull(request.getParams().getInstance());
    assertNull(request.getParams().getValue());
    assertThat(request.getParams().getType(), is("SomeClass"));
    assertThat(request.getParams().getArgs(), is(empty()));
  }

  @Test
  public void buildConstructorCallWithArgs() {
    String id = UUID.randomUUID().toString();
    List<Argument> args =
        Arrays.asList(new Argument("Hello, World!", "String"), new Argument(23823));
    JsonRpcRequest request = JsonRpcMessageFactory.buildConstructorCall(id, "SomeClass", args);
    assertNotNull(request);
    assertThat(request.getId(), is(id));
    assertThat(request.getMethod(), is("new"));
    assertNull(request.getParams().getMethod());
    assertNull(request.getParams().getField());
    assertNull(request.getParams().getInstance());
    assertNull(request.getParams().getValue());
    assertThat(request.getParams().getType(), is("SomeClass"));
    assertThat(request.getParams().getArgs(), is(args));
  }

  @Test
  public void buildClassMethodCallWithoutArgs() {
    List<Argument> emptyArgs = new ArrayList<>();
    String id = UUID.randomUUID().toString();
    JsonRpcRequest request =
        JsonRpcMessageFactory.buildClassMethodCall(id, "SomeClass", "someMethod", emptyArgs);
    assertNotNull(request);
    assertThat(request.getId(), is(id));
    assertThat(request.getMethod(), is("call"));
    assertThat(request.getParams().getMethod(), is("someMethod"));
    assertThat(request.getParams().getType(), is("SomeClass"));
    assertNull(request.getParams().getField());
    assertNull(request.getParams().getInstance());
    assertNull(request.getParams().getValue());
    assertThat(request.getParams().getArgs(), is(empty()));
  }

  @Test
  public void buildClassMethodCallWithArgs() {
    String id = UUID.randomUUID().toString();
    List<Argument> args =
        Arrays.asList(new Argument("Hello, World!", "String"), new Argument(23823));
    JsonRpcRequest request =
        JsonRpcMessageFactory.buildClassMethodCall(id, "SomeClass", "someMethod", args);
    assertNotNull(request);
    assertThat(request.getId(), is(id));
    assertThat(request.getMethod(), is("call"));
    assertThat(request.getParams().getMethod(), is("someMethod"));
    assertThat(request.getParams().getType(), is("SomeClass"));
    assertNull(request.getParams().getField());
    assertNull(request.getParams().getInstance());
    assertNull(request.getParams().getValue());
    assertThat(request.getParams().getArgs(), is(args));
  }

  @Test
  public void buildInstanceMethodCallWithoutArgs() {
    String id = UUID.randomUUID().toString();
    int instanceId = 942389;
    JsonRpcRequest request =
        JsonRpcMessageFactory.buildInstanceMethodCall(
            id, "SomeClass", "someMethod", instanceId, null);
    assertNotNull(request);
    assertThat(request.getId(), is(id));
    assertThat(request.getMethod(), is("call"));
    assertThat(request.getParams().getMethod(), is("someMethod"));
    assertThat(request.getParams().getType(), is("SomeClass"));
    assertThat(request.getParams().getInstance(), is(instanceId));
    assertNull(request.getParams().getField());
    assertNull(request.getParams().getValue());
    assertThat(request.getParams().getArgs(), is(empty()));
  }

  @Test
  public void buildInstanceMethodCallWithArgs() {
    String id = UUID.randomUUID().toString();
    int instanceId = 942389;
    List<Argument> args =
        Arrays.asList(new Argument("Hello, World!", "String"), new Argument(23823));
    JsonRpcRequest request =
        JsonRpcMessageFactory.buildInstanceMethodCall(
            id, "SomeClass", "someMethod", instanceId, args);
    assertNotNull(request);
    assertThat(request.getId(), is(id));
    assertThat(request.getMethod(), is("call"));
    assertThat(request.getParams().getMethod(), is("someMethod"));
    assertThat(request.getParams().getType(), is("SomeClass"));
    assertThat(request.getParams().getInstance(), is(instanceId));
    assertNull(request.getParams().getField());
    assertNull(request.getParams().getValue());
    assertThat(request.getParams().getArgs(), is(args));
  }

  @Test
  public void buildStaticFieldGet() {
    String id = UUID.randomUUID().toString();
    JsonRpcRequest request =
        JsonRpcMessageFactory.buildStaticFieldGet(id, "SomeClass", "someField");
    assertNotNull(request);
    assertThat(request.getId(), is(id));
    assertThat(request.getMethod(), is("get"));
    assertNull(request.getParams().getMethod());
    assertNull(request.getParams().getValue());
    assertNull(request.getParams().getInstance());
    assertThat(request.getParams().getType(), is("SomeClass"));
    assertThat(request.getParams().getField(), is("someField"));
    assertThat(request.getParams().getArgs(), is(empty()));
  }

  @Test
  public void buildInstanceFieldGet() {
    String id = UUID.randomUUID().toString();
    int instanceId = 942389;
    JsonRpcRequest request =
        JsonRpcMessageFactory.buildInstanceFieldGet(id, "SomeClass", instanceId, "someField");
    assertNotNull(request);
    assertThat(request.getId(), is(id));
    assertThat(request.getMethod(), is("get"));
    assertNull(request.getParams().getMethod());
    assertNull(request.getParams().getValue());
    assertThat(request.getParams().getType(), is("SomeClass"));
    assertThat(request.getParams().getInstance(), is(instanceId));
    assertThat(request.getParams().getField(), is("someField"));
    assertThat(request.getParams().getArgs(), is(empty()));
  }

  @Test
  public void buildStaticFieldPut() {
    String id = UUID.randomUUID().toString();
    JsonRpcRequest request =
        JsonRpcMessageFactory.buildStaticFieldPut(
            id, "SomeClass", "someField", new Argument("Hello, World!", "String"));
    assertNotNull(request);
    assertThat(request.getId(), is(id));
    assertThat(request.getMethod(), is("put"));
    assertNull(request.getParams().getMethod());
    assertThat(request.getParams().getType(), is("SomeClass"));
    assertNull(request.getParams().getInstance());
    assertThat(request.getParams().getField(), is("someField"));
    assertThat(request.getParams().getValue(), is(new Argument("Hello, World!", "String")));
    assertThat(request.getParams().getArgs(), is(empty()));
  }

  @Test
  public void buildInstanceFieldPut() {
    String id = UUID.randomUUID().toString();
    int instanceId = 942389;
    JsonRpcRequest request =
        JsonRpcMessageFactory.buildInstanceFieldPut(
            id, "SomeClass", instanceId, "someField", new Argument("Hello, World!", "String"));
    assertNotNull(request);
    assertThat(request.getId(), is(id));
    assertThat(request.getMethod(), is("put"));
    assertNull(request.getParams().getMethod());
    assertThat(request.getParams().getType(), is("SomeClass"));
    assertThat(request.getParams().getInstance(), is(instanceId));
    assertThat(request.getParams().getField(), is("someField"));
    assertThat(request.getParams().getValue(), is(new Argument("Hello, World!", "String")));
    assertThat(request.getParams().getArgs(), is(empty()));
  }
}
