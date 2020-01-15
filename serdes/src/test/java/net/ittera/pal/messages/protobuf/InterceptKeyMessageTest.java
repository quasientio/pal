package net.ittera.pal.messages.protobuf;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import java.util.Arrays;
import java.util.List;
import net.ittera.pal.messages.protobuf.Exec.ExecMessageType;
import net.ittera.pal.messages.protobuf.Intercepts.InterceptKeyMessage;
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
