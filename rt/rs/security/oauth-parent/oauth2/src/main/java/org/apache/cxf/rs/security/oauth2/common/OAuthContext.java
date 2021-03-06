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
package org.apache.cxf.rs.security.oauth2.common;

import java.util.Collections;
import java.util.List;


/**
 * Captures the information about the current client request
 * which custom filters may use to further protect the endpoints
 */
public class OAuthContext {

    private UserSubject subject;
    private List<OAuthPermission> permissions;
    private String tokenGrantType;
    
    public OAuthContext(UserSubject subject, 
                        List<OAuthPermission> perms,
                        String tokenGrantType) {
        this.subject = subject;
        this.permissions = perms;
        this.tokenGrantType = tokenGrantType;
    }
   
    /**
     * Gets the {@link UserSubject} representing the end user authorizing the client 
     * at the authorization grant creation time 
     * @return the subject
     */
    public UserSubject getSubject() {
        return subject;
    }
    
    /**
     * Gets the list of the permissions assigned to the current access token
     * @return the permissions
     */
    public List<OAuthPermission> getPermissions() {
        return Collections.unmodifiableList(permissions);
    }

    /**
     * Returns the grant type which was used to obtain the access token
     * the client is using now during the current request
     * @return the grant type
     */
    public String getTokenGrantType() {
        return tokenGrantType;
    }
    

}
