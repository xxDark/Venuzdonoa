package dev.xdark.venuzdonoa;

import org.junit.jupiter.api.Test;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

class VenuzdonoaTest {
    @org.junit.jupiter.api.Test
    void basicAccessorTest() {
        Object hopefullyUnsafe = assertDoesNotThrow(() -> {
            Method m = Class.forName("jdk.internal.misc.Unsafe").getDeclaredMethod("getUnsafe");
            MethodHandle methodHandle = Venuzdonoa.makeMethodAccessor(m);
            return methodHandle.invoke();
        });
        assertTrue(hopefullyUnsafe.toString().startsWith("jdk.internal.misc.Unsafe"));
    }

    /**
     * returns arg
     *
     * @param arg to return
     *
     * @return arg
     * @see #testWithParams()
     */
    private String accessMe(String arg) {
        return arg;
    }

    @Test
    void testWithParams() {
        Object ret = assertDoesNotThrow(() -> {
            Method target = this.getClass().getDeclaredMethod("accessMe", String.class);
            // I used the NativeMethodAccessorImpl to invoke the NativeMethodAccessorImpl
            Method invoke0 = Class.forName("jdk.internal.reflect.NativeMethodAccessorImpl").getDeclaredMethod("invoke0", Method.class, Object.class, Object[].class);
            MethodHandle methodHandle = Venuzdonoa.makeMethodAccessor(invoke0);
            return methodHandle.invoke(target, this, new Object[] { "Hello Chat" });
        });
        assertEquals("Hello Chat", ret);
    }
}