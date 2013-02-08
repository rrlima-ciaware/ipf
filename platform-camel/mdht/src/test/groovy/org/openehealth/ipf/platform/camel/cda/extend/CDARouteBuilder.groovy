/*
 * Copyright 2008 the original author or authors.
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
package org.openehealth.ipf.platform.camel.cda.extend

import org.apache.camel.spring.SpringRouteBuilder

/**
 * @author Christian Ohr
 */
class CDARouteBuilder extends SpringRouteBuilder {
      
    void configure() {
        
        from("direct:input1")
            .marshal().cda()
            .to('mock:output')        

        from("direct:input2")
            .unmarshal().cda()
            .to('mock:output') 
            
        from("direct:input3")
            .onException(Exception.class)
                .to('mock:error')
                .end()
            .validate().cda()
            .to('mock:output')
            
        from("direct:input4")
            .onException(Exception.class)
                .to('mock:error')
                .end()
            .validate().cda()
            .to('mock:output')             
    }
    
}