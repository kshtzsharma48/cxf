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
package org.apache.cxf.rs.security.oauth2.filters;

import java.security.Principal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;

import org.apache.cxf.common.logging.LogUtils;
import org.apache.cxf.common.security.SimplePrincipal;
import org.apache.cxf.jaxrs.ext.MessageContext;
import org.apache.cxf.jaxrs.ext.RequestHandler;
import org.apache.cxf.jaxrs.model.ClassResourceInfo;
import org.apache.cxf.message.Message;
import org.apache.cxf.rs.security.oauth2.common.OAuthContext;
import org.apache.cxf.rs.security.oauth2.common.OAuthPermission;
import org.apache.cxf.rs.security.oauth2.common.ServerAccessToken;
import org.apache.cxf.rs.security.oauth2.common.UserSubject;
import org.apache.cxf.rs.security.oauth2.provider.AccessTokenValidator;
import org.apache.cxf.rs.security.oauth2.provider.OAuthDataProvider;
import org.apache.cxf.rs.security.oauth2.provider.OAuthServiceException;
import org.apache.cxf.rs.security.oauth2.utils.AuthorizationUtils;
import org.apache.cxf.rs.security.oauth2.utils.OAuthConstants;
import org.apache.cxf.rs.security.oauth2.utils.OAuthUtils;
import org.apache.cxf.security.SecurityContext;

/**
 * JAX-RS OAuth filter which can be used to protect end user endpoints
 */
@Provider
public class OAuthRequestFilter implements RequestHandler {
    private static final Logger LOG = LogUtils.getL7dLogger(OAuthRequestFilter.class);
    
    private static final String DEFAULT_AUTH_SCHEME = OAuthConstants.BEARER_AUTHORIZATION_SCHEME; 
    
    private MessageContext mc;

    private List<AccessTokenValidator> tokenHandlers = Collections.emptyList();
    private Set<String> supportedSchemes = new HashSet<String>();
    private boolean useUserSubject;
    private OAuthDataProvider dataProvider;
    
    public void setGrantHandlers(List<AccessTokenValidator> handlers) {
        tokenHandlers = handlers;
        for (AccessTokenValidator handler : handlers) {
            supportedSchemes.addAll(handler.getSupportedAuthorizationSchemes());
        }
    }
    
    public Response handleRequest(Message m, ClassResourceInfo resourceClass) {
        ServerAccessToken accessToken = getAccessToken(); 
        
        List<OAuthPermission> permissions = accessToken.getScopes();
        List<OAuthPermission> matchingPermissions = new ArrayList<OAuthPermission>();
        
        HttpServletRequest req = mc.getHttpServletRequest();
        for (OAuthPermission perm : permissions) {
            boolean uriOK = checkRequestURI(req, perm.getUris());
            boolean verbOK = checkHttpVerb(req, perm.getHttpVerbs());
            if (uriOK && verbOK) {
                matchingPermissions.add(perm);
            }
        }
        
        if (permissions.size() > 0 && matchingPermissions.isEmpty()) {
            String message = "Client has no valid permissions";
            LOG.warning(message);
            throw new WebApplicationException(403);
        }
      
        OAuthInfo info = new OAuthInfo(accessToken, matchingPermissions);
        SecurityContext sc = createSecurityContext(req, info);
        m.setContent(SecurityContext.class, sc);
        m.setContent(OAuthContext.class, createOAuthContext(info));
        
        return null;
    }

    protected boolean checkHttpVerb(HttpServletRequest req, List<String> verbs) {
        if (!verbs.isEmpty() 
            && !verbs.contains(req.getMethod())) {
            String message = "Invalid http verb";
            LOG.fine(message);
            return false;
        }
        return true;
    }
    
    protected boolean checkRequestURI(HttpServletRequest request, List<String> uris) {
        
        if (uris.isEmpty()) {
            return true;
        }
        String servletPath = request.getPathInfo();
        boolean foundValidScope = false;
        for (String uri : uris) {
            if (OAuthUtils.checkRequestURI(servletPath, uri)) {
                foundValidScope = true;
                break;
            }
        }
        if (!foundValidScope) {
            String message = "Invalid request URI";
            LOG.fine(message);
        }
        return foundValidScope;
    }
    
    public void setDataProvider(OAuthDataProvider provider) {
        dataProvider = provider;
    }
    
    public void setUseUserSubject(boolean useUserSubject) {
        this.useUserSubject = useUserSubject;
    }
    
    @Context
    public void setMessageContext(MessageContext context) {
        this.mc = context;
    }

    protected AccessTokenValidator findTokenHandler(String authScheme) {
        for (AccessTokenValidator handler : tokenHandlers) {
            if (handler.getSupportedAuthorizationSchemes().contains(authScheme)) {
                return handler;
            }
        }
        return null;        
    }
    
    protected ServerAccessToken getAccessToken() {
        ServerAccessToken accessToken = null;
        if (dataProvider == null && tokenHandlers.isEmpty()) {
            throw new WebApplicationException(500);
        }
        
        String[] authParts = AuthorizationUtils.getAuthorizationParts(mc, supportedSchemes);
        String authScheme = authParts[0];
        String authSchemeData = authParts[1];
        
        AccessTokenValidator handler = findTokenHandler(authScheme);
        if (handler != null) {
            try {
                accessToken = handler.getAccessToken(authSchemeData);
            } catch (OAuthServiceException ex) {
                AuthorizationUtils.throwAuthorizationFailure(
                    Collections.singleton(authScheme));
            }
        }
        if (accessToken == null && authScheme.equals(DEFAULT_AUTH_SCHEME)) {
            try {
                accessToken = dataProvider.getAccessToken(authSchemeData);
            } catch (OAuthServiceException ex) {
                AuthorizationUtils.throwAuthorizationFailure(
                    Collections.singleton(authScheme));
            }
        }
        if (accessToken == null) {
            AuthorizationUtils.throwAuthorizationFailure(supportedSchemes);
        }
        if (OAuthUtils.isExpired(accessToken.getIssuedAt(), accessToken.getLifetime())) {
            dataProvider.removeAccessToken(accessToken);
            AuthorizationUtils.throwAuthorizationFailure(supportedSchemes);
        }
        return accessToken;
    }
    
    protected SecurityContext createSecurityContext(HttpServletRequest request, 
                                                    final OAuthInfo info) {
        UserSubject subject = info.getToken().getSubject();

        final UserSubject theSubject = subject;
        return new SecurityContext() {

            public Principal getUserPrincipal() {
                String login = OAuthRequestFilter.this.useUserSubject 
                    ? (theSubject != null ? theSubject.getLogin() : null)
                    : info.getToken().getClient().getLoginName();  
                return new SimplePrincipal(login);
            }

            public boolean isUserInRole(String role) {
                List<String> roles = null;
                if (OAuthRequestFilter.this.useUserSubject && theSubject != null) {
                    roles = theSubject.getRoles();    
                } else {
                    roles = info.getRoles();
                }
                return roles == null ? false : roles.contains(role);
            }
             
        };
    }
    
    protected OAuthContext createOAuthContext(OAuthInfo info) {
        UserSubject subject = null;
        if (info.getToken() != null) {
            subject = info.getToken().getSubject();
        }
        return new OAuthContext(subject, info.getMatchedPermissions());
    }
    
    private static class OAuthInfo {
        private ServerAccessToken token;
        private List<OAuthPermission> permissions;
        public OAuthInfo(ServerAccessToken token, 
                         List<OAuthPermission> matchedPermissions) {
            this.token = token;
            this.permissions = matchedPermissions;
        }
        public ServerAccessToken getToken() {
            return token;
        }
        
        public List<String> getRoles() {
            List<String> authorities = new ArrayList<String>();
            for (OAuthPermission permission : permissions) {
                authorities.addAll(permission.getRoles());
            }
            return authorities;
        }
        
        public List<OAuthPermission> getMatchedPermissions() {
            return permissions;
        }
        
            
    }
}
