package org.objectweb.celtix.service.model;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.xml.namespace.QName;

/**
 * 
 */
public class OperationInfo extends AbstractPropertiesHolder {
    
    final InterfaceInfo intf;
    QName opName;
    String inName;
    MessageInfo inputMessage;
    String outName;
    MessageInfo outputMessage;
    Map<QName, FaultInfo> faults;
    
    OperationInfo unwrappedOperation;
    
    OperationInfo(InterfaceInfo it, QName n) { 
        intf = it;
        setName(n);
    }
    OperationInfo(OperationInfo op) {
        intf = op.getInterface();
        setName(op.getName());
    }
    
    /**
     * Returns the name of the Operation.
     * @return the name of the Operation
     */
    public QName getName() {
        return opName;
    }
    /**
     * Sets the name of the operation.
     * @param name the new name of the operation
     */
    public final void setName(QName name) {
        if (name == null) {
            throw new NullPointerException("Name cannot be null.");
        }        
        opName = name;
    }
    public InterfaceInfo getInterface() {
        return intf;
    }

    
    public MessageInfo createMessage(QName nm) {
        return new MessageInfo(this, nm);
    }

    public MessageInfo getOutput() {
        return outputMessage;
    }
    public String getOutputName() {
        return outName;
    }
    public void setOutput(String nm, MessageInfo out) {
        outName = nm;
        outputMessage = out;
    }    
    public boolean hasOutput() {
        return outputMessage != null;
    }

    public MessageInfo getInput() {
        return inputMessage;
    }
    public String getInputName() {
        return inName;
    }
    public void setInput(String nm, MessageInfo in) {
        inName = nm;
        inputMessage = in;
    }
    public boolean hasInput() {
        return inputMessage != null;
    }
    
    public boolean isOneWay() {
        return inputMessage != null && outputMessage == null;
    }
    
    public boolean isUnwrappedCapable() {
        return unwrappedOperation != null;
    }
    public OperationInfo getUnwrappedOperation() {
        return unwrappedOperation;
    }
    public void setUnwrappedOperation(OperationInfo op) {
        unwrappedOperation = op;
    }
    public boolean isUnwrapped() {
        return false;
    }
    
    
    /**
     * Adds an fault to this operation.
     *
     * @param name the fault name.
     */
    public FaultInfo addFault(QName name, QName message) {
        if (name == null) {
            throw new NullPointerException("Name cannot be null.");
        } 
        if (faults != null && faults.containsKey(name)) {
            throw new IllegalArgumentException("A fault with name [" + name
                                               + "] already exists in this operation");
        }
        FaultInfo fault = new FaultInfo(name, message, this);
        addFault(fault);
        return fault;
    }

    /**
     * Adds a fault to this operation.
     *
     * @param fault the fault.
     */
    synchronized void addFault(FaultInfo fault) {
        if (faults == null) { 
            faults = new ConcurrentHashMap<QName, FaultInfo>(4);
        }
        faults.put(fault.getFaultName(), fault);
    }

    /**
     * Removes a fault from this operation.
     *
     * @param name the qualified fault name.
     */
    public void removeFault(QName name) {
        if (faults != null) {
            faults.remove(name);
        }
    }

    /**
     * Returns the fault with the given name, if found.
     *
     * @param name the name.
     * @return the fault; or <code>null</code> if not found.
     */
    public FaultInfo getFault(QName name) {
        if (faults != null) {
            return faults.get(name);
        }
        return null;
    }

    /**
     * Returns all faults for this operation.
     *
     * @return all faults.
     */
    public Collection<FaultInfo> getFaults() {
        if (faults == null) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableCollection(faults.values());
    }
    
}
