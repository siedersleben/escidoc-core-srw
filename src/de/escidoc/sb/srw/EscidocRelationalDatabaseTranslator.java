/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at license/ESCIDOC.LICENSE
 * or http://www.escidoc.de/license.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at license/ESCIDOC.LICENSE.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information: Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 */

/*
 * Copyright 2008 Fachinformationszentrum Karlsruhe Gesellschaft
 * fuer wissenschaftlich-technische Information mbH and Max-Planck-
 * Gesellschaft zur Foerderung der Wissenschaft e.V.  
 * All rights reserved.  Use is subject to license terms.
 */

package de.escidoc.sb.srw;

import gov.loc.www.zing.srw.ExtraDataType;
import gov.loc.www.zing.srw.ScanRequestType;
import gov.loc.www.zing.srw.SearchRetrieveRequestType;
import gov.loc.www.zing.srw.TermType;
import gov.loc.www.zing.srw.utils.Stream;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.axis.types.NonNegativeInteger;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.osuosl.srw.ResolvingQueryResult;
import org.osuosl.srw.SRWDiagnostic;
import org.z3950.zing.cql.CQLNode;
import org.z3950.zing.cql.CQLTermNode;

import ORG.oclc.os.SRW.QueryResult;

public class EscidocRelationalDatabaseTranslator extends EscidocTranslator {

	protected static Log log = LogFactory.getLog(EscidocRelationalDatabaseTranslator.class);

    @Override
    public QueryResult search(final CQLNode cqlnode,
            final ExtraDataType extradatatype,
            final SearchRetrieveRequestType request, final String dbName) throws SRWDiagnostic {
        
        String sortKeys = request.getSortKeys();
        Stream[] identifiers = new Stream[1];
        
        StringBuilder builder = new StringBuilder();
        builder.append("<testidentifier>");
        builder.append("<element>hello world</element>");
        builder.append("</testidentifier>");

        try {
        	identifiers[0] = new Stream();
			identifiers[0].write(builder.toString().getBytes(Constants.CHARACTER_ENCODING));
			identifiers[0].lock();
		} catch (UnsupportedEncodingException e) {
			for (int i = 0; i < identifiers.length; i++) {
				if (identifiers[i] != null) {
					try {
						identifiers[i].close();
					} catch (IOException e1) {
						log.info("couldnt close stream");
					}
				}
			}
			throw new SRWDiagnostic(SRWDiagnostic.GeneralSystemError, e.toString());
		} catch (IOException e) {
			for (int i = 0; i < identifiers.length; i++) {
				if (identifiers[i] != null) {
					try {
						identifiers[i].close();
					} catch (IOException e1) {
						log.info("couldnt close stream");
					}
				}
			}
			throw new SRWDiagnostic(SRWDiagnostic.GeneralSystemError, e.toString());
		}
        
        return new ResolvingQueryResult(identifiers);
    }

    @Override
    public TermType[] scan(CQLTermNode cqlnode, ScanRequestType scanRequestType)
            throws Exception {
        TermType[] response = new TermType[0];
        Map termMap = new HashMap();

        TermType termType = new TermType();
        termType.setValue("hello world");
        termType.setNumberOfRecords(new NonNegativeInteger(Integer
                .toString(1)));
        termMap.put("hello world", termType);
        response = (TermType[]) termMap.values().toArray(response);
        return response;
    }

    @Override
    public void init(Properties properties) {
        
    }

    /**
     * Returns a list of all FieldNames 
     * that are indexed.
     * 
     * @return Collection all FieldNames 
     * that are indexed.
     * 
     * @sb
     */
    @Override
    public Collection<String> getIndexedFieldList() {
        return null;
    }

    /**
     * Returns a list of all FieldNames 
     * that are stored.
     * 
     * @return Collection all FieldNames 
     * that are stored
     * 
     * @sb
     */
    @Override
    public Collection<String> getStoredFieldList() {
        return null;
    }
}
