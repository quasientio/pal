package com.ittera.cometa.concentrator;

import com.ittera.cometa.concentrator.messages.protobuf.ProtobufDataMessageBuilder;
import com.ittera.cometa.concentrator.messages.protobuf.data.Values.ReturnValue;
import com.ittera.cometa.concentrator.messages.protobuf.data.Wrappers.DataMessage;
import com.ittera.cometa.concentrator.messages.protobuf.data.Primitives;
import com.ittera.cometa.concentrator.messages.DataMessageBuilder;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import java.util.UUID;

import java.io.IOException;

import org.junit.AfterClass;
import org.junit.BeforeClass;

import static org.junit.Assert.*;

public abstract class AbstractConcentratorTest {

    protected final static Logger logger = LogManager.getLogger("tests");

    protected final static UUID clientId = UUID.randomUUID();

    protected static DataMessageBuilder dataMessageBuilder;
    private static DualPeer dualPeer;

    @BeforeClass
    public static void initialize() throws Exception {

        dataMessageBuilder = new ProtobufDataMessageBuilder();
        dualPeer = new DualPeer("/tests.properties");

    }

    protected DataMessage sendAndReceive(DataMessage message) {
        return dualPeer.sendAndReceive(message);
    }

    /**
     * Helper assertion methods
     * This method is also useful as it encapsulates details of the protobuf serialization
     *
     * @param returnValue
     * @param className
     * @return
     */
    private void isObjectOfRightType(ReturnValue returnValue, String className, boolean isObjRef, boolean isNull, boolean isArray) {
        assertFalse(returnValue.getIsVoid());
        assertFalse(returnValue.getIsClass());
        assertTrue(returnValue.hasClazz());
        assertEquals(className, returnValue.getClazz().getName());
        assertTrue(returnValue.hasObject());

        Primitives.Object retObj = returnValue.getObject();
        assertEquals(isArray, retObj.getIsArray());
        assertEquals(isNull, retObj.getIsNull());
        assertEquals(isObjRef, retObj.hasRef());
        assertTrue(retObj.hasClass_());
        assertFalse(retObj.getClass_().getUnknown());
        assertEquals(className, retObj.getClass_().getName());

    }

    protected void assertValueIsObjectOfRightType(ReturnValue returnValue, String className) {
        isObjectOfRightType(returnValue, className, false, false, false);
    }

    protected void assertValueIsObjectRefOfRightType(ReturnValue returnValue, String className) {
        isObjectOfRightType(returnValue, className, true, false, false);
    }

    protected void assertValueIsWrappedArrayOfRightType(ReturnValue returnValue, String className) {
        isObjectOfRightType(returnValue, className, false, false, true);
    }

    protected void assertValueIsArrayOfRightType(ReturnValue returnValue, String className) {
        isObjectOfRightType(returnValue, className, true, false, true);
    }

    protected void assertValueIsNullObjectOfRightType(ReturnValue returnValue, String className) {
        isObjectOfRightType(returnValue, className, false, true, false);
    }

    protected void assertValueIsNullArrayOfRightType(ReturnValue returnValue, String className) {
        isObjectOfRightType(returnValue, className, false, true, true);
    }

    @AfterClass
    public static void finalizeStuff() {
        logger.debug("Finalizing after tests...");
        dualPeer.close();
    }
}

