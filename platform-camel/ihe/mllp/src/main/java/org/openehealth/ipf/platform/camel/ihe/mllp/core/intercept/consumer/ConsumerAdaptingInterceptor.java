/*
 * Copyright 2009 the original author or authors.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *     
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openehealth.ipf.platform.camel.ihe.mllp.core.intercept.consumer;

import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.parser.ModelClassFactory;
import ca.uhn.hl7v2.parser.Parser;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openehealth.ipf.modules.hl7.AckTypeCode;
import org.openehealth.ipf.modules.hl7.HL7v2Exception;
import org.openehealth.ipf.modules.hl7dsl.MessageAdapter;
import org.openehealth.ipf.platform.camel.core.util.Exchanges;
import org.openehealth.ipf.platform.camel.ihe.mllp.core.*;
import org.openehealth.ipf.platform.camel.ihe.mllp.core.intercept.AbstractMllpInterceptor;

import static org.openehealth.ipf.platform.camel.core.util.Exchanges.resultMessage;


/**
 * Consumer-side Camel interceptor which creates a {@link MessageAdapter} 
 * from various possible response types.
 *  
 * @author Dmytro Rud
 */
public class ConsumerAdaptingInterceptor extends AbstractMllpInterceptor {
    private static final transient Log LOG = LogFactory.getLog(ConsumerAdaptingInterceptor.class);

    public ConsumerAdaptingInterceptor(MllpEndpoint endpoint, Processor wrappedProcessor) {
        super(endpoint, wrappedProcessor);
    }
    
    
    /**
     * Converts response to a {@link MessageAdapter}, throws 
     * a {@link MllpAdaptingException} on failure.  
     */
    @Override
    public void process(Exchange exchange) throws Exception {
        MessageAdapter originalAdapter = exchange.getIn().getHeader(ORIGINAL_MESSAGE_ADAPTER_HEADER_NAME, MessageAdapter.class); 
        Message originalMessage = originalAdapter.getHapiMessage();

        // run the route
        try {
            getWrappedProcessor().process(exchange);
            checkExchangeFailed(exchange, originalMessage);
        } catch(Exception e) {
            LOG.error("Message processing failed", e);
            resultMessage(exchange).setBody(getMllpEndpoint().getNakFactory().createNak(originalMessage, e));
        }

        org.apache.camel.Message m = Exchanges.resultMessage(exchange);
        Object body = m.getBody();

        // try to convert route response from a known type
        MessageAdapter msg = MllpMarshalUtils.extractMessageAdapter(
                m,
                getMllpEndpoint().getConfiguration().getCharsetName(),
                getMllpEndpoint().getTransactionConfiguration().getParser());
        
        // additionally: an Exception in the body?
        if((msg == null) && (body instanceof Throwable)) {
            Message message = getMllpEndpoint().getNakFactory().createNak(originalMessage, (Throwable) body);
            msg = new MessageAdapter(message);
        }
        
        // no known data type --> determine user's intention on the basis of a header 
        if(msg == null) {
            msg = analyseMagicHeader(m, originalMessage);
        }

        // unable to create a MessageAdaper :-(
        if(msg == null) {
            String className = (body == null) ? "null" : body.getClass().getName();
            throw new MllpAdaptingException("Do not know how to handle " + className +
                    " value returned from the PIX/PDQ route");
        }
        
        m.setBody(msg);
    }


    /**
     * Considers a specific header to determine whether the route author want us to generate
     * an automatic acknowledgment, and generates the latter when the author really does.   
     */
    private MessageAdapter analyseMagicHeader(org.apache.camel.Message m, Message originalMessage) {
        Object header = m.getHeader(MllpComponent.ACK_TYPE_CODE_HEADER, AckTypeCode.class);
        if (header == null) {
            return null;
        }

        Message ack;
        if ((header == AckTypeCode.AA) || (header == AckTypeCode.CA)) {
            ack = getMllpEndpoint().getNakFactory().createAck(
                    originalMessage,
                    (AckTypeCode) header);
        } else {
            HL7v2Exception exception = new HL7v2Exception(
                    "Error in PIX/PDQ route", 
                    getMllpEndpoint().getTransactionConfiguration().getResponseErrorDefaultErrorCode());
            ack = getMllpEndpoint().getNakFactory().createNak(
                    originalMessage,
                    exception, 
                    (AckTypeCode) header); 
        }
        return new MessageAdapter(ack);
    }
    
    
    /**
     * Checks whether the given exchange has failed.  
     * If yes, substitutes the exception object with a HL7 NAK  
     * and marks the exchange as successful. 
     */
    private void checkExchangeFailed(Exchange exchange, Message original) {
        if (exchange.isFailed()) {
            Throwable t; 
            if(exchange.getException() != null) {
                t = exchange.getException();
                exchange.setException(null);
            } else {
                org.apache.camel.Message m = resultMessage(exchange);
                t = m.getBody(Throwable.class);
                m.setBody(null);
            }
            LOG.error("Message processing failed", t);
            resultMessage(exchange).setBody(getMllpEndpoint().getNakFactory().createNak(original, t));
        }
    }
    
}
