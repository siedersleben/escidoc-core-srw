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

import gov.loc.www.zing.srw.ExtraDataType;
import gov.loc.www.zing.srw.utils.Stream;

import java.util.Properties;

import ORG.oclc.os.SRW.Record;
import ORG.oclc.os.SRW.SRWDiagnostic;

/**
 * Interface for resolving records and schemas.
 *
 * @author peter
 *         Date: Oct 25, 2005
 *         Time: 9:49:08 AM
 */
public interface RecordResolver {

    /**
     * Resolves a record from the identifier
     *
     * @param Id - identifier
     * @param extraDataType - nonstandard search parameters
     * @return record if found.
     */
    public Record resolve(Object Id, ExtraDataType extraDataType);

    /**
     * Transform record from one schema to another.  If the schema isn't
     * supported the appropriate diagnostic should be thrown.
     *
     * @param record - record to transform
     * @param schema - schema to transform to
     * @return transformed xml.
     * @throws SRWDiagnostic - thrown if schema isn't available for the record
     * or if there is a general exception.
     */
    public Stream transform(Record record, String schema) throws SRWDiagnostic;

    /**
     * Determines if a schema is supported.  This method
     * must return true for at least the default schema.
     *
     * @param schema
     * @return true if schema was found
     */
    public boolean containsSchema(String schema);

    /**
     * Generate a schema xml fragment for use in responses.
     *
     *
     * @return xml fragment containing schema info
     */
    public StringBuffer getSchemaInfo();

    /**
     * Initialize the resolver.
     * 
     * @param properties
     */
    public void init(Properties properties);



}
