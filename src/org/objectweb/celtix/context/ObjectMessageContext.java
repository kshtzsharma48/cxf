package org.objectweb.celtix.context;

import java.lang.reflect.Method;
import javax.xml.ws.handler.MessageContext;

public interface ObjectMessageContext extends MessageContext {

    String MESSAGE_INPUT = "org.objectweb.celtix.input";
    String MESSAGE_PAYLOAD = "org.objectweb.celtix.payload";
    String REQUEST_PROXY = "org.objectweb.celtix.proxy"; 

    Object[] getMessageObjects();
    
    void setMessageObjects(Object ... objects);

    void setMethod(Method method);
    
    Method getMethod();
    
    void setReturn(Object retVal);
    
    Object getReturn();
    
    void setException(Object retVal);
    
    Object getException();
}


