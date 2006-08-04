package org.objectweb.celtix.messaging;

import org.objectweb.celtix.BusException;

/**
 * The ConduitInitiatorManager provides an interface to register and retrieve
 * transport factories.
 */
public interface ConduitInitiatorManager {

    /**
     * Associates a name, often a URI, with a <code>ConduitInitiator</code>
     * when registering with the <code>Bus</code>'s <code>TransportRegistry</code>.
     * @param name A string containing the name used to identify the
     * <code>ConduitInitiator</code>
     * @param factory The <code>ConduitInitiator</code> to be registered.
     * @throws BusException If there is an error registering the conduit initiator.
     */
    void registerConduitInitiator(String name,
        ConduitInitiator factory) throws BusException;

    /**
     * Unregister a <code>ConduitInitiator</code>.
     * @param name A string containing the name of the
     * <code>ConduitInitiator</code>.
     * @throws BusException If there is an error deregistering
     * the conduit initiator.
     */
    void deregisterConduitInitiator(String name)
        throws BusException;
    
    /**
     * Returns the <code>ConduitInitiator</code> registered with the specified name, 
     * loading the appropriate plugin if necessary.
     * 
     * @param name
     * @return the registered <code>ConduitInitiator</code>
     * @throws BusException
     */
    ConduitInitiator getConduitInitiator(String name) throws BusException;
}
