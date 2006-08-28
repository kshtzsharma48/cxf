package org.apache.cxf.endpoint;

import java.util.concurrent.Executor;

import org.apache.cxf.binding.Binding;
import org.apache.cxf.interceptor.Interceptor;
import org.apache.cxf.interceptor.InterceptorProvider;
import org.apache.cxf.service.Service;
import org.apache.cxf.service.model.EndpointInfo;

/**
 * Represents an endpoint that receives messages. 
 *
 */
public interface Endpoint extends InterceptorProvider {

    EndpointInfo getEndpointInfo();
    
    Binding getBinding();
    
    Service getService();

    void setExecutor(Executor executor);
    
    Executor getExecutor();
    
    Interceptor getFaultInterceptor();
}
