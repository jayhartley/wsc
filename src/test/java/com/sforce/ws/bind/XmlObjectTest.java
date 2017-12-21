/*
 * Copyright (c) 2017, salesforce.com, inc.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided
 * that the following conditions are met:
 *
 *    Redistributions of source code must retain the above copyright notice, this list of conditions and the
 *    following disclaimer.
 *
 *    Redistributions in binary form must reproduce the above copyright notice, this list of conditions and
 *    the following disclaimer in the documentation and/or other materials provided with the distribution.
 *
 *    Neither the name of salesforce.com, inc. nor the names of its contributors may be used to endorse or
 *    promote products derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
 * PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED
 * TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package com.sforce.ws.bind;

import com.sforce.ws.parser.XmlInputStream;
import com.sforce.ws.parser.XmlOutputStream;
import com.sforce.ws.wsdl.Constants;
import junit.framework.Assert;
import junit.framework.TestCase;

import javax.xml.namespace.QName;
import java.io.*;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicLong;

/**
 * XmlObjectTest -- Validates that subclasses of basic types can be serialized
 *
 * @author fhossain
 * @since 170
 */
public class XmlObjectTest extends TestCase {

    private static final String NAMESPACE = "urn:sobject.partner.soap.sforce.com";

    private static class MyDate extends Date {
    }

    public void testSubclassWrite() throws IOException {
        QName qname = new QName("type", NAMESPACE);
        XmlOutputStream xout = new XmlOutputStream(System.out, true);
        TypeMapper typeMapper = new TypeMapper();

        // First try with Date type
        XmlObject obj = new XmlObject(qname);
        obj.setValue(new Date());
        obj.write(qname, xout, typeMapper);

        // Then try subclass of Date
        obj = new XmlObject(qname);
        obj.setValue(new MyDate());
        obj.write(qname, xout, typeMapper);

        // Then try String[]
        obj = new XmlObject(qname);
        obj.setValue(new String[] {"a","b" });
        obj.write(qname, xout, typeMapper);

        // Then try some non-mappable subclass
        obj = new XmlObject(qname);
        obj.setValue(new AtomicLong(10L));
        try {
            obj.write(qname, xout, typeMapper);
            fail("We should have received an exception trying to write object: " + obj.getValue());
        } catch (Exception e) {
            Assert.assertTrue(e.getMessage().contains("Unable to find xml type for"));
        }
    }

    public void testStringArray() throws Exception {
    	String ns = NAMESPACE;
    	QName qname = new QName( ns, "anArray" );
    	TypeMapper typeMapper = new TypeMapper();

    	String[] ab = new String[] { "a","b"  };
		XmlObject obj = new XmlObject( qname );
		obj.setValue( ab );

		// Serialize
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
    	XmlOutputStream xout = new XmlOutputStream( baos, true );
    	xout.setPrefix( "sfdc", ns );
    	xout.setPrefix( "xsi", Constants.SCHEMA_NS );
    	xout.startDocument();
    	xout.writeStartTag(ns, "start");
		obj.write( qname,  xout,  typeMapper );
		xout.writeEndTag(ns, "start");
		xout.close();

		// Parse
		TypeInfo info = new TypeInfo(qname.getNamespaceURI(), "anArray",
		Constants.SCHEMA_NS, "string", 0, -1, true );
		ByteArrayInputStream bais = new ByteArrayInputStream( baos.toByteArray() );
		XmlInputStream xin = new XmlInputStream();
		xin.setInput( bais, "UTF-8" );
		xin.nextTag();
		Object result = typeMapper.readObject(xin, info, String[].class);

		// Assert
		assertTrue( result.getClass().isArray() );
		assertTrue( Arrays.equals(ab, (Object[])result));
    }

    public void testSimpleSerialization() throws IOException, ClassNotFoundException {
        QName qname = new QName(NAMESPACE, "calendar");
        XmlObject original = new XmlObject(qname);
        Calendar cal = Calendar.getInstance();
        original.setValue(cal);
        verifySerialization(original);
    }

    public void testNestedSerialization() throws IOException, ClassNotFoundException {
        QName qname = new QName(NAMESPACE, "top");
        XmlObject originalParent = new XmlObject(qname);
        for (int i=0; i < 10; i ++) {
            originalParent.setField(String.format("Field_%02d", i), (i * 3.14));
        }
        verifySerialization(originalParent);
    }

    public void testArrayValueSerialization() throws IOException, ClassNotFoundException {
        QName qName = new QName(NAMESPACE, "anArray");
        XmlObject original = new XmlObject(qName);
        String[] data = {"one", "two", "three", "four"};
        original.setValue(data);
        verifySerialization(original);
    }

    public void testObjectArrayValueSerialization() throws IOException, ClassNotFoundException {
        QName qName = new QName(NAMESPACE, "anArray");
        XmlObject original = new XmlObject(qName);
        Object[] data = {"one", 2, 3.0f, "four"};
        original.setValue(data);
        verifySerialization(original);
    }

    private static void verifySerialization(XmlObject original) throws IOException, ClassNotFoundException {
        try (ByteArrayOutputStream bitesOut = new ByteArrayOutputStream();
             ObjectOutputStream out = new ObjectOutputStream(bitesOut)) {
            out.writeObject(original);
            try (ByteArrayInputStream bitesIn = new ByteArrayInputStream(bitesOut.toByteArray());
                 ObjectInputStream in = new ObjectInputStream(bitesIn)) {
                Object copyObj = in.readObject();
                assertTrue("Failed to create proper object type.", copyObj instanceof XmlObject);
                XmlObject copy = (XmlObject) copyObj;
                deepAssertEquals(original, copy, 0);
            }
        }
    }

    private static void deepAssertEquals(XmlObject original, XmlObject copy, int depth) {
        assertEquals(original.getName(), copy.getName());
        Object originalValue = original.getValue();
        if (originalValue != null && originalValue.getClass().isArray()) {
            assertTrue("Array value mismatch at depth " + depth,
                    Arrays.equals((Object[])originalValue, (Object[])copy.getValue()));
        } else {
            assertEquals(original.getValue(), copy.getValue());
        }
        boolean hasChildren = original.hasChildren();
        assertEquals("Children do not match.", hasChildren, copy.hasChildren());
        if (hasChildren) {
            Iterator<XmlObject> originalChildren = original.getChildren();
            Iterator<XmlObject> copyChildren = copy.getChildren();
            while (originalChildren.hasNext() && copyChildren.hasNext()) {
                deepAssertEquals(originalChildren.next(), copyChildren.next(), depth + 1);
            }
            assertFalse("Mis-match in child lists at depth " + depth,
                    originalChildren.hasNext() || copyChildren.hasNext());
        }
    }
}
