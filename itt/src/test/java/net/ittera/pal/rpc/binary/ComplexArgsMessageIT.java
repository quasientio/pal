package net.ittera.pal.rpc.binary;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import net.ittera.pal.common.objects.ObjectRef;
import net.ittera.pal.messages.colfer.ReturnValue;
import net.ittera.pal.serdes.Unwrapper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/** Naming convention to use: methodName_stateUnderTest_expectedBehavior. */
@RunWith(Parameterized.class)
public class ComplexArgsMessageIT extends AbstractBinaryRpcMessageIT {

  private static final String CLASS_NAME = "net.ittera.pal.apps.rpc.Methods";

  public ComplexArgsMessageIT(TargetType targetType) {
    super(targetType);
  }

  @Parameterized.Parameters(name = "{index}: channel={0}")
  public static Collection<Object[]> data() {
    return getSendTargetParameters();
  }

  @Test
  public void callClassMethod_withArrayList_void() throws ClassNotFoundException {

    final String methodName = "nonVoidSumUpList";

    // 1. Create a new ArrayList instance with some integers
    Integer[] someIntegers = {39, 5, 58, 32, 70, 42};
    ArrayList<Integer> arrayList = new ArrayList<>();
    String[] parameterTypes = new String[] {arrayList.getClass().getName()};
    Object[] parameters = new Object[] {arrayList};
    Collections.addAll(arrayList, someIntegers);

    // 2. Call the static method with the list as argument BY VALUE
    ReturnValue retValue =
        callClassMethod(
            CLASS_NAME,
            methodName,
            parameterTypes,
            parameters,
            new ObjectRef[parameterTypes.length]);

    assertValueIsObjectOfType(retValue, Integer.class.getName());
    Integer shouldReturn = Arrays.stream(someIntegers).mapToInt(Integer::intValue).sum();
    Object rawObj = Unwrapper.unwrapObject(retValue.getObject());
    assertEquals(shouldReturn, rawObj);
  }

  @Test
  public void callClassMethod_withHashMap_void() throws ClassNotFoundException {

    final String methodName = "nonVoidSumUpMap";

    // 1. Create a new HashMap instance with some floats as values
    Float[] someFloats = {39f, 5.8f, -12f, 58.4f, 32f, 7f, 38338f, 42.98f};
    Map<String, Float> map = new HashMap<>();
    String[] parameterTypes = new String[] {map.getClass().getName()};
    Object[] parameters = new Object[] {map};
    int idx = 0;
    for (float f : someFloats) {
      map.put(String.valueOf(f), someFloats[idx++]);
    }

    // 2. Call the static method with the map as argument BY VALUE
    ReturnValue retValue =
        callClassMethod(
            CLASS_NAME,
            methodName,
            parameterTypes,
            parameters,
            new ObjectRef[parameterTypes.length]);

    assertValueIsObjectOfType(retValue, Float.class.getName());
    double shouldReturn = Arrays.stream(someFloats).mapToDouble(Float::floatValue).sum();
    Object rawObj = Unwrapper.unwrapObject(retValue.getObject());
    assertEquals(shouldReturn, (Float) rawObj, 0.001);
  }
}
