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

import org.z3950.zing.cql.CQLNode;
import org.z3950.zing.cql.CQLTermNode;

import gov.loc.www.zing.srw.ExtraDataType;
import gov.loc.www.zing.srw.ScanRequestType;
import gov.loc.www.zing.srw.TermType;

import java.util.Properties;

import ORG.oclc.os.SRW.QueryResult;

/**
 * Interface describing the bridge between CQL and another search language.
 *
 * @author peter
 *         Date: Oct 25, 2005
 *         Time: 9:54:31 AM
 */
public interface CQLTranslator {

    /**
     * Accepts a CQL search search and translates it into another search language,
     * executes it and then returns the results.
     *
     * @param queryRoot - The root node of a CQL search.
     * @param extraDataType - nonstandard search parameters
     * @return an array of identifiers.
     */
    public QueryResult search(CQLNode queryRoot, ExtraDataType extraDataType) throws SRWDiagnostic;


    /**
     * Accepts a CQL Scan search and translates it into another search language,
     * executes it, and then returns the results.
     *
     * @param queryRoot - The root node of a CQL search.
     * @param extraDataType - nonstandard search parameters
     * @return an array of terms and thier document counts.
     */
    public TermType[] scan(CQLTermNode queryRoot, ExtraDataType extraDataType) throws Exception;

    /**
     * Accepts a CQL Scan search and translates it into another search language,
     * executes it, and then returns the results.
     *
     * @param queryRoot - The root node of a CQL search.
     * @param extraDataType - nonstandard search parameters
     * @return an array of terms and thier document counts.
     */
    public TermType[] scan(CQLTermNode queryRoot, ScanRequestType scanRequestType) throws Exception;

    /**
     * Initializes the translator with the properties passed in
     * 
     * @param properties
     */
    public void init(Properties properties);


}
