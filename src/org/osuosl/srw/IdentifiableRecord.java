/*
 * OCKHAM P2PREGISTRY Copyright 2006 Oregon State University
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
package org.osuosl.srw;

import ORG.oclc.os.SRW.Record;

/**
 *
 * Extension of Record for the purpose of adding an easily accessible identifier.
 *
 * @author peter
 *         Date: Apr 13, 2006
 *         Time: 9:36:54 AM
 */
public class IdentifiableRecord extends Record {

    /**
     *  Identifier
     */
    private String identifier;
    public String getIdentifier(){return identifier;}
    public void setIdentifier(String inp){
        identifier = inp;
    }

    public IdentifiableRecord(String record, String schemaID) {
        super(record, schemaID);
    }

    public IdentifiableRecord(String identifier, String record, String schemaID) {
        super(record, schemaID);
        this.identifier = identifier;
    }

}
