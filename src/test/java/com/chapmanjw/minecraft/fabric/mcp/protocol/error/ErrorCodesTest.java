package com.chapmanjw.minecraft.fabric.mcp.protocol.error;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;

import org.junit.jupiter.api.Test;

class ErrorCodesTest {

    @Test
    void standardJsonRpcCodes() {
        assertEquals(-32700, ErrorCodes.PARSE_ERROR);
        assertEquals(-32600, ErrorCodes.INVALID_REQUEST);
        assertEquals(-32601, ErrorCodes.METHOD_NOT_FOUND);
        assertEquals(-32602, ErrorCodes.INVALID_PARAMS);
        assertEquals(-32603, ErrorCodes.INTERNAL_ERROR);
    }

    @Test
    void mcpSpecificCodes() {
        assertEquals(-32001, ErrorCodes.TOOL_INPUT_INVALID);
        assertEquals(-32002, ErrorCodes.TOOL_HANDLER_ERROR);
        assertEquals(-32003, ErrorCodes.MAIN_THREAD_TIMEOUT);
        assertEquals(-32004, ErrorCodes.SERVER_NOT_RUNNING);
        assertEquals(-32005, ErrorCodes.TOOL_NOT_COMPATIBLE);
    }

    @Test
    void allCodesAreDistinct() {
        int[] codes = {
            ErrorCodes.PARSE_ERROR,
            ErrorCodes.INVALID_REQUEST,
            ErrorCodes.METHOD_NOT_FOUND,
            ErrorCodes.INVALID_PARAMS,
            ErrorCodes.INTERNAL_ERROR,
            ErrorCodes.TOOL_INPUT_INVALID,
            ErrorCodes.TOOL_HANDLER_ERROR,
            ErrorCodes.MAIN_THREAD_TIMEOUT,
            ErrorCodes.SERVER_NOT_RUNNING,
            ErrorCodes.TOOL_NOT_COMPATIBLE,
        };
        for (int i = 0; i < codes.length; i++) {
            for (int j = i + 1; j < codes.length; j++) {
                assertNotEquals(codes[i], codes[j], "duplicate code at indexes " + i + "/" + j);
            }
        }
    }

    @Test
    void constructorIsPrivate() throws Exception {
        Constructor<ErrorCodes> ctor = ErrorCodes.class.getDeclaredConstructor();
        assertTrue(Modifier.isPrivate(ctor.getModifiers()));
        ctor.setAccessible(true);
        // Invoke to mark the line covered.
        ctor.newInstance();
    }
}
