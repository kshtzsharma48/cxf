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

package org.apache.cxf.ws.security.wss4j.policyvalidators;

import java.util.Collection;
import java.util.List;

import org.apache.cxf.message.Message;
import org.apache.cxf.ws.policy.AssertionInfo;
import org.apache.cxf.ws.policy.AssertionInfoMap;
import org.apache.cxf.ws.security.policy.SP12Constants;
import org.apache.cxf.ws.security.policy.SPConstants;
import org.apache.cxf.ws.security.policy.model.IssuedToken;
import org.apache.cxf.ws.security.policy.model.KerberosToken;
import org.apache.cxf.ws.security.policy.model.SamlToken;
import org.apache.cxf.ws.security.policy.model.SecurityContextToken;
import org.apache.cxf.ws.security.policy.model.SupportingToken;
import org.apache.cxf.ws.security.policy.model.Token;
import org.apache.cxf.ws.security.policy.model.UsernameToken;
import org.apache.cxf.ws.security.policy.model.X509Token;
import org.apache.ws.security.WSSecurityEngineResult;

/**
 * Validate a SignedEncryptedSupportingToken policy. 
 */
public class SignedEncryptedTokenPolicyValidator extends AbstractSupportingTokenPolicyValidator {
    
    public SignedEncryptedTokenPolicyValidator(
        Message message,
        List<WSSecurityEngineResult> results,
        List<WSSecurityEngineResult> signedResults
    ) {
        super(message, results, signedResults);
    }
    
    public boolean validatePolicy(
        AssertionInfoMap aim
    ) {
        Collection<AssertionInfo> ais = aim.get(SP12Constants.SIGNED_ENCRYPTED_SUPPORTING_TOKENS);
        if (ais == null || ais.isEmpty()) {                       
            return true;
        }

        for (AssertionInfo ai : ais) {
            SupportingToken binding = (SupportingToken)ai.getAssertion();
            if (SPConstants.SupportTokenType.SUPPORTING_TOKEN_SIGNED_ENCRYPTED != binding.getTokenType()) {
                continue;
            }
            ai.setAsserted(true);
            setSigned(true);
            setEncrypted(true);

            List<Token> tokens = binding.getTokens();
            for (Token token : tokens) {
                if (!isTokenRequired(token, message)) {
                    continue;
                }
                
                boolean processingFailed = false;
                if (token instanceof UsernameToken) {
                    if (!processUsernameTokens()) {
                        processingFailed = true;
                    }
                } else if (token instanceof KerberosToken) {
                    if (!processKerberosTokens()) {
                        processingFailed = true;
                    }
                } else if (token instanceof X509Token) {
                    if (!processX509Tokens()) {
                        processingFailed = true;
                    }
                } else if (token instanceof SecurityContextToken) {
                    if (!processSCTokens()) {
                        processingFailed = true;
                    }
                } else if (token instanceof SamlToken) {
                    if (!processSAMLTokens()) {
                        processingFailed = true;
                    }
                } else if (!(token instanceof IssuedToken)) {
                    processingFailed = true;
                }
                
                if (processingFailed) {
                    ai.setNotAsserted(
                        "The received token does not match the signed encrypted supporting token requirement"
                    );
                    return false;
                }
            }
        }
        
        return true;
    }
    
}