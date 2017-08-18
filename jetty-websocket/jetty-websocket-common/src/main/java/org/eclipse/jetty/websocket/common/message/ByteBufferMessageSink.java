//
//  ========================================================================
//  Copyright (c) 1995-2017 Mort Bay Consulting Pty. Ltd.
//  ------------------------------------------------------------------------
//  All rights reserved. This program and the accompanying materials
//  are made available under the terms of the Eclipse Public License v1.0
//  and Apache License v2.0 which accompanies this distribution.
//
//      The Eclipse Public License is available at
//      http://www.eclipse.org/legal/epl-v10.html
//
//      The Apache License v2.0 is available at
//      http://www.opensource.org/licenses/apache2.0.php
//
//  You may elect to redistribute this code under either of these licenses.
//  ========================================================================
//

package org.eclipse.jetty.websocket.common.message;

import java.io.ByteArrayOutputStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.nio.ByteBuffer;
import java.util.Objects;

import org.eclipse.jetty.util.BufferUtil;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.websocket.common.MessageSink;
import org.eclipse.jetty.websocket.core.Frame;
import org.eclipse.jetty.websocket.core.WSPolicy;
import org.eclipse.jetty.websocket.core.invoke.InvalidSignatureException;

public class ByteBufferMessageSink implements MessageSink
{
    private static final int BUFFER_SIZE = 65535;
    private final WSPolicy policy;
    private final MethodHandle onMessageMethod;
    private ByteArrayOutputStream out;
    private int size;

    public ByteBufferMessageSink(WSPolicy policy, MethodHandle methodHandle)
    {
        // Validate onMessageMethod
        Objects.requireNonNull(methodHandle, "MethodHandle");
        MethodType onMessageType = MethodType.methodType(Void.TYPE, ByteBuffer.class);
        if (methodHandle.type() != onMessageType)
        {
            throw InvalidSignatureException.build(onMessageType, methodHandle.type());
        }

        this.onMessageMethod = methodHandle;
        this.policy = policy;
    }

    @Override
    public void accept(Frame frame, Callback callback)
    {
        try
        {
            if (frame.hasPayload())
            {
                ByteBuffer payload = frame.getPayload();
                policy.assertValidBinaryMessageSize(size + payload.remaining());
                size += payload.remaining();

                if (out == null)
                    out = new ByteArrayOutputStream(BUFFER_SIZE);

                BufferUtil.writeTo(payload, out);
            }

            if (frame.isFin())
            {
                if (out != null)
                    onMessageMethod.invoke(ByteBuffer.wrap(out.toByteArray()));
                else
                    onMessageMethod.invoke(BufferUtil.EMPTY_BUFFER);
            }

            callback.succeeded();
        }
        catch (Throwable t)
        {
            callback.failed(t);
        }
        finally
        {
            if (frame.isFin())
            {
                // reset
                out = null;
                size = 0;
            }
        }
    }
}
