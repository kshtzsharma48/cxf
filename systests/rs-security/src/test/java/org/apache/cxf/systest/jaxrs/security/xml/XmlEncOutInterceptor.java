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
package org.apache.cxf.systest.jaxrs.security.xml;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.annotation.Annotation;
import java.net.URL;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.dom.DOMSource;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;

import org.apache.cxf.Bus;
import org.apache.cxf.common.classloader.ClassLoaderUtils;
import org.apache.cxf.common.logging.LogUtils;
import org.apache.cxf.common.util.Base64Utility;
import org.apache.cxf.common.util.StringUtils;
import org.apache.cxf.endpoint.Endpoint;
import org.apache.cxf.helpers.CastUtils;
import org.apache.cxf.helpers.DOMUtils;
import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.jaxrs.provider.JAXBElementProvider;
import org.apache.cxf.jaxrs.provider.ProviderFactory;
import org.apache.cxf.message.Message;
import org.apache.cxf.message.MessageContentsList;
import org.apache.cxf.phase.AbstractPhaseInterceptor;
import org.apache.cxf.phase.Phase;
import org.apache.cxf.resource.ResourceManager;
import org.apache.cxf.service.model.EndpointInfo;
import org.apache.cxf.staxutils.W3CDOMStreamWriter;
import org.apache.cxf.ws.security.SecurityConstants;
import org.apache.ws.security.WSConstants;
import org.apache.ws.security.WSSConfig;
import org.apache.ws.security.WSSecurityException;
import org.apache.ws.security.components.crypto.Crypto;
import org.apache.ws.security.components.crypto.CryptoFactory;
import org.apache.ws.security.components.crypto.CryptoType;
import org.apache.ws.security.message.token.DOMX509Data;
import org.apache.ws.security.message.token.DOMX509IssuerSerial;
import org.apache.ws.security.util.Base64;
import org.apache.ws.security.util.UUIDGenerator;
import org.apache.ws.security.util.WSSecurityUtil;
import org.apache.xml.security.algorithms.JCEMapper;
import org.apache.xml.security.encryption.XMLCipher;
import org.apache.xml.security.encryption.XMLEncryptionException;

public class XmlEncOutInterceptor extends AbstractPhaseInterceptor<Message> {
    private static final Logger LOG = 
        LogUtils.getL7dLogger(XmlEncOutInterceptor.class);
    private static final String CRYPTO_CACHE = "ws-security.crypto.cache";
    
    static {
        WSSConfig.init();
    }
    
    private boolean encryptSymmetricKey = true;
    private SecretKey symmetricKey;
    private String keyEncAlgo = XMLCipher.RSA_OAEP; 
    private String symEncAlgo = XMLCipher.AES_256;
    
    public XmlEncOutInterceptor() {
        super(Phase.WRITE);
        addAfter(XmlSigOutInterceptor.class.getName());
    } 

    public void setSymmetricEncAlgorithm(String algo) {
        symEncAlgo = algo;
    }
    
    public void setKeyEncAlgorithm(String algo) {
        keyEncAlgo = algo;
    }
    
    public void handleMessage(Message message) throws Fault {
        try {
            Object body = getRequestBody(message);
            if (body == null) {
                return;
            }
            Document doc = getDomDocument(body, message);
            if (doc == null) {
                return;
            }
 
            Document encryptedDataDoc = encryptDocument(message, doc);
            message.setContent(List.class, 
                new MessageContentsList(new DOMSource(encryptedDataDoc)));
        } catch (Exception ex) {
            StringWriter sw = new StringWriter();
            ex.printStackTrace(new PrintWriter(sw));
            LOG.warning(sw.toString());
            throw new Fault(new RuntimeException(ex.getMessage() + ", stacktrace: " + sw.toString()));
        }
    }
    
    // at the moment all the doc gets encrypted
    private Document encryptDocument(Message message, Document payloadDoc) 
        throws Exception {
        
        byte[] secretKey = getSymmetricKey();

        Document encryptedDataDoc = DOMUtils.createDocument();
        Element encryptedDataElement = createEncryptedDataElement(encryptedDataDoc);
        if (encryptSymmetricKey) {
            Crypto crypto = getCrypto(message, 
                                      SecurityConstants.ENCRYPT_CRYPTO,
                                      SecurityConstants.ENCRYPT_PROPERTIES);
            
            String user = getUserName(message, crypto);
            if (StringUtils.isEmpty(user)) {
                return null;
            }
            X509Certificate cert = getReceiverCertificate(crypto, user);
            byte[] encryptedSecretKey = encryptSymmetricKey(secretKey, cert, crypto);

            addEncryptedKeyElement(encryptedDataElement, cert, encryptedSecretKey);
        }
               
        // encrypt payloadDoc
        XMLCipher xmlCipher = null;
        try {
            xmlCipher = XMLCipher.getInstance(symEncAlgo);
        } catch (XMLEncryptionException ex) {
            throw new WSSecurityException(
                WSSecurityException.UNSUPPORTED_ALGORITHM, null, null, ex
            );
        }
        xmlCipher.init(XMLCipher.ENCRYPT_MODE, symmetricKey);
        Document result = xmlCipher.doFinal(payloadDoc, payloadDoc.getDocumentElement(), false);
        NodeList list = result.getElementsByTagNameNS(WSConstants.ENC_NS, "CipherValue");
        if (list.getLength() != 1) {
            throw new WSSecurityException("Payload CipherData is missing", null);
        }
        String cipherText = ((Element)list.item(0)).getTextContent().trim();
        Element cipherValue = 
            createCipherValue(encryptedDataDoc, encryptedDataDoc.getDocumentElement());
        cipherValue.appendChild(encryptedDataDoc.createTextNode(cipherText));
         
        //StaxUtils.copy(new DOMSource(encryptedDataDoc), System.out);
        return encryptedDataDoc;
    }
    
    private byte[] getSymmetricKey() throws Exception {
        synchronized (this) {
            if (symmetricKey == null) {
                KeyGenerator keyGen = getKeyGenerator();
                symmetricKey = keyGen.generateKey();
            } 
        }
        return symmetricKey.getEncoded();
    }
    
    private X509Certificate getReceiverCertificate(Crypto crypto, String user) throws Exception {
        CryptoType cryptoType = new CryptoType(CryptoType.TYPE.ALIAS);
        cryptoType.setAlias(user);
        X509Certificate[] certs = crypto.getX509Certificates(cryptoType);
        if (certs == null || certs.length <= 0) {
            throw new WSSecurityException(
                WSSecurityException.FAILURE,
                "noUserCertsFound",  
                new Object[] {user, "encryption"}
            );
        }
        return certs[0];
    }
    
    private KeyGenerator getKeyGenerator() throws WSSecurityException {
        try {
            //
            // Assume AES as default, so initialize it
            //
            String keyAlgorithm = JCEMapper.getJCEKeyAlgorithmFromURI(symEncAlgo);
            KeyGenerator keyGen = KeyGenerator.getInstance(keyAlgorithm);
            if (symEncAlgo.equalsIgnoreCase(WSConstants.AES_128)) {
                keyGen.init(128);
            } else if (symEncAlgo.equalsIgnoreCase(WSConstants.AES_192)) {
                keyGen.init(192);
            } else if (symEncAlgo.equalsIgnoreCase(WSConstants.AES_256)) {
                keyGen.init(256);
            }
            return keyGen;
        } catch (NoSuchAlgorithmException e) {
            throw new WSSecurityException(
                WSSecurityException.UNSUPPORTED_ALGORITHM, null, null, e
            );
        }
    }
    
    // Apache Security XMLCipher does not support 
    // Certificates for encrypting the keys
    protected byte[] encryptSymmetricKey(byte[] keyBytes, 
                                         X509Certificate remoteCert,
                                         Crypto crypto) throws WSSecurityException {
        Cipher cipher = WSSecurityUtil.getCipherInstance(keyEncAlgo);
        try {
            cipher.init(Cipher.ENCRYPT_MODE, remoteCert);
        } catch (InvalidKeyException e) {
            throw new WSSecurityException(
                WSSecurityException.FAILED_ENCRYPTION, null, null, e
            );
        }
        int blockSize = cipher.getBlockSize();
        if (blockSize > 0 && blockSize < keyBytes.length) {
            throw new WSSecurityException(
                WSSecurityException.FAILURE,
                "unsupportedKeyTransp",
                new Object[] {"public key algorithm too weak to encrypt symmetric key"}
            );
        }
        byte[] encryptedEphemeralKey = null;
        try {
            encryptedEphemeralKey = cipher.doFinal(keyBytes);
        } catch (IllegalStateException ex) {
            throw new WSSecurityException(
                WSSecurityException.FAILED_ENCRYPTION, null, null, ex
            );
        } catch (IllegalBlockSizeException ex) {
            throw new WSSecurityException(
                WSSecurityException.FAILED_ENCRYPTION, null, null, ex
            );
        } catch (BadPaddingException ex) {
            throw new WSSecurityException(
                WSSecurityException.FAILED_ENCRYPTION, null, null, ex
            );
        }
       
        return encryptedEphemeralKey;
       
    }
    
    private void addEncryptedKeyElement(Element encryptedDataElement,
                                        X509Certificate cert,
                                        byte[] encryptedKey) throws Exception {
        
        Document doc = encryptedDataElement.getOwnerDocument();
        
        String encodedKey = Base64Utility.encode(encryptedKey);
        Element encryptedKeyElement = createEncryptedKeyElement(doc);
        String encKeyId = "EK-" + UUIDGenerator.getUUID();
        encryptedKeyElement.setAttributeNS(null, "Id", encKeyId);
                
        Element keyInfoElement = createKeyInfoElement(doc, cert, WSConstants.X509_KEY_IDENTIFIER);
        encryptedKeyElement.appendChild(keyInfoElement);
        
        Element xencCipherValue = createCipherValue(doc, encryptedKeyElement);
        xencCipherValue.appendChild(doc.createTextNode(encodedKey));
        
        
        encryptedDataElement.appendChild(encryptedKeyElement);
    }
    
    protected Element createCipherValue(Document doc, Element encryptedKey) {
        Element cipherData = 
            doc.createElementNS(WSConstants.ENC_NS, WSConstants.ENC_PREFIX + ":CipherData");
        Element cipherValue = 
            doc.createElementNS(WSConstants.ENC_NS, WSConstants.ENC_PREFIX + ":CipherValue");
        cipherData.appendChild(cipherValue);
        encryptedKey.appendChild(cipherData);
        return cipherValue;
    }
    
    private Element createKeyInfoElement(Document encryptedDataDoc,
                                         X509Certificate remoteCert,
                                         int keyIdentifierType) throws Exception {
        Element keyInfoElement = 
            encryptedDataDoc.createElementNS(
                WSConstants.SIG_NS, WSConstants.SIG_PREFIX + ":" + WSConstants.KEYINFO_LN
            );
        
        Node keyIdentifierNode = null; 
        switch (keyIdentifierType) {
        case WSConstants.X509_KEY_IDENTIFIER:
            byte data[] = null;
            try {
                data = remoteCert.getEncoded();
            } catch (CertificateEncodingException e) {
                throw new WSSecurityException(
                    WSSecurityException.SECURITY_TOKEN_UNAVAILABLE, "encodeError", null, e
                );
            }
            Text text = encryptedDataDoc.createTextNode(Base64.encode(data));
            Element cert = encryptedDataDoc.createElementNS(
                WSConstants.SIG_NS, WSConstants.SIG_PREFIX + ":" + WSConstants.X509_CERT_LN);
            cert.appendChild(text);
            Element x509Data = encryptedDataDoc.createElementNS(
                WSConstants.SIG_NS, WSConstants.SIG_PREFIX + ":" + WSConstants.X509_DATA_LN);
            
            x509Data.appendChild(cert);
            keyIdentifierNode = x509Data; 
            break;

        case WSConstants.ISSUER_SERIAL:
            String issuer = remoteCert.getIssuerDN().getName();
            java.math.BigInteger serialNumber = remoteCert.getSerialNumber();
            DOMX509IssuerSerial domIssuerSerial = 
                new DOMX509IssuerSerial(
                    encryptedDataDoc, issuer, serialNumber
                );
            DOMX509Data domX509Data = new DOMX509Data(encryptedDataDoc, domIssuerSerial);
            keyIdentifierNode = domX509Data.getElement();
            break;
        default:
            throw new WSSecurityException("Unsupported key identifier:" + keyIdentifierType);
        }
 
        keyInfoElement.appendChild(keyIdentifierNode);
        
        return keyInfoElement;
    }
    
    protected Element createEncryptedKeyElement(Document encryptedDataDoc) {
        Element encryptedKey = 
            encryptedDataDoc.createElementNS(WSConstants.ENC_NS, WSConstants.ENC_PREFIX + ":EncryptedKey");

        Element encryptionMethod = 
            encryptedDataDoc.createElementNS(WSConstants.ENC_NS, WSConstants.ENC_PREFIX 
                                             + ":EncryptionMethod");
        encryptionMethod.setAttributeNS(null, "Algorithm", keyEncAlgo);
        encryptedKey.appendChild(encryptionMethod);
        return encryptedKey;
    }
    
    protected Element createEncryptedDataElement(Document encryptedDataDoc) {
        Element encryptedData = 
            encryptedDataDoc.createElementNS(WSConstants.ENC_NS, WSConstants.ENC_PREFIX + ":EncryptedData");

        WSSecurityUtil.setNamespace(encryptedData, WSConstants.ENC_NS, WSConstants.ENC_PREFIX);
        
        Element encryptionMethod = 
            encryptedDataDoc.createElementNS(WSConstants.ENC_NS, WSConstants.ENC_PREFIX 
                                             + ":EncryptionMethod");
        encryptionMethod.setAttributeNS(null, "Algorithm", symEncAlgo);
        encryptedData.appendChild(encryptionMethod);
        encryptedDataDoc.appendChild(encryptedData);
        
        return encryptedData;
    }
    
    
    private Object getRequestBody(Message message) {
        MessageContentsList objs = MessageContentsList.getContentsList(message);
        if (objs == null || objs.size() == 0) {
            return null;
        } else {
            return objs.get(0);
        }
    }
    
    @SuppressWarnings("unchecked")
    private Document getDomDocument(Object body, Message m) throws Exception {
        
        if (body instanceof Document) {
            return (Document)body;
        }
        if (body instanceof DOMSource) {
            return (Document)((DOMSource)body).getNode();
        }
        
        ProviderFactory pf = ProviderFactory.getInstance(m);
        
        Object providerObject = pf.createMessageBodyWriter(body.getClass(), 
                                   body.getClass(), new Annotation[]{}, 
                                   MediaType.APPLICATION_XML_TYPE, m);
        if (!(providerObject instanceof JAXBElementProvider)) {
            return null;
        }
        JAXBElementProvider provider = (JAXBElementProvider)providerObject;
        W3CDOMStreamWriter writer = new W3CDOMStreamWriter();
        m.setContent(XMLStreamWriter.class, writer);
        provider.writeTo(body, body.getClass(), 
                         body.getClass(), new Annotation[]{},
                         MediaType.APPLICATION_XML_TYPE,
                         (MultivaluedMap)m.get(Message.PROTOCOL_HEADERS), null);
        return writer.getDocument();
    }
    
 // This code will be moved to a common utility class
    private String getUserName(Message message, Crypto crypto) {
        String userNameKey = SecurityConstants.ENCRYPT_USERNAME;
        String user = (String)message.getContextualProperty(userNameKey);
        if (crypto != null && StringUtils.isEmpty(user)) {
            try {
                user = crypto.getDefaultX509Identifier();
            } catch (WSSecurityException e1) {
                throw new Fault(e1);
            }
        }
        return user;
    }
    
        
    private Crypto getCrypto(Message message,
                             String cryptoKey, 
                             String propKey) {
        Crypto crypto = (Crypto)message.getContextualProperty(cryptoKey);
        if (crypto != null) {
            return crypto;
        }
        
        Object o = message.getContextualProperty(propKey);
        if (o == null) {
            return null;
        }
        
        crypto = getCryptoCache(message).get(o);
        if (crypto != null) {
            return crypto;
        }
        Properties properties = null;
        if (o instanceof Properties) {
            properties = (Properties)o;
        } else if (o instanceof String) {
            ResourceManager rm = message.getExchange().get(Bus.class).getExtension(ResourceManager.class);
            URL url = rm.resolveResource((String)o, URL.class);
            try {
                if (url == null) {
                    url = ClassLoaderUtils.getResource((String)o, this.getClass());
                }
                if (url == null) {
                    try {
                        url = new URL((String)o);
                    } catch (Exception ex) {
                        throw new RuntimeException(ex);
                    }
                }
                if (url != null) {
                    InputStream ins = url.openStream();
                    properties = new Properties();
                    properties.load(ins);
                    ins.close();
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else if (o instanceof URL) {
            properties = new Properties();
            try {
                InputStream ins = ((URL)o).openStream();
                properties.load(ins);
                ins.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }            
        }
        
        if (properties != null) {
            try {
                crypto = CryptoFactory.getInstance(properties);
            } catch (Exception ex) {
                return null;
            }
            getCryptoCache(message).put(o, crypto);
        }
        return crypto;
    }
    
    protected final Map<Object, Crypto> getCryptoCache(Message message) {
        EndpointInfo info = message.getExchange().get(Endpoint.class).getEndpointInfo();
        synchronized (info) {
            Map<Object, Crypto> o = 
                CastUtils.cast((Map<?, ?>)message.getContextualProperty(CRYPTO_CACHE));
            if (o == null) {
                o = new ConcurrentHashMap<Object, Crypto>();
                info.setProperty(CRYPTO_CACHE, o);
            }
            return o;
        }
    }
}