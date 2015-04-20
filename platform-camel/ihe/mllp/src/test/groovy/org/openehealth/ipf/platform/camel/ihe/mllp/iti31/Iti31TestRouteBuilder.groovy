/*
 * Copyright 2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openehealth.ipf.platform.camel.ihe.mllp.iti31

import org.apache.camel.spring.SpringRouteBuilder

import static org.openehealth.ipf.platform.camel.core.util.Exchanges.resultMessage
import static org.openehealth.ipf.platform.camel.hl7.HL7v2.ack


/**
 * Camel route for generic unit tests.
 * @author Christian Ohr
 */
class Iti31TestRouteBuilder extends SpringRouteBuilder {

    void configure() throws Exception {

        // normal processing with auditing
        from('pam-iti31://0.0.0.0:18100')
                .transform(ack())

        // fictive route to test producer-side acceptance checking
        from('pam-iti31://0.0.0.0:18101')
                .process {
            resultMessage(it).body.MSH[9][1] = 'DOES NOT MATTER'
            resultMessage(it).body.MSH[9][2] = 'SHOULD FAIL IN INTERCEPTORS'
        }

        // route with runtime exception
        from('pam-iti31://0.0.0.0:18102')
            .onException(Exception.class)
                .maximumRedeliveries(0)
                .end()
            .process { throw new RuntimeException('Jump over the lazy dog, you fox.') }

        // route with explicit options
        from('pam-iti31://0.0.0.0:18103?options=BASIC,TEMPORARY_PATIENT_TRANSFERS_TRACKING')
                .routeId("withOptions")
                .transform(ack())
    }
}

