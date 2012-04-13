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
package org.apache.cxf.systest.sts.batch;

import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.cxf.Bus;
import org.apache.cxf.bus.spring.SpringBusFactory;
import org.apache.cxf.systest.sts.common.SecurityTestUtil;
import org.apache.cxf.testutil.common.AbstractBusClientServerTestBase;
import org.apache.cxf.ws.security.SecurityConstants;
import org.apache.cxf.ws.security.tokenstore.SecurityToken;
import org.apache.cxf.ws.security.trust.STSUtils;
import org.junit.BeforeClass;
import org.opensaml.common.xml.SAMLConstants;

/**
 * In this test case, a CXF client requests a number of SAML Tokens from an STS using batch processing.
 * It uses a simple STSClient implementation to request both a SAML 1.1 and 2.0 token at the same time.
 * Batch validation is also tested.
 */
public class SAMLBatchUnitTest extends AbstractBusClientServerTestBase {
    
    static final String STSPORT = allocatePort(STSServer.class);
    
    @BeforeClass
    public static void startServers() throws Exception {
        assertTrue(
                   "Server failed to launch",
                   // run the server in the same process
                   // set this to false to fork
                   launchServer(STSServer.class, true)
        );
    }
    
    @org.junit.AfterClass
    public static void cleanup() {
        SecurityTestUtil.cleanup();
    }

    @org.junit.Test
    public void testBatchSAMLTokens() throws Exception {
        SpringBusFactory bf = new SpringBusFactory();
        URL busFile = SAMLBatchUnitTest.class.getResource("cxf-client-unit.xml");

        Bus bus = bf.createBus(busFile.toString());
        SpringBusFactory.setDefaultBus(bus);
        SpringBusFactory.setThreadDefaultBus(bus);
        
        String wsdlLocation = 
            "https://localhost:" + STSPORT + "/SecurityTokenService/Transport?wsdl";
        
        List<BatchRequest> requestList = new ArrayList<BatchRequest>();
        BatchRequest request = new BatchRequest();
        request.setAppliesTo("https://localhost:8081/doubleit/services/doubleittransportsaml1");
        request.setTokenType("http://docs.oasis-open.org/wss/oasis-wss-saml-token-profile-1.1#SAMLV1.1");
        request.setKeyType("http://docs.oasis-open.org/ws-sx/ws-trust/200512/Bearer");
        requestList.add(request);
        
        request = new BatchRequest();
        request.setAppliesTo("https://localhost:8081/doubleit/services/doubleittransportsaml2");
        request.setTokenType("http://docs.oasis-open.org/wss/oasis-wss-saml-token-profile-1.1#SAMLV2.0");
        request.setKeyType("http://docs.oasis-open.org/ws-sx/ws-trust/200512/Bearer");
        requestList.add(request);
        
        String action = "http://docs.oasis-open.org/ws-sx/ws-trust/200512/BatchIssue";
        String requestType = "http://docs.oasis-open.org/ws-sx/ws-trust/200512/BatchIssue";
        String port = "{http://docs.oasis-open.org/ws-sx/ws-trust/200512/}Transport_Port";
        
        // Request the token
        List<SecurityToken> tokens = 
            requestSecurityTokens(bus, wsdlLocation, requestList, action, requestType, port);
        assertTrue(tokens != null && tokens.size() == 2);
        
        assertTrue(tokens.get(0).getToken().getLocalName().equals("Assertion"));
        assertTrue(tokens.get(0).getToken().getNamespaceURI().equals(SAMLConstants.SAML1_NS));
        assertTrue(tokens.get(1).getToken().getLocalName().equals("Assertion"));
        assertTrue(tokens.get(1).getToken().getNamespaceURI().equals(SAMLConstants.SAML20_NS));
        
        // Now validate the tokens
        requestList.get(0).setValidateTarget(tokens.get(0).getToken());
        requestList.get(0).setTokenType(STSUtils.WST_NS_05_12 + "/RSTR/Status");
        requestList.get(1).setValidateTarget(tokens.get(1).getToken());
        requestList.get(1).setTokenType(STSUtils.WST_NS_05_12 + "/RSTR/Status");
        action = "http://docs.oasis-open.org/ws-sx/ws-trust/200512/BatchValidate";
        requestType = "http://docs.oasis-open.org/ws-sx/ws-trust/200512/BatchValidate";
        port = "{http://docs.oasis-open.org/ws-sx/ws-trust/200512/}Transport_Port2";
        
        validateSecurityTokens(bus, wsdlLocation, requestList, action, requestType, port);
    }
    
    
    private List<SecurityToken> requestSecurityTokens(
        Bus bus, String wsdlLocation, List<BatchRequest> requestList, String action, String requestType,
        String port
    ) throws Exception {
        SimpleBatchSTSClient stsClient = new SimpleBatchSTSClient(bus);
        stsClient.setWsdlLocation(wsdlLocation);
        stsClient.setServiceName("{http://docs.oasis-open.org/ws-sx/ws-trust/200512/}SecurityTokenService");
        stsClient.setEndpointName(port);

        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put(SecurityConstants.USERNAME, "alice");
        properties.put(
            SecurityConstants.CALLBACK_HANDLER, 
            "org.apache.cxf.systest.sts.common.CommonCallbackHandler"
        );
        properties.put(SecurityConstants.STS_TOKEN_PROPERTIES, "serviceKeystore.properties");
        
        stsClient.setEnableLifetime(true);

        stsClient.setProperties(properties);
        stsClient.setAddressingNamespace("http://www.w3.org/2005/08/addressing");

        return stsClient.requestBatchSecurityTokens(requestList, action, requestType);
    }
    
    private List<SecurityToken> validateSecurityTokens(
        Bus bus, String wsdlLocation, List<BatchRequest> requestList, String action, String requestType,
        String port
    ) throws Exception {
        SimpleBatchSTSClient stsClient = new SimpleBatchSTSClient(bus);
        stsClient.setWsdlLocation(wsdlLocation);
        stsClient.setServiceName("{http://docs.oasis-open.org/ws-sx/ws-trust/200512/}SecurityTokenService");
        stsClient.setEndpointName(port);

        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put(SecurityConstants.USERNAME, "alice");
        properties.put(
            SecurityConstants.CALLBACK_HANDLER, 
            "org.apache.cxf.systest.sts.common.CommonCallbackHandler"
        );
        properties.put(SecurityConstants.STS_TOKEN_PROPERTIES, "serviceKeystore.properties");

        stsClient.setProperties(properties);
        stsClient.setAddressingNamespace("http://www.w3.org/2005/08/addressing");

        return stsClient.validateBatchSecurityTokens(requestList, action, requestType);
    }


}
