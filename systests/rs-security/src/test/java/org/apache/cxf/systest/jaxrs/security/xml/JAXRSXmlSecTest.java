/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.cxf.systest.jaxrs.security.xml;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import org.apache.cxf.Bus;
import org.apache.cxf.bus.spring.SpringBusFactory;
import org.apache.cxf.jaxrs.client.ClientWebApplicationException;
import org.apache.cxf.jaxrs.client.JAXRSClientFactoryBean;
import org.apache.cxf.jaxrs.client.ServerWebApplicationException;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.systest.jaxrs.security.Book;
import org.apache.cxf.testutil.common.AbstractBusClientServerTestBase;
import org.apache.xml.security.encryption.XMLCipher;
import org.junit.BeforeClass;
import org.junit.Test;

public class JAXRSXmlSecTest extends AbstractBusClientServerTestBase {
    public static final String PORT = BookServerXmlSec.PORT;

    @BeforeClass
    public static void startServers() throws Exception {
        assertTrue("server did not launch correctly", 
                   launchServer(BookServerXmlSec.class, true));
    }
    
    @Test
    public void testPostBookWithEnvelopedSig() throws Exception {
        String address = "https://localhost:" + PORT + "/xmlsig/bookstore/books";
        JAXRSClientFactoryBean bean = new JAXRSClientFactoryBean();
        bean.setAddress(address);
        
        SpringBusFactory bf = new SpringBusFactory();
        URL busFile = JAXRSXmlSecTest.class.getResource("client.xml");
        Bus springBus = bf.createBus(busFile.toString());
        bean.setBus(springBus);

        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put("ws-security.callback-handler", 
                       "org.apache.cxf.systest.jaxrs.security.saml.KeystorePasswordCallback");
        properties.put("ws-security.signature.username", "alice");
        properties.put("ws-security.signature.properties", 
                       "org/apache/cxf/systest/jaxrs/security/alice.properties");
        bean.setProperties(properties);
        bean.getOutInterceptors().add(new XmlSigOutInterceptor());
        
        
        WebClient wc = bean.createWebClient();
        try {
            Book book = wc.post(new Book("CXF", 126L), Book.class);
            assertEquals(126L, book.getId());
        } catch (ServerWebApplicationException ex) {
            fail(ex.getMessage());
        } catch (ClientWebApplicationException ex) {
            if (ex.getCause() != null && ex.getCause().getMessage() != null) {
                fail(ex.getCause().getMessage());
            } else {
                fail(ex.getMessage());
            }
        }
        
    }
    
    @Test
    public void testPostEncryptedBook() throws Exception {
        String address = "https://localhost:" + PORT + "/xmlenc/bookstore/books";
        JAXRSClientFactoryBean bean = new JAXRSClientFactoryBean();
        bean.setAddress(address);
        
        SpringBusFactory bf = new SpringBusFactory();
        URL busFile = JAXRSXmlSecTest.class.getResource("client.xml");
        Bus springBus = bf.createBus(busFile.toString());
        bean.setBus(springBus);

        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put("ws-security.callback-handler", 
                       "org.apache.cxf.systest.jaxrs.security.saml.KeystorePasswordCallback");
        properties.put("ws-security.encryption.username", "bob");
        properties.put("ws-security.encryption.properties", 
                       "org/apache/cxf/systest/jaxrs/security/bob.properties");
        bean.setProperties(properties);
        XmlEncOutInterceptor encInterceptor = new XmlEncOutInterceptor();
        encInterceptor.setSymmetricEncAlgorithm(XMLCipher.AES_128);
        bean.getOutInterceptors().add(encInterceptor);
                
        WebClient wc = bean.createWebClient();
        try {
            Book book = wc.post(new Book("CXF", 126L), Book.class);
            assertEquals(126L, book.getId());
        } catch (ServerWebApplicationException ex) {
            fail(ex.getMessage());
        } catch (ClientWebApplicationException ex) {
            if (ex.getCause() != null && ex.getCause().getMessage() != null) {
                fail(ex.getCause().getMessage());
            } else {
                fail(ex.getMessage());
            }
        }
        
    }
    
    @Test
    public void testPostEncryptedSignedBook() throws Exception {
        String address = "https://localhost:" + PORT + "/xmlsec/bookstore/books";
        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put("ws-security.callback-handler", 
                       "org.apache.cxf.systest.jaxrs.security.saml.KeystorePasswordCallback");
        properties.put("ws-security.encryption.username", "bob");
        properties.put("ws-security.encryption.properties", 
                       "org/apache/cxf/systest/jaxrs/security/bob.properties");
        properties.put("ws-security.signature.username", "alice");
        properties.put("ws-security.signature.properties", 
                       "org/apache/cxf/systest/jaxrs/security/alice.properties");
        doTestPostEncryptedSignedBook(address, properties);
        
    }
    
    @Test
    //Encryption properties are shared by encryption and signature handlers
    public void testPostEncryptedSignedBookSharedProps() throws Exception {
        String address = "https://localhost:" + PORT + "/xmlsec2/bookstore/books";
        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put("ws-security.callback-handler", 
                       "org.apache.cxf.systest.jaxrs.security.saml.KeystorePasswordCallback");
        properties.put("ws-security.encryption.username", "bob");
        properties.put("ws-security.encryption.properties", 
                       "org/apache/cxf/systest/jaxrs/security/bob.properties");
        doTestPostEncryptedSignedBook(address, properties);
        
    }
    
    public void doTestPostEncryptedSignedBook(String address, Map<String, Object> properties) 
        throws Exception {
        JAXRSClientFactoryBean bean = new JAXRSClientFactoryBean();
        bean.setAddress(address);
        
        SpringBusFactory bf = new SpringBusFactory();
        URL busFile = JAXRSXmlSecTest.class.getResource("client.xml");
        Bus springBus = bf.createBus(busFile.toString());
        bean.setBus(springBus);

        bean.setProperties(properties);
        bean.getOutInterceptors().add(new XmlSigOutInterceptor());
        XmlEncOutInterceptor encInterceptor = new XmlEncOutInterceptor();
        encInterceptor.setSymmetricEncAlgorithm(XMLCipher.AES_128);
        bean.getOutInterceptors().add(encInterceptor);
        
        
        WebClient wc = bean.createWebClient();
        try {
            Book book = wc.post(new Book("CXF", 126L), Book.class);
            assertEquals(126L, book.getId());
        } catch (ServerWebApplicationException ex) {
            fail(ex.getMessage());
        } catch (ClientWebApplicationException ex) {
            if (ex.getCause() != null && ex.getCause().getMessage() != null) {
                fail(ex.getCause().getMessage());
            } else {
                fail(ex.getMessage());
            }
        }
        
    }
    
    
}