package net.ittera.pal.serdes.colfer;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.UUID;
import net.ittera.pal.common.lang.reflect.ConstructorSignature;
import net.ittera.pal.common.objects.ObjectRef;
import net.ittera.pal.messages.colfer.ExecMessage;
import net.ittera.pal.messages.colfer.InternalHeader;
import net.ittera.pal.messages.types.ExecMessageType;
import net.ittera.pal.messages.types.InternalHeaderType;
import org.junit.Before;
import org.junit.Test;

class DummyClassForTest {
  DummyClassForTest(String str1, int number) {}
}

public class MessageBuilderTest {

  private net.ittera.pal.common.runtime.Context createContextForConstructor() throws Exception {
    ConstructorSignature constructorSignature =
        new ConstructorSignature(
            DummyClassForTest.class.getDeclaredConstructor(new Class[] {String.class, int.class}));
    String sourceFile = "MessageBuilderTest.java";
    int lineNumber = 17;
    Class withinType = DummyClassForTest.class;
    return new net.ittera.pal.common.runtime.Context(
        sourceFile, lineNumber, withinType, constructorSignature);
  }

  private MessageBuilder messageBuilder;
  private MessageBuilder messageBuilderWithContext;
  private net.ittera.pal.common.runtime.Context constructorContext;

  @Before
  public void setUp() throws Exception {
    messageBuilder = new MessageBuilder();
    messageBuilderWithContext = new MessageBuilder(Boolean.toString(true));
    constructorContext = createContextForConstructor();
  }

  @Test
  public void MessageBuilder_NoArgs_newMessageBuilder() {
    messageBuilder = new MessageBuilder();
    assertNotNull(messageBuilder);
  }

  @Test
  public void MessageBuilder_WithIncludeSourceContextStr_newMessageBuilder() {
    MessageBuilder messageBuilder = new MessageBuilder(Boolean.toString(false));
    assertNotNull(messageBuilder);
  }

  // <editor-fold desc="Header messages">
  @Test
  public void buildWriteAheadHeader_ValidUuid_newWriteAheadHeader() {
    MessageBuilder messageBuilder = new MessageBuilder();
    UUID peerUuid = UUID.randomUUID();
    InternalHeader header = messageBuilder.buildWriteAheadHeader(peerUuid);
    assertNotNull(header);
    assertEquals(peerUuid.toString(), header.getValue());
    assertEquals((byte) InternalHeaderType.WRITE_AHEAD.ordinal(), header.getHeaderType());
  }
  // </editor-fold>

  // <editor-fold desc="Constructor messages">
  @Test
  public void buildEmptyConstructor_ValidArguments_newConstructorMessage() {
    UUID peerUuid = UUID.randomUUID();
    String className = this.getClass().getName();
    ExecMessage execMessage = messageBuilder.buildEmptyConstructor(peerUuid, className);
    assertNotNull(execMessage);
    assertEquals((byte) ExecMessageType.CONSTRUCTOR.ordinal(), execMessage.execMessageType);
    assertNotNull(execMessage.getConstructorCall());
    assertEquals(peerUuid.toString(), execMessage.getPeerUuid());
    assertEquals(className, execMessage.constructorCall.getClazz().getName());
    assertNull(execMessage.getConstructorCall().getContext());
  }

  @Test
  public void buildNonEmptyConstructor_ValueArguments_newConstructorMessage() {
    UUID peerUuid = UUID.randomUUID();
    String className = this.getClass().getName();
    String[] parameterTypes = {"String", "int"};
    Object[] args = {"test", 123};
    ObjectRef[] argObjRefs = {null, null};
    ExecMessage execMessage =
        messageBuilder.buildNonEmptyConstructor(
            peerUuid, className, parameterTypes, args, argObjRefs);
    assertNotNull(execMessage);
    assertEquals((byte) ExecMessageType.CONSTRUCTOR.ordinal(), execMessage.execMessageType);
    assertNotNull(execMessage.getConstructorCall());
    assertEquals(peerUuid.toString(), execMessage.getPeerUuid());
    assertEquals(className, execMessage.constructorCall.getClazz().getName());
    assertNull(execMessage.getConstructorCall().getContext());
    assertEquals(parameterTypes.length, execMessage.getConstructorCall().getParameters().length);
  }

  @Test
  public void buildConstructor_WithContext_newConstructorMessage() {
    UUID peerUuid = UUID.randomUUID();
    Object sender = this;
    ObjectRef senderObjRef = ObjectRef.randomRef();
    Object[] args = {"test", 123};
    ObjectRef[] argObjRefs = {null, null};
    ExecMessage execMessage =
        messageBuilderWithContext.buildConstructor(
            peerUuid, constructorContext, sender, senderObjRef, args, argObjRefs);
    assertNotNull(execMessage);
    assertEquals((byte) ExecMessageType.CONSTRUCTOR.ordinal(), execMessage.execMessageType);
    assertNotNull(execMessage.getConstructorCall());
    assertEquals(peerUuid.toString(), execMessage.getPeerUuid());
    assertEquals(
        DummyClassForTest.class.getName(), execMessage.getConstructorCall().getClazz().getName());
    assertNotNull(execMessage.getConstructorCall().getContext());

    // verify Context
    assertEquals(
        constructorContext.getSourceFilename(),
        execMessage.getConstructorCall().getContext().getSourceLocationFile());
    assertEquals(
        constructorContext.getSourceLine(),
        execMessage.getConstructorCall().getContext().getSourceLocationLine());
    assertEquals(
        constructorContext.getWithinType().getName(),
        execMessage.getConstructorCall().getContext().getSourceLocationType());

    assertEquals(args.length, execMessage.getConstructorCall().getParameters().length);
  }

  @Test
  public void buildConstructor_ConstructorCallWithNoArgs_newConstructorMessage() {
    UUID peerUuid = UUID.randomUUID();
    Object sender = this;
    ObjectRef senderObjRef = ObjectRef.randomRef();
    Class clazz = ArrayList.class;
    net.ittera.pal.common.api.rmi.ConstructorCall constructorCall =
        new net.ittera.pal.common.api.rmi.ConstructorCall(clazz);
    ExecMessage execMessage =
        messageBuilder.buildConstructor(peerUuid, sender, senderObjRef, constructorCall);
    assertNotNull(execMessage);
    assertEquals((byte) ExecMessageType.CONSTRUCTOR.ordinal(), execMessage.execMessageType);
    assertNotNull(execMessage.getConstructorCall());
    assertEquals(peerUuid.toString(), execMessage.getPeerUuid());
    assertEquals(clazz.getName(), execMessage.constructorCall.getClazz().getName());
    assertNull(execMessage.getConstructorCall().getContext());
    assertEquals(0, execMessage.getConstructorCall().getParameters().length);
  }

  @Test
  public void buildConstructor_ConstructorCallWithArgs_newConstructorMessage() {
    UUID peerUuid = UUID.randomUUID();
    Object sender = this;
    ObjectRef senderObjRef = ObjectRef.randomRef();
    Class clazz = ArrayList.class;
    int arrayListInitialCapacity = 23;
    Object[] args = {arrayListInitialCapacity};
    net.ittera.pal.common.api.rmi.ConstructorCall constructorCall =
        new net.ittera.pal.common.api.rmi.ConstructorCall(clazz)
            .withArgs(args)
            .withParameterTypes(new String[] {"int"});
    ExecMessage execMessage =
        messageBuilder.buildConstructor(peerUuid, sender, senderObjRef, constructorCall);
    assertNotNull(execMessage);
    assertEquals((byte) ExecMessageType.CONSTRUCTOR.ordinal(), execMessage.execMessageType);
    assertNotNull(execMessage.getConstructorCall());
    assertEquals(peerUuid.toString(), execMessage.getPeerUuid());
    assertEquals(clazz.getName(), execMessage.constructorCall.getClazz().getName());
    assertNull(execMessage.getConstructorCall().getContext());
    assertEquals(args.length, execMessage.getConstructorCall().getParameters().length);
  }
  // </editor-fold>
}
