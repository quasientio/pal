package com.ittera.cometa.messages.protobuf;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import com.ittera.cometa.messages.protobuf.Exec.ExecMessageType;
import com.ittera.cometa.messages.protobuf.Intercepts.InterceptKeyMessage;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class InterceptKeyMessageTest {

  @Test
  public void equals() {

    final String classname = "java.io.PrintStream";
    final String executableName = "println";
    final List<String> paramTypes = Arrays.asList("java.lang.String", "java.lang.String");

    final InterceptKeyMessage keyMessage1 =
        InterceptKeyMessage.newBuilder()
            .setClazz(classname)
            .setExecutableName(executableName)
            .setMsgType(ExecMessageType.CONSTRUCTOR)
            .addAllParameterType(paramTypes)
            .build();

    final InterceptKeyMessage keyMessage2 =
        InterceptKeyMessage.newBuilder()
            .setClazz(classname)
            .setExecutableName(executableName)
            .setMsgType(ExecMessageType.CONSTRUCTOR)
            .addAllParameterType(paramTypes)
            .build();

    assertThat(keyMessage1, is(keyMessage2));
  }
}
