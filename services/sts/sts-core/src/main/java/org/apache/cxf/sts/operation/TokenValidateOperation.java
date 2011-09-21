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

package org.apache.cxf.sts.operation;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.bind.JAXBElement;
import javax.xml.ws.WebServiceContext;

import org.apache.cxf.common.logging.LogUtils;
import org.apache.cxf.sts.QNameConstants;
import org.apache.cxf.sts.STSConstants;
import org.apache.cxf.sts.request.KeyRequirements;
import org.apache.cxf.sts.request.ReceivedToken;
import org.apache.cxf.sts.request.RequestParser;
import org.apache.cxf.sts.request.TokenRequirements;
import org.apache.cxf.sts.token.provider.TokenProvider;
import org.apache.cxf.sts.token.provider.TokenProviderParameters;
import org.apache.cxf.sts.token.provider.TokenProviderResponse;
import org.apache.cxf.sts.token.provider.TokenReference;
import org.apache.cxf.sts.token.validator.TokenValidator;
import org.apache.cxf.sts.token.validator.TokenValidatorParameters;
import org.apache.cxf.sts.token.validator.TokenValidatorResponse;
import org.apache.cxf.ws.security.sts.provider.STSException;
import org.apache.cxf.ws.security.sts.provider.model.LifetimeType;
import org.apache.cxf.ws.security.sts.provider.model.RequestSecurityTokenResponseType;
import org.apache.cxf.ws.security.sts.provider.model.RequestSecurityTokenType;
import org.apache.cxf.ws.security.sts.provider.model.RequestedReferenceType;
import org.apache.cxf.ws.security.sts.provider.model.RequestedSecurityTokenType;
import org.apache.cxf.ws.security.sts.provider.model.StatusType;
import org.apache.cxf.ws.security.sts.provider.operation.ValidateOperation;
import org.apache.ws.security.WSSecurityException;

/**
 * An implementation of the ValidateOperation interface.
 */
public class TokenValidateOperation extends AbstractOperation implements ValidateOperation {

    private static final Logger LOG = LogUtils.getL7dLogger(TokenValidateOperation.class);

    private List<TokenValidator> tokenValidators = new ArrayList<TokenValidator>();
    
    public void setTokenValidators(List<TokenValidator> tokenValidators) {
        this.tokenValidators = tokenValidators;
    }
    
    public List<TokenValidator> getTokenValidators() {
        return tokenValidators;
    }
    
    public RequestSecurityTokenResponseType validate(
        RequestSecurityTokenType request, 
        WebServiceContext context
    ) {
        RequestParser requestParser = parseRequest(request, context);
        
        KeyRequirements keyRequirements = requestParser.getKeyRequirements();
        TokenRequirements tokenRequirements = requestParser.getTokenRequirements();
        
        ReceivedToken validateTarget = tokenRequirements.getValidateTarget();
        if (validateTarget == null || validateTarget.getToken() == null) {
            throw new STSException("No element presented for validation", STSException.INVALID_REQUEST);
        }
        if (tokenRequirements.getTokenType() == null) {
            tokenRequirements.setTokenType(STSConstants.STATUS);
            LOG.fine(
                "Received TokenType is null, falling back to default token type: " 
                + STSConstants.STATUS
            );
        }
        
        TokenValidatorParameters validatorParameters = new TokenValidatorParameters();
        validatorParameters.setStsProperties(stsProperties);
        validatorParameters.setPrincipal(context.getUserPrincipal());
        validatorParameters.setWebServiceContext(context);
        validatorParameters.setCache(getCache());
        
        validatorParameters.setKeyRequirements(keyRequirements);
        validatorParameters.setTokenRequirements(tokenRequirements);
        
        //
        // Validate token
        //
        TokenValidatorResponse tokenResponse = null;
        for (TokenValidator tokenValidator : tokenValidators) {
            if (tokenValidator.canHandleToken(validateTarget)) {
                try {
                    tokenResponse = tokenValidator.validateToken(validatorParameters);
                } catch (RuntimeException ex) {
                    LOG.log(Level.WARNING, "", ex);
                    tokenResponse = new TokenValidatorResponse();
                    tokenResponse.setValid(false);
                }
                break;
            }
        }
        if (tokenResponse == null) {
            LOG.fine("No Token Validator has been found that can handle this token");
            tokenResponse = new TokenValidatorResponse();
            tokenResponse.setValid(false);
        }
        
        //
        // Create a new token (if requested)
        //
        TokenProviderResponse tokenProviderResponse = null;
        String tokenType = tokenRequirements.getTokenType();
        if (tokenResponse.isValid() && !STSConstants.STATUS.equals(tokenType)) {
            TokenProviderParameters providerParameters = 
                 createTokenProviderParameters(requestParser, context);
            Principal responsePrincipal = tokenResponse.getPrincipal();
            if (responsePrincipal != null) {
                providerParameters.setPrincipal(responsePrincipal);
            }
            Map<String, Object> additionalProperties = tokenResponse.getAdditionalProperties();
            if (additionalProperties != null) {
                providerParameters.setAdditionalProperties(additionalProperties);
            }
            for (TokenProvider tokenProvider : tokenProviders) {
                if (tokenProvider.canHandleToken(tokenType)) {
                    try {
                        tokenProviderResponse = tokenProvider.createToken(providerParameters);
                    } catch (STSException ex) {
                        LOG.log(Level.WARNING, "", ex);
                        throw ex;
                    } catch (RuntimeException ex) {
                        LOG.log(Level.WARNING, "", ex);
                        throw new STSException(
                            "Error in providing a token", ex, STSException.REQUEST_FAILED
                        );
                    }
                    break;
                }
            }
            if (tokenProviderResponse == null || tokenProviderResponse.getToken() == null) {
                LOG.fine("No Token Provider has been found that can handle this token");
                throw new STSException(
                    "No token provider found for requested token type: " + tokenType, 
                    STSException.REQUEST_FAILED
                );
            }
        }
        
        // prepare response
        try {
            return createResponse(tokenResponse, tokenProviderResponse, tokenRequirements);
        } catch (Throwable ex) {
            LOG.log(Level.WARNING, "", ex);
            throw new STSException("Error in creating the response", ex, STSException.REQUEST_FAILED);
        }
    }
    
    private RequestSecurityTokenResponseType createResponse(
        TokenValidatorResponse tokenResponse,
        TokenProviderResponse tokenProviderResponse,
        TokenRequirements tokenRequirements
    ) throws WSSecurityException {
        RequestSecurityTokenResponseType response = 
            QNameConstants.WS_TRUST_FACTORY.createRequestSecurityTokenResponseType();

        String context = tokenRequirements.getContext();
        if (context != null) {
            response.setContext(context);
        }
        
        // TokenType
        boolean valid = tokenResponse.isValid();
        String tokenType = tokenRequirements.getTokenType();
        if (valid || STSConstants.STATUS.equals(tokenType)) {
            JAXBElement<String> jaxbTokenType = 
                QNameConstants.WS_TRUST_FACTORY.createTokenType(tokenType);
            response.getAny().add(jaxbTokenType);
        }
        
        // Status
        StatusType statusType = QNameConstants.WS_TRUST_FACTORY.createStatusType();
        if (valid) {
            statusType.setCode(STSConstants.VALID_CODE);
            statusType.setReason(STSConstants.VALID_REASON);
        } else {
            statusType.setCode(STSConstants.INVALID_CODE);
            statusType.setReason(STSConstants.INVALID_REASON);
        }
        JAXBElement<StatusType> status = QNameConstants.WS_TRUST_FACTORY.createStatus(statusType);
        response.getAny().add(status);
        
        // RequestedSecurityToken
        if (valid && !STSConstants.STATUS.equals(tokenType) && tokenProviderResponse != null 
            && tokenProviderResponse.getToken() != null) {
            RequestedSecurityTokenType requestedTokenType = 
                QNameConstants.WS_TRUST_FACTORY.createRequestedSecurityTokenType();
            JAXBElement<RequestedSecurityTokenType> requestedToken = 
                QNameConstants.WS_TRUST_FACTORY.createRequestedSecurityToken(requestedTokenType);
            requestedTokenType.setAny(tokenProviderResponse.getToken());
            response.getAny().add(requestedToken);
            
            // Lifetime
            LifetimeType lifetime = createLifetime(tokenProviderResponse.getLifetime());
            JAXBElement<LifetimeType> lifetimeType =
                QNameConstants.WS_TRUST_FACTORY.createLifetime(lifetime);
            response.getAny().add(lifetimeType);
            
            if (returnReferences) {
                // RequestedAttachedReference
                TokenReference attachedReference = tokenProviderResponse.getAttachedReference();
                RequestedReferenceType requestedAttachedReferenceType = null;
                if (attachedReference != null) {
                    requestedAttachedReferenceType = createRequestedReference(attachedReference, true);
                } else {
                    requestedAttachedReferenceType = 
                        createRequestedReference(
                            tokenProviderResponse.getTokenId(), tokenRequirements.getTokenType(), true
                        );
                }
    
                JAXBElement<RequestedReferenceType> requestedAttachedReference = 
                    QNameConstants.WS_TRUST_FACTORY.createRequestedAttachedReference(
                        requestedAttachedReferenceType
                    );
                response.getAny().add(requestedAttachedReference);
    
                // RequestedUnattachedReference
                TokenReference unAttachedReference = tokenProviderResponse.getUnAttachedReference();
                RequestedReferenceType requestedUnattachedReferenceType = null;
                if (unAttachedReference != null) {
                    requestedUnattachedReferenceType = 
                        createRequestedReference(unAttachedReference, false);
                } else {
                    requestedUnattachedReferenceType = 
                        createRequestedReference(
                            tokenProviderResponse.getTokenId(), tokenRequirements.getTokenType(), false
                        );
                }
                
                JAXBElement<RequestedReferenceType> requestedUnattachedReference = 
                    QNameConstants.WS_TRUST_FACTORY.createRequestedUnattachedReference(
                        requestedUnattachedReferenceType
                    );
                response.getAny().add(requestedUnattachedReference);
            }
        }
        
        return response;
    }
    
    
}