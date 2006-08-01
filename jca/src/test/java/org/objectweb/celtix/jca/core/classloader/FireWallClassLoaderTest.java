package org.objectweb.celtix.jca.core.classloader;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

public class FireWallClassLoaderTest extends TestCase {

    public FireWallClassLoaderTest(String name) {
        super(name);
    }
    
    public void testJavaLangStringAlt() throws Exception {
        ClassLoader c = new FireWallClassLoader(ClassLoader.getSystemClassLoader(), new String[] {"java.*"});
        Class c1 = c.loadClass("java.lang.String");
        assertNotNull("Should have returned a class here", c1);
    }
    
    public void testJavaLangStringBlock() throws Exception {
        ClassLoader c = new FireWallClassLoader(ClassLoader.getSystemClassLoader(), 
                                                new String[] {}, 
                                                new String[] {"java.lang.String"});
        try {
            c.loadClass("java.lang.String");
            fail("Expected ClassNotFoundException");
        } catch (ClassNotFoundException ex) {
            assertNotNull("Exception message must not be null.", ex.getMessage());
            assertTrue("not found class must be part of the message. ",
                ex.getMessage().indexOf("java.lang.String") > -1);
        }
    }

    // Check that an internal JDK class can load a class with a prefix that
    // would have
    // been blocked by the firewall
    public void testJDKInternalClass() throws Exception {
        // Just create a temp file we can play with
        File tmpFile = File.createTempFile("FireWall", "Test");
        OutputStream os = new FileOutputStream(tmpFile);
        os.write("This is a test".getBytes());
        os.close();
        tmpFile.deleteOnExit();
        String urlString = tmpFile.toURL().toString();

        ClassLoader c = new FireWallClassLoader(getClass().getClassLoader(), new String[] {"java."});
        Class urlClass = c.loadClass("java.net.URL");
        Constructor urlConstr = urlClass.getConstructor(new Class[] {String.class});
        Object url = urlConstr.newInstance(new Object[] {urlString});
        Method meth = url.getClass().getMethod("openConnection", new Class[] {});
        Object urlConn = meth.invoke(url, new Object[] {});
        System.out.println("Class urlConn: " + urlConn.getClass().getName());

        // Make sure that the internal (sun) class used by the URL connection
        // cannot be found directly through the firewall
        try {
            c.loadClass(urlConn.getClass().getName());
        } catch (ClassNotFoundException cfne) {
            return;
        }
        fail("Should not have found the " + urlConn.getClass().getName() + " class");
    }
   
    public void testSecurityException() {
        try {
            new FireWallClassLoader(ClassLoader.getSystemClassLoader(), new String[] {"hi.there"});
        } catch (SecurityException se) {
            return;
        }
        fail("Constructing a FireWallClassLoader that does not pass through java." 
             + " should cause a SecurityException.");
    }

    public static Test suite() {
        return new TestSuite(FireWallClassLoaderTest.class);
    }

    public static void main(String[] args) throws Exception {
        junit.awtui.TestRunner.main(new String[] {FireWallClassLoaderTest.class.getName()});
    }
}
