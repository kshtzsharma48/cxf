package org.objectweb.celtix.bus.ws.addressing;



import java.util.logging.Level;
import java.util.logging.Logger;

import javax.wsdl.Port;
import javax.xml.ws.handler.MessageContext;
import static javax.xml.ws.handler.MessageContext.MESSAGE_OUTBOUND_PROPERTY;

import org.objectweb.celtix.common.logging.LogUtils;
import org.objectweb.celtix.ws.addressing.AddressingProperties;
import org.objectweb.celtix.ws.addressing.AttributedURIType;
import org.objectweb.celtix.ws.addressing.EndpointReferenceType;
import org.objectweb.celtix.ws.addressing.ObjectFactory;
import org.objectweb.celtix.ws.addressing.RelatesToType;

import static org.objectweb.celtix.context.ObjectMessageContext.REQUESTOR_ROLE_PROPERTY;
import static org.objectweb.celtix.context.OutputStreamMessageContext.ONEWAY_MESSAGE_TF;
import static org.objectweb.celtix.ws.addressing.JAXWSAConstants.CLIENT_ADDRESSING_PROPERTIES;
import static org.objectweb.celtix.ws.addressing.JAXWSAConstants.CLIENT_ADDRESSING_PROPERTIES_INBOUND;
import static org.objectweb.celtix.ws.addressing.JAXWSAConstants.CLIENT_ADDRESSING_PROPERTIES_OUTBOUND;
import static org.objectweb.celtix.ws.addressing.JAXWSAConstants.SERVER_ADDRESSING_PROPERTIES_INBOUND;
import static org.objectweb.celtix.ws.addressing.JAXWSAConstants.SERVER_ADDRESSING_PROPERTIES_OUTBOUND;


/**
 * Holder for utility methods relating to contexts.
 */
public final class ContextUtils {

    public static final ObjectFactory WSA_OBJECT_FACTORY = new ObjectFactory();

    private static final Logger LOG = LogUtils.getL7dLogger(ContextUtils.class);

    /**
     * Used by client transport to cache To address in the context
     */
    private static final String CLIENT_TO_ADDRESS_PROPERTY = 
        "org.objectweb.celtix.ws.addressing.client.to";

    /**
     * Used by client transport to cache WSDL Port in the context
     */
    private static final String CLIENT_WSDL_PORT_PROPERTY = 
        "org.objectweb.celtix.ws.addressing.client.port";
 
    /**
     * Used by MAPAggregator to cache bad MAP fault name
     */
    private static final String MAP_FAULT_NAME_PROPERTY = 
        "org.objectweb.celtix.ws.addressing.map.fault.name";

    /**
     * Used by MAPAggregator to cache bad MAP fault reason
     */
    private static final String MAP_FAULT_REASON_PROPERTY = 
        "org.objectweb.celtix.ws.addressing.map.fault.reason";
 
   /**
    * Prevents instantiation.
    */
    private ContextUtils() {
    }

   /**
    * Determine if context indicates message is outbound.
    *
    * @param context the current MessageContext
    * @return true iff the message direction is outbound
    */
    public static boolean isOutbound(MessageContext context) {
        Boolean outbound = (Boolean)context.get(MESSAGE_OUTBOUND_PROPERTY);
        return outbound != null && outbound.booleanValue();
    }

   /**
    * Determine if context indicates current messaging role is that of
    * requestor.
    *
    * @param context the current MessageContext
    * @return true iff the current messaging role is that of requestor
    */
    public static boolean isRequestor(MessageContext context) {
        Boolean requestor = (Boolean)context.get(REQUESTOR_ROLE_PROPERTY);
        return requestor != null && requestor.booleanValue();
    }

    /**
     * Determine if context indicates current invocation is oneway.
     *
     * @param context the current MessageContext
     * @return true iff the current invocation is oneway
     */
    public static boolean isOneway(MessageContext context) {
        Boolean oneway = (Boolean)context.get(ONEWAY_MESSAGE_TF);
        return oneway != null && oneway.booleanValue();
    }

    /**
     * Get appropriate context property name for message addressing properties.
     *
     * @param isProviderContext true if the binding provider request context 
     * available to the client application as opposed to the message context 
     * visible to handlers
     * @param isRequestor true iff the current messaging role is that of
     * requestor
     * @param isOutbound true iff the message is outbound
     * @return the property name to use when caching the MAPs in the context
     */
    public static String getMAPProperty(boolean isRequestor, 
                                        boolean isProviderContext,
                                        boolean isOutbound) {

        return isRequestor
                ? isProviderContext
                 ? CLIENT_ADDRESSING_PROPERTIES
                 : isOutbound
                   ? CLIENT_ADDRESSING_PROPERTIES_OUTBOUND
                   : CLIENT_ADDRESSING_PROPERTIES_INBOUND
               : isOutbound
                 ? SERVER_ADDRESSING_PROPERTIES_OUTBOUND
                 : SERVER_ADDRESSING_PROPERTIES_INBOUND;
    }

    /**
     * Store MAPs in the context.
     *
     * @param context the message context
     * @param isOutbound true iff the message is outbound
     */
    public static void storeMAPs(AddressingProperties maps,
                                 MessageContext context,
                                 boolean isOutbound) {
        storeMAPs(maps, context, isOutbound, isRequestor(context), true);
    }

    /**
     * Store MAPs in the context.
     *
     * @param maps the MAPs to store
     * @param context the message context
     * @param isOutbound true iff the message is outbound
     * @param isRequestor true iff the current messaging role is that of
     * requestor
     * @param handler true if HANDLER scope, APPLICATION scope otherwise
     */
    public static void storeMAPs(AddressingProperties maps,
                                 MessageContext context,
                                 boolean isOutbound, 
                                 boolean isRequestor,
                                 boolean handler) {
        if (maps != null) {
            String mapProperty = getMAPProperty(isRequestor, false, isOutbound);
            LOG.log(Level.INFO,
                    "associating MAPs with context property {0}",
                    mapProperty);
            context.put(mapProperty, maps);
            context.setScope(mapProperty, 
                             handler
                             ? MessageContext.Scope.HANDLER
                             : MessageContext.Scope.APPLICATION);
        }
    }

    /**
     * @param context the message context
     * @param isProviderContext true if the binding provider request context
     * available to the client application as opposed to the message context
     * visible to handlers
     * @param isOutbound true iff the message is outbound
     * @return the current addressing properties
     */
    public static AddressingProperties retrieveMAPs(MessageContext context, 
                                                    boolean isProviderContext,
                                                    boolean isOutbound) {
        boolean isRequestor = ContextUtils.isRequestor(context);
        String mapProperty =
            ContextUtils.getMAPProperty(isProviderContext, 
                                        isRequestor,
                                        isOutbound);
        LOG.log(Level.INFO,
                "retrieving MAPs from context property {0}",
                mapProperty);
        AddressingProperties maps =
            (AddressingProperties)context.get(mapProperty);
        if (maps != null) {
            LOG.log(Level.INFO, "current MAPs {0}", maps);
        } else if (!isProviderContext) {
            LOG.warning("MAPS_RETRIEVAL_FAILURE_MSG");
        }
        return maps;
    }

    /**
     * Helper method to get an attributed URI.
     *
     * @param uri the URI
     * @return an AttributedURIType encapsulating the URI
     */
    public static AttributedURIType getAttributedURI(String uri) {
        AttributedURIType attributedURI = 
            WSA_OBJECT_FACTORY.createAttributedURIType();
        attributedURI.setValue(uri);
        return attributedURI;
    }

    /**
     * Helper method to get a RealtesTo instance.
     *
     * @param uri the related URI
     * @return a RelatesToType encapsulating the URI
     */
    public static RelatesToType getRelatesTo(String uri) {
        RelatesToType relatesTo =
            WSA_OBJECT_FACTORY.createRelatesToType();
        relatesTo.setValue(uri);
        return relatesTo;
    }

    /**
     * Helper method to determine if an EPR address is generic (either null,
     * none or anonymous).
     *
     * @param ref the EPR under test
     * @return true iff the address is generic
     */
    public static boolean isGenericAddress(EndpointReferenceType ref) {
        return ref == null 
               || ref.getAddress() == null
               || Names.WSA_ANONYMOUS_ADDRESS.equals(ref.getAddress().getValue())
               || Names.WSA_NONE_ADDRESS.equals(ref.getAddress().getValue());
    }

    /**
     * Retrieve WSDL Port from the context.
     *
     * @param context the message context
     * @returned the retrieved Port
     */
    public static Port retrievePort(MessageContext context) {
        return (Port)context.get(CLIENT_WSDL_PORT_PROPERTY);
    }

    /**
     * Retrieve To EPR from the context.
     *
     * @param context the message context
     * @returned the retrieved EPR
     */
    public static EndpointReferenceType retrieveTo(MessageContext context) {
        return (EndpointReferenceType)context.get(CLIENT_TO_ADDRESS_PROPERTY);
    }

    /**
     * Store bad MAP fault name in the context.
     *
     * @param faultName the fault name to store
     * @param context the message context
     */
    public static void storeMAPFaultName(String faultName, 
                                         MessageContext context) {
        context.put(MAP_FAULT_NAME_PROPERTY, faultName);
        context.setScope(MAP_FAULT_NAME_PROPERTY,
                         MessageContext.Scope.HANDLER);
    }

    /**
     * Retrieve MAP fault name from the context.
     *
     * @param context the message context
     * @returned the retrieved fault name
     */
    public static String retrieveMAPFaultName(MessageContext context) {
        return (String)context.get(MAP_FAULT_NAME_PROPERTY);
    }

    /**
     * Store MAP fault reason in the context.
     *
     * @param reason the fault reason to store
     * @param context the message context
     */
    public static void storeMAPFaultReason(String reason, 
                                           MessageContext context) {
        context.put(MAP_FAULT_REASON_PROPERTY, reason);
        context.setScope(MAP_FAULT_REASON_PROPERTY,
                         MessageContext.Scope.HANDLER);
    }

    /**
     * Retrieve MAP fault reason from the context.
     *
     * @param context the message context
     * @returned the retrieved fault reason
     */
    public static String retrieveMAPFaultReason(MessageContext context) {
        return (String)context.get(MAP_FAULT_REASON_PROPERTY);
    }
}











