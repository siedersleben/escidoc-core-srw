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

import java.io.IOException;
import java.util.NoSuchElementException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import ORG.oclc.os.SRW.Record;
import ORG.oclc.os.SRW.RecordIterator;
import ORG.oclc.os.SRW.SRWDiagnostic;

/**
 * @author peter
 *         Date: Apr 10, 2006
 *         Time: 11:23:20 AM
 */
public class ResolvingRecordIterator implements RecordIterator {

    static final Log log= LogFactory.getLog(RecordIterator.class);

    int index=0;

    Stream[] identifiers;
    RecordResolver resolver;
    ExtraDataType edt;
    String schemaID;

    public ResolvingRecordIterator(Stream[] records, String schemaId, ExtraDataType edt, RecordResolver resolver) {
        this.identifiers = records;
        this.resolver = resolver;
        this.schemaID = schemaId;
        this.edt = edt;
    }

    public void close() {}



    public boolean hasNext() {
        return index < identifiers.length;
    }

    public Object next() throws NoSuchElementException {
        return nextRecord();
    }

    public Record nextRecord() throws NoSuchElementException {
        if(hasNext()) {

            // get the identifier for next record
            Stream id = identifiers[index];

            resolver.init(null);
            Record record = resolver.resolve(id, edt);

            if(schemaID!=null && !schemaID.equals(SRWDatabaseImpl.DEFAULT_SCHEMA) && !record.getRecordSchemaID().equals(schemaID)) {
                log.debug("transforming to "+schemaID);

                try {
                    /**
                     * Attempt to transform record to requested format
                     */
                    Stream transformedXML = resolver.transform(record, schemaID);
                    if (record.getRecord() != null) {
                        try {
							record.getRecord().close();
						} catch (IOException e) {
							log.info("couldnt close Stream");
						}
                    }
                    record = new Record(transformedXML, schemaID);

                } catch (SRWDiagnostic srwDiagnostic) {
                    //todo update this logic
                    if (true) {
                        /**
                         * Schema is not available for this specific record.
                         * return a diagnostic as a surrogate.
                         */
                        log.info("record not available in schema "+schemaID);
                        //  rec = new Record(
                        //          SRWDiagnostic.newSurragateDiagnostic(SRWDiagnostic.RecordNotAvailableInThisSchema,
                        //                  schemaID, response)
                        //          ,"info:srw/diagnostic/1/71"
                        //          );
                    }

                    //return diagnostic(SRWDiagnostic.,
                    //        schemaID, response);

                }

            }

            // increment index
            index++;

            return record;
        }
        throw new NoSuchElementException();
    }

    public void remove() throws UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }

}
