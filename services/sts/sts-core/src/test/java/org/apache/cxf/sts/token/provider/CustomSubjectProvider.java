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
package org.apache.cxf.sts.token.provider;

import java.security.Principal;

import org.w3c.dom.Document;

import org.apache.cxf.sts.STSConstants;
import org.apache.cxf.sts.request.KeyRequirements;
import org.apache.cxf.sts.request.TokenRequirements;
import org.apache.ws.security.WSConstants;
import org.apache.ws.security.saml.ext.bean.SubjectBean;
import org.apache.ws.security.saml.ext.builder.SAML1Constants;
import org.apache.ws.security.saml.ext.builder.SAML2Constants;

/**
 * A test implementation of SubjectProvider.
 */
public class CustomSubjectProvider implements SubjectProvider {
    
    private String subjectNameQualifier = "http://cxf.apache.org/sts/custom";
    
    /**
     * Get a SubjectBean object.
     */
    public SubjectBean getSubject(TokenProviderParameters providerParameters, Document doc, byte[] secret) {
        TokenRequirements tokenRequirements = providerParameters.getTokenRequirements();
        KeyRequirements keyRequirements = providerParameters.getKeyRequirements();

        String tokenType = tokenRequirements.getTokenType();
        String keyType = keyRequirements.getKeyType();
        String confirmationMethod = getSubjectConfirmationMethod(tokenType, keyType);
        
        Principal principal = providerParameters.getPrincipal();
        SubjectBean subjectBean = 
            new SubjectBean(principal.getName(), subjectNameQualifier, confirmationMethod);

        return subjectBean;
    }
        
    /**
     * Get the SubjectConfirmation method given a tokenType and keyType
     */
    private String getSubjectConfirmationMethod(String tokenType, String keyType) {
        if (WSConstants.WSS_SAML2_TOKEN_TYPE.equals(tokenType)
            || WSConstants.SAML2_NS.equals(tokenType)) {
            if (STSConstants.SYMMETRIC_KEY_KEYTYPE.equals(keyType) 
                || STSConstants.PUBLIC_KEY_KEYTYPE.equals(keyType)) {
                return SAML2Constants.CONF_HOLDER_KEY;
            } else {
                return SAML2Constants.CONF_BEARER;
            }
        } else {
            if (STSConstants.SYMMETRIC_KEY_KEYTYPE.equals(keyType) 
                || STSConstants.PUBLIC_KEY_KEYTYPE.equals(keyType)) {
                return SAML1Constants.CONF_HOLDER_KEY;
            } else {
                return SAML1Constants.CONF_BEARER;
            }
        }
    }


}
