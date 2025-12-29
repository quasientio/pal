/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.serdes.jsonrpc;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

import io.quasient.pal.common.objects.ObjectRef;
import io.quasient.pal.messages.jsonrpc.Argument;
import io.quasient.pal.messages.jsonrpc.JsonRpcRequest;
import io.quasient.pal.messages.jsonrpc.Params;
import io.quasient.pal.messages.types.ControlCommandType;
import io.quasient.pal.messages.types.MessageFamily;
import io.quasient.pal.messages.types.MetaServiceType;
import java.util.List;
import org.junit.Test;

public class JsonRpcMessageFactoryTest {

  @Test
  public void buildConstructorCall_setsNewMethod_andTypeAndArgs() {
    JsonRpcRequest req =
        JsonRpcMessageFactory.buildConstructorCall(
            "java.lang.String", List.of(Argument.builder().withValue("abc").build()));
    assertThat(req.getMethod(), is("new"));
    Params p = req.getParams();
    assertThat(p.getType(), is("java.lang.String"));
    assertThat(p.getArgs().size(), is(1));
  }

  @Test
  public void buildClassAndInstanceMethodCall_setCallMethod_andFields() {
    JsonRpcRequest classReq =
        JsonRpcMessageFactory.buildClassMethodCall(
            "java.lang.Integer", "valueOf", List.of(Argument.builder().withValue("5").build()));
    assertThat(classReq.getMethod(), is("call"));
    assertThat(classReq.getParams().getType(), is("java.lang.Integer"));
    assertThat(classReq.getParams().getMethod(), is("valueOf"));

    JsonRpcRequest instReq =
        JsonRpcMessageFactory.buildInstanceMethodCall(
            "java.lang.String", "substring", 1, List.of(Argument.builder().withValue(0).build()));
    assertThat(instReq.getMethod(), is("call"));
    assertThat(instReq.getParams().getInstance(), is(1));
  }

  @Test
  public void buildStaticAndInstanceField_getPut_setFields() {
    JsonRpcRequest sget = JsonRpcMessageFactory.buildStaticFieldGet("java.lang.System", "out");
    assertThat(sget.getMethod(), is("get"));
    assertThat(sget.getParams().getField(), is("out"));

    JsonRpcRequest sput =
        JsonRpcMessageFactory.buildStaticFieldPut(
            "java.lang.System", "out", Argument.builder().withValue("x").build());
    assertThat(sput.getMethod(), is("put"));
    assertThat(sput.getParams().getField(), is("out"));
    assertThat(sput.getParams().getValue() != null, is(true));

    ObjectRef ref = ObjectRef.from(7);
    JsonRpcRequest iget = JsonRpcMessageFactory.buildInstanceFieldGet("T", ref, "field");
    assertThat(iget.getMethod(), is("get"));
    assertThat(iget.getParams().getInstance(), is(7));

    JsonRpcRequest iput =
        JsonRpcMessageFactory.buildInstanceFieldPut(
            "T", ref, "field", Argument.builder().withValue(1).build());
    assertThat(iput.getMethod(), is("put"));
    assertThat(iput.getParams().getInstance(), is(7));
  }

  @Test
  public void buildMetaAndControlMessages_setFamilies_andService() {
    JsonRpcRequest meta =
        JsonRpcMessageFactory.buildFetchClassesInfoMetaMessage(
            new String[] {"A"}, new String[] {"java."}, true, false);
    assertThat(meta.getMethod(), is(MessageFamily.META.getJsonName()));
    assertThat(meta.getParams().getMethod(), is(MetaServiceType.FETCH_CLASSES_INFO.getJsonName()));
    // ensure args include keys
    String s = meta.toString();
    assertThat(s, containsString("compress_encode"));
    assertThat(s, containsString("merge_ancestry"));
    assertThat(s, containsString("exclude_prefixes"));
    assertThat(s, containsString("include_classes"));

    JsonRpcRequest delObj = JsonRpcMessageFactory.buildDeleteObjectCommandMessage(3);
    assertThat(delObj.getMethod(), is(MessageFamily.CONTROL.getJsonName()));
    assertThat(delObj.getParams().getMethod(), is(ControlCommandType.DELETE_OBJECT.getJsonName()));
    assertThat(delObj.getParams().getArgs().size(), is(1));

    JsonRpcRequest delSess = JsonRpcMessageFactory.buildDeleteSessionCommandMessage();
    assertThat(
        delSess.getParams().getMethod(), is(ControlCommandType.DELETE_SESSION.getJsonName()));
    JsonRpcRequest gc = JsonRpcMessageFactory.buildGcCommandMessage();
    assertThat(gc.getParams().getMethod(), is(ControlCommandType.GC.getJsonName()));
  }
}
