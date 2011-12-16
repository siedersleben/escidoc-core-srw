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
import gov.loc.www.zing.srw.RecordType;
import gov.loc.www.zing.srw.RecordsType;
import gov.loc.www.zing.srw.SearchRetrieveRequestType;
import gov.loc.www.zing.srw.SearchRetrieveResponseType;
import gov.loc.www.zing.srw.StringOrXmlFragment;
import gov.loc.www.zing.srw.utils.RestSearchRetrieveResponseType;
import gov.loc.www.zing.srw.utils.Stream;

import java.io.IOException;
import java.io.StringReader;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.NoSuchElementException;
import java.util.regex.Matcher;

import javax.servlet.ServletException;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.Marshaller;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.axis.MessageContext;
import org.apache.axis.message.MessageElement;
import org.apache.axis.message.Text;
import org.apache.axis.types.NonNegativeInteger;
import org.apache.axis.types.PositiveInteger;
import org.apache.axis.types.URI;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.osuosl.srw.ResolvingQueryResult;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;
import org.z3950.zing.cql.CQLNode;
import org.z3950.zing.cql.CQLParseException;

import ORG.oclc.os.SRW.Record;
import ORG.oclc.os.SRW.RecordIterator;
import ORG.oclc.os.SRW.SRWDiagnostic;

/**
 * Class overwrites org.osuosl.srw.SRWDatabaseImpl. This is done because: -we
 * dont retrieve and store all search-hits but only the ones requested -we dont
 * use result-sets -we do sorting while querying lucene and not afterwards -get
 * dynamic index-info from available lucene-fields
 * 
 * @author MIH
 * @sb
 */
public class EscidocSRWDatabaseImpl extends org.osuosl.srw.SRWDatabaseImpl {

    private static Log log = LogFactory.getLog(EscidocSRWDatabaseImpl.class);
    
    private static final int DIAGNOSTIC_CODE_EIGHT = 8;

    private static final int DIAGNOSTIC_CODE_SEVENTY_ONE = 71;

    private static final int TEN = 10;

    private static final int ELEVEN = 11;

    private static final int MILLISECONDS_PER_SECOND = 1000;

    /**
     * execute search -> get results. getRecordIterator from results. iterate
     * over results, get records, put records into response.
     * 
     * @param request
     *            SearchRetrieveRequestType
     * @return SearchRetrieveResponseType response
     * @throws ServletException
     *             e
     * @sb
     */
    @Override
    public RestSearchRetrieveResponseType doRequest(
        final org.escidoc.core.domain.sru.SearchRetrieveRequestType request) throws ServletException {

    	RestSearchRetrieveResponseType response = null; // search response
        int resultSetTTL; // time result set should expire
        String recordPacking; // how record is packed (xml|string)
        String query; // Search String
        String sortKeysString; // keys to sort on
        PositiveInteger startRec; // record number to start with
        ResolvingQueryResult results; // results of search, array of
        // Ids/handles

        try {
            MessageContext msgContext = MessageContext.getCurrentContext();
            response = new RestSearchRetrieveResponseType();
            org.escidoc.core.domain.sru.SearchRetrieveResponseType searchRetrieveResponse = new org.escidoc.core.domain.sru.SearchRetrieveResponseType();
            response.setSearchRetrieveResponse(searchRetrieveResponse);
            response.getSearchRetrieveResponse().setNumberOfRecords(new NonNegativeInteger("0"));
            startRec = request.getStartRecord();

            /**
             * Get schema name and verify its supported.
             */
            String schemaName = request.getRecordSchema();
            if (schemaName == null) {
                schemaName = "default";
            }
            log.info("recordSchema=" + schemaName);

            if (schemaName != null && !schemaName.equals(DEFAULT_SCHEMA)) {
                if (!getResolver().containsSchema(schemaName)) {
                    log.error("no handler for schema " + schemaName);
                    response.setSearchRetrieveResponse(diagnostic(SRWDiagnostic.UnknownSchemaForRetrieval,
                        schemaName, response.getSearchRetrieveResponse()));
                    return response;
                }
            }

            /**
             * RecordXPath - UNSUPPORTED
             */
            if (request.getRecordXPath() != null
                && request.getRecordXPath().trim().length() != 0) {
            	response.setSearchRetrieveResponse(diagnostic(
                    DIAGNOSTIC_CODE_EIGHT, request.getRecordXPath(), response.getSearchRetrieveResponse()));
            	return response;
            }

            /**
             * set result set TTL
             */
            if (request.getResultSetTTL() != null) {
                resultSetTTL = request.getResultSetTTL().intValue();
            }
            else {
                resultSetTTL = defaultResultSetTTL;
            }

            /**
             * Set Record Packing
             */
            recordPacking = request.getRecordPacking();
            if (recordPacking == null) {
                if (msgContext != null && msgContext.getProperty("sru") != null) {
                    recordPacking = "xml"; // default for sru
                }
                else {
                    recordPacking = "string"; // default for srw
                }
            }

            /**
             * get sort keys
             */
            sortKeysString = request.getSortKeys();
            if (sortKeysString != null && sortKeysString.length() == 0) {
                sortKeysString = null;
            }

            /**
             * Parse and Execute Query
             */
            query = request.getQuery();
            try {
                log.info("search:\n"
                    + ORG.oclc.util.Util.byteArrayToString(query
                        .getBytes("UTF-8")));
            }
            catch (Exception e) {
                log.info(e);
            }

            CQLNode queryRoot = null;
            //MIH: Workaround because CQLParser has Bug when handling \"
            //replace \" with #quote#
            query = query.replaceAll("([^\\\\])\\\\\"", "$1#quote#");
            
            //MIH: another workaround if query is empty string
            if (query.trim().equals("")) {
                query = "nosrwqueryprovided";
            }
            
            if (query.matches(".*[^\\\\]\".*")) {
            	query = escapeBackslash(query);
            }
            try {
                queryRoot = parser.parse(query);
            }
            catch (CQLParseException e) {
            	response.setSearchRetrieveResponse(diagnostic(SRWDiagnostic.QuerySyntaxError, e
                    .getMessage(), response.getSearchRetrieveResponse()));
            	return response;
            }

            String resultSetID = queryRoot.getResultSetName();
            int numRecs = defaultNumRecs;
            NonNegativeInteger maxRecs = request.getMaximumRecords();
            if (maxRecs != null) {
                numRecs =
                    (int) java.lang.Math.min(maxRecs.longValue(),
                        maximumRecords);
            }

            // MIH: set maxRecs in request, because request
            // gets passed to Escidoc..Translator
            // Escidoc...Translator performs search and fills identifers
            request.setMaximumRecords(new NonNegativeInteger(Integer
                .toString(numRecs)));

            int startPoint = 1;
            if (startRec != null) {
                startPoint = (int) startRec.longValue();
            }
            if (resultSetID != null) {
                // look for existing result set
                log.info("resultSetID=" + resultSetID);
                results = (ResolvingQueryResult) oldResultSets.get(resultSetID);
                if (results == null) {
                	response.setSearchRetrieveResponse(diagnostic(SRWDiagnostic.ResultSetDoesNotExist,
                        resultSetID, response.getSearchRetrieveResponse()));
                	return response;
                }

            }
            else {
            	ExtraDataType extraData = new ExtraDataType();
            	if (request.getExtraRequestData() != null) {
                	MessageElement[] msgs = new MessageElement[request.getExtraRequestData().getAny().size()];
                	int i = 0;
                	for (Object o : request.getExtraRequestData().getAny()) {
                		msgs[i] = new MessageElement((Element)o);
                	}
                	extraData.set_any(msgs);
            	}
            	SearchRetrieveRequestType soapRequest = new SearchRetrieveRequestType();
            	soapRequest.setExtraRequestData(extraData);
            	soapRequest.setMaximumRecords(request.getMaximumRecords());
            	soapRequest.setQuery(request.getQuery());
            	soapRequest.setRecordPacking(request.getRecordPacking());
            	soapRequest.setRecordSchema(request.getRecordSchema());
            	soapRequest.setRecordXPath(request.getRecordXPath());
            	soapRequest.setResultSetTTL(request.getResultSetTTL());
            	soapRequest.setSortKeys(request.getSortKeys());
            	soapRequest.setStartRecord(request.getStartRecord());
            	if (request.getStylesheet() != null) {
                	soapRequest.setStylesheet(new URI(request.getStylesheet()));
            	}
            	soapRequest.setVersion(request.getVersion());
                // MIH: call overwritten method that is only available in
                // Escidoc..Translator:
                // 3rd parameter: request (to get sortKeys,
                // startRecord, maxRecords..)!
                // 4th parameter: dbname (to get filterQuery for db)!
                results =
                    (ResolvingQueryResult) (
                        (EscidocTranslator) getCQLTranslator())
                        .search(queryRoot, extraData, soapRequest, dbname);

                results.setResolver(getResolver());

                /**
                 * if results were found save the result set and setup the timer
                 * for when it expires
                 * 
                 */
                if (results.getNumberOfRecords() > 0 && resultSetTTL > 0) {
                    resultSetID = makeResultSetID();
                    oldResultSets.put(resultSetID, results);
                    log.info("keeping resultSet '" + resultSetID + "' for "
                        + resultSetTTL + " seconds");
                    timers.put(resultSetID, new Long(System.currentTimeMillis()
                        + (resultSetTTL * MILLISECONDS_PER_SECOND)));
                    response.getSearchRetrieveResponse().setResultSetId(resultSetID);
                    response.getSearchRetrieveResponse().setResultSetIdleTime(new PositiveInteger(Integer
                        .toString(resultSetTTL)));
                }
            }

            int postings = (int) results.getNumberOfRecords();
            response.getSearchRetrieveResponse().setNumberOfRecords(new NonNegativeInteger(Long
                .toString(postings)));

            log.info("'" + query + "'==> " + postings);

            if (postings > 0 && startPoint > postings) {
            	response.setSearchRetrieveResponse(diagnostic(SRWDiagnostic.FirstRecordPositionOutOfRange, null,
                    response.getSearchRetrieveResponse()));
            }
            if ((startPoint - 1 + numRecs) > postings) {
                numRecs = postings - (startPoint - 1);
            }
            if (postings > 0 && numRecs == 0) {
                response.getSearchRetrieveResponse().setNextRecordPosition(new PositiveInteger("1"));
            }
            if (postings > 0 && numRecs > 0) { // render some records into SGML
            	org.escidoc.core.domain.sru.RecordsType records = new org.escidoc.core.domain.sru.RecordsType();
                log.info("trying to get " + numRecs
                    + " records starting with record " + startPoint
                    + " from a set of " + postings + " records");
                if (!recordPacking.equals("xml")
                    && !recordPacking.equals("string")) {
                	response.setSearchRetrieveResponse(diagnostic(
                        DIAGNOSTIC_CODE_SEVENTY_ONE, recordPacking, response.getSearchRetrieveResponse()));
                	return response;
                }

                int i = -1;
                org.escidoc.core.domain.sru.RecordType record;

                /**
                 * One at a time, retrieve and display the requested documents.
                 */
                RecordIterator list = null;
                try {
                    log.info("making RecordIterator, startPoint=" + startPoint
                        + ", schemaID=" + schemaName);
                    list =
                        results.recordIterator(startPoint - 1, numRecs,
                            schemaName, null);
                }
                catch (InstantiationException e) {
                	response.setSearchRetrieveResponse(diagnostic(SRWDiagnostic.GeneralSystemError,
                        e.getMessage(), response.getSearchRetrieveResponse()));
                }
                

                Marshaller marshaller = null;
                if (!recordPacking.equals("xml")) {
                    try {
                        JAXBContext jc = JAXBContext.newInstance("org.escidoc.core.domain.sru");
                        marshaller = jc.createMarshaller();
                        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
                    } catch (Exception e) {
                        throw new IOException(e);
                    }
                }
                for (i = 0; list.hasNext(); i++) {
                    try {

                        Record rec = list.nextRecord();

                        /**
                         * create record container
                         */
                        record = new org.escidoc.core.domain.sru.RecordType();
                        record.setRecordPacking(recordPacking);
                        record.setRecordSchema(rec.getRecordSchemaID());

                        
                        Stream stream = new Stream();
                        
                        if (recordPacking.equals("xml")) {
                            stream.write(rec.getRecord().getBytes(Constants.CHARACTER_ENCODING));
                        }
                        else { // string
                            stream.write(StringEscapeUtils.escapeXml(rec.getRecord()).getBytes(Constants.CHARACTER_ENCODING));
                        }
                        response.getRecordStreams().add(stream);
                        
//                        org.escidoc.core.domain.sru.StringOrXmlFragment frag = new org.escidoc.core.domain.sru.StringOrXmlFragment();
//                        frag.getContent().add(rec.getRecord());
//                        record.setRecordData(frag);
//                        log.debug("setRecordData");

                        record.setRecordPosition(new PositiveInteger(Integer
                            .toString(startPoint + i)));
                        records.getRecord().add(record);

                    }
                    catch (NoSuchElementException e) {
                        log.error("Read beyond the end of list!!");
                        log.error(e);
                        break;
                    }
                    response.getSearchRetrieveResponse().setRecords(records);
                }
                if (startPoint + i <= postings) {
                    response.getSearchRetrieveResponse().setNextRecordPosition(new PositiveInteger(Long
                        .toString(startPoint + i)));
                }
            }
            log.debug("exit doRequest");
            return response;

        }
        catch (Exception e) {
        	response.setSearchRetrieveResponse(diagnostic(SRWDiagnostic.GeneralSystemError, e.getMessage(),
                response.getSearchRetrieveResponse()));
        	return response;
        }

    }

    /**
     * execute search -> get results. getRecordIterator from results. iterate
     * over results, get records, put records into response.
     * 
     * @param request
     *            SearchRetrieveRequestType
     * @return SearchRetrieveResponseType response
     * @throws ServletException
     *             e
     * @sb
     */
    @Override
    public SearchRetrieveResponseType doRequest(
        final SearchRetrieveRequestType request) throws ServletException {

        SearchRetrieveResponseType response = null; // search response
        int resultSetTTL; // time result set should expire
        String recordPacking; // how record is packed (xml|string)
        String query; // Search String
        String sortKeysString; // keys to sort on
        PositiveInteger startRec; // record number to start with
        ExtraDataType extraData = null; // extra params sent with request
        ResolvingQueryResult results; // results of search, array of
        // Ids/handles

        try {
            MessageContext msgContext = MessageContext.getCurrentContext();
            response = new SearchRetrieveResponseType();
            response.setNumberOfRecords(new NonNegativeInteger("0"));
            startRec = request.getStartRecord();
            extraData = request.getExtraRequestData();

            /**
             * Get schema name and verify its supported.
             */
            String schemaName = request.getRecordSchema();
            if (schemaName == null) {
                schemaName = "default";
            }
            log.info("recordSchema=" + schemaName);

            if (schemaName != null && !schemaName.equals(DEFAULT_SCHEMA)) {
                if (!getResolver().containsSchema(schemaName)) {
                    log.error("no handler for schema " + schemaName);
                    return diagnostic(SRWDiagnostic.UnknownSchemaForRetrieval,
                        schemaName, response);
                }
            }

            /**
             * RecordXPath - UNSUPPORTED
             */
            if (request.getRecordXPath() != null
                && request.getRecordXPath().trim().length() != 0) {
                return diagnostic(
                    DIAGNOSTIC_CODE_EIGHT, request.getRecordXPath(), response);
            }

            /**
             * set result set TTL
             */
            if (request.getResultSetTTL() != null) {
                resultSetTTL = request.getResultSetTTL().intValue();
            }
            else {
                resultSetTTL = defaultResultSetTTL;
            }

            /**
             * Set Record Packing
             */
            recordPacking = request.getRecordPacking();
            if (recordPacking == null) {
                if (msgContext != null && msgContext.getProperty("sru") != null) {
                    recordPacking = "xml"; // default for sru
                }
                else {
                    recordPacking = "string"; // default for srw
                }
            }

            /**
             * get sort keys
             */
            sortKeysString = request.getSortKeys();
            if (sortKeysString != null && sortKeysString.length() == 0) {
                sortKeysString = null;
            }

            /**
             * Parse and Execute Query
             */
            query = request.getQuery();
            try {
                log.info("search:\n"
                    + ORG.oclc.util.Util.byteArrayToString(query
                        .getBytes("UTF-8")));
            }
            catch (Exception e) {
                log.info(e);
            }

            CQLNode queryRoot = null;
            //MIH: Workaround because CQLParser has Bug when handling \"
            //replace \" with #quote#
            query = query.replaceAll("([^\\\\])\\\\\"", "$1#quote#");
            
            //MIH: another workaround if query is empty string
            if (query.trim().equals("")) {
                query = "nosrwqueryprovided";
            }
            
            if (query.matches(".*[^\\\\]\".*")) {
            	query = escapeBackslash(query);
            }
            try {
                queryRoot = parser.parse(query);
            }
            catch (CQLParseException e) {
                return diagnostic(SRWDiagnostic.QuerySyntaxError, e
                    .getMessage(), response);
            }

            String resultSetID = queryRoot.getResultSetName();
            int numRecs = defaultNumRecs;
            NonNegativeInteger maxRecs = request.getMaximumRecords();
            if (maxRecs != null) {
                numRecs =
                    (int) java.lang.Math.min(maxRecs.longValue(),
                        maximumRecords);
            }

            // MIH: set maxRecs in request, because request
            // gets passed to Escidoc..Translator
            // Escidoc...Translator performs search and fills identifers
            request.setMaximumRecords(new NonNegativeInteger(Integer
                .toString(numRecs)));

            int startPoint = 1;
            if (startRec != null) {
                startPoint = (int) startRec.longValue();
            }
            if (resultSetID != null) {
                // look for existing result set
                log.info("resultSetID=" + resultSetID);
                results = (ResolvingQueryResult) oldResultSets.get(resultSetID);
                if (results == null) {
                    return diagnostic(SRWDiagnostic.ResultSetDoesNotExist,
                        resultSetID, response);
                }

            }
            else {
                // MIH: call overwritten method that is only available in
                // Escidoc..Translator:
                // 3rd parameter: request (to get sortKeys,
                // startRecord, maxRecords..)!
                // 4th parameter: dbname (to get filterQuery for db)!
                results =
                    (ResolvingQueryResult) (
                        (EscidocTranslator) getCQLTranslator())
                        .search(queryRoot, extraData, request, dbname);

                results.setResolver(getResolver());
                results.setExtraDataType(extraData);

                /**
                 * if results were found save the result set and setup the timer
                 * for when it expires
                 * 
                 */
                if (results.getNumberOfRecords() > 0 && resultSetTTL > 0) {
                    resultSetID = makeResultSetID();
                    oldResultSets.put(resultSetID, results);
                    log.info("keeping resultSet '" + resultSetID + "' for "
                        + resultSetTTL + " seconds");
                    timers.put(resultSetID, new Long(System.currentTimeMillis()
                        + (resultSetTTL * MILLISECONDS_PER_SECOND)));
                    response.setResultSetId(resultSetID);
                    response.setResultSetIdleTime(new PositiveInteger(Integer
                        .toString(resultSetTTL)));
                }
            }

            int postings = (int) results.getNumberOfRecords();
            response.setNumberOfRecords(new NonNegativeInteger(Long
                .toString(postings)));

            log.info("'" + query + "'==> " + postings);

            if (postings > 0 && startPoint > postings) {
                diagnostic(SRWDiagnostic.FirstRecordPositionOutOfRange, null,
                    response);
            }
            if ((startPoint - 1 + numRecs) > postings) {
                numRecs = postings - (startPoint - 1);
            }
            if (postings > 0 && numRecs == 0) {
                response.setNextRecordPosition(new PositiveInteger("1"));
            }
            if (postings > 0 && numRecs > 0) { // render some records into SGML
                RecordsType records = new RecordsType();
                log.info("trying to get " + numRecs
                    + " records starting with record " + startPoint
                    + " from a set of " + postings + " records");
                if (!recordPacking.equals("xml")
                    && !recordPacking.equals("string")) {
                    return diagnostic(
                        DIAGNOSTIC_CODE_SEVENTY_ONE, recordPacking, response);
                }

                records.setRecord(new RecordType[numRecs]);
                Document domDoc;
                DocumentBuilder db = null;
                DocumentBuilderFactory dbf = null;
                int i = -1;
                MessageElement[] elems;
                RecordType record;
                StringOrXmlFragment frag;
                if (recordPacking.equals("xml")) {
                    dbf = DocumentBuilderFactory.newInstance();
                    dbf.setNamespaceAware(true);
                    db = dbf.newDocumentBuilder();
                }

                /**
                 * One at a time, retrieve and display the requested documents.
                 */
                RecordIterator list = null;
                try {
                    log.info("making RecordIterator, startPoint=" + startPoint
                        + ", schemaID=" + schemaName);
                    list =
                        results.recordIterator(startPoint - 1, numRecs,
                            schemaName, extraData);
                }
                catch (InstantiationException e) {
                    diagnostic(SRWDiagnostic.GeneralSystemError,
                        e.getMessage(), response);
                }

                for (i = 0; list.hasNext(); i++) {
                    try {

                        Record rec = list.nextRecord();

                        /**
                         * create record container
                         */
                        record = new RecordType();
                        record.setRecordPacking(recordPacking);
                        frag = new StringOrXmlFragment();
                        elems = new MessageElement[1];
                        frag.set_any(elems);
                        record.setRecordSchema(rec.getRecordSchemaID());

                        if (recordPacking.equals("xml")) {
                            domDoc =
                                db.parse(new InputSource(new StringReader(rec
                                    .getRecord())));
                            Element el = domDoc.getDocumentElement();
                            log.debug("got the DocumentElement");
                            elems[0] = new MessageElement(el);
                            log.debug("put the domDoc into elems[0]");
                        }
                        else { // string
                            Text t = new Text(rec.getRecord());
                            elems[0] = new MessageElement(t);
                        }

                        record.setRecordData(frag);
                        log.debug("setRecordData");

                        record.setRecordPosition(new PositiveInteger(Integer
                            .toString(startPoint + i)));
                        records.setRecord(i, record);

                    }
                    catch (NoSuchElementException e) {
                        log.error("Read beyond the end of list!!");
                        log.error(e);
                        break;
                    }
                    response.setRecords(records);
                }
                if (startPoint + i <= postings) {
                    response.setNextRecordPosition(new PositiveInteger(Long
                        .toString(startPoint + i)));
                }
            }
            log.debug("exit doRequest");
            return response;

        }
        catch (Exception e) {
            return diagnostic(SRWDiagnostic.GeneralSystemError, e.getMessage(),
                response);
        }

    }

    /**
     * returns info about databases for explainPlan.
     * Overwritten because schema 2.0 doesnt allow 
     * attribute indentifier in element implementation
     * 
     * @return String databaseInfo xml for explainPlan
     * @sb
     */
    @Override
    public String getDatabaseInfo() {
        StringBuffer sb=new StringBuffer();
        sb.append("        <databaseInfo>\n");
        if(dbProperties!=null) {
            String t=dbProperties.getProperty("databaseInfo.title");
            if(t!=null)
                sb.append("          <title>").append(t).append("</title>\n");
            t=dbProperties.getProperty("databaseInfo.description");
            if(t!=null)
                sb.append("          <description>").append(t).append("</description>\n");
            t=dbProperties.getProperty("databaseInfo.author");
            if(t!=null)
                sb.append("          <author>").append(t).append("</author>\n");
            t=dbProperties.getProperty("databaseInfo.contact");
            if(t!=null)
                sb.append("          <contact>").append(t).append("</contact>\n");
            t=dbProperties.getProperty("databaseInfo.restrictions");
            if(t!=null)
                sb.append("          <restrictions>").append(t).append("</restrictions>\n");
        }
        sb.append("          <implementation version='1.1'>\n");
        sb.append("            <title>OCLC Research SRW Server version 1.1</title>\n");
        sb.append("            </implementation>\n");
        sb.append("          </databaseInfo>\n");
        return sb.toString();
    }


    /**
     * returns info about metaInfo for explainPlan.
     * Overwritten because schema 2.0 needs attribute dateModified
     * if element metaInfo is set. 
     * 
     * @return String databaseInfo xml for explainPlan
     * @sb
     */
    @Override
    public String getMetaInfo() {
        StringBuffer sb=new StringBuffer();
        boolean writeElement = false;
        sb.append("        <metaInfo>\n");
        if(dbProperties!=null) {
            String t=dbProperties.getProperty("metaInfo.dateModified");
            if(t!=null) {
                sb.append("          <dateModified>").append(t).append("</dateModified>\n");
                writeElement = true;
            }
            t=dbProperties.getProperty("metaInfo.aggregatedFrom");
            if(t!=null) {
                sb.append("          <aggregatedFrom>").append(t).append("</aggregatedFrom>\n");
            }
            t=dbProperties.getProperty("metaInfo.dateAggregated");
            if(t!=null) {
                sb.append("          <dateAggregated>").append(t).append("</dateAggregated>\n");
            }
        }
        sb.append("          </metaInfo>\n");
        if (writeElement) {
            return sb.toString();
        } else {
            return "";
        }
    }


    /**
     * returns info about indices in this database for explainPlan. Dynamically
     * reads all fields from lucene-index and appends them to the explainPlan if
     * prefix of fieldName (string that ends with a dot) is defined as
     * contextSet in properties (contextSet...). Dynamically adds sortKeywords
     * by selecting all fields that start with a prefix as given in property
     * sortSet.
     * 
     * @return String indexInfo xml for explainPlan
     * @sb
     */
    @Override
    public String getIndexInfo() {
        Enumeration enumer = dbProperties.propertyNames();
        HashSet<String> sets = new HashSet<String>();
        String index, indexSet, prop;
        StringBuffer sb = new StringBuffer("");
        HashSet<String> contextSets = new HashSet<String>();
        HashSet<String> sortSets = new HashSet<String>();
        Matcher contextSetMatcher = Constants.CONTEXT_SET_PATTERN.matcher("");
        Matcher sortSetMatcher = Constants.SORT_SET_PATTERN.matcher("");
        Matcher reservedSetMatcher = Constants.RESERVED_SET_PATTERN.matcher("");
        Matcher qualifierMatcher = Constants.QUALIFIER_PATTERN.matcher("");
        Matcher dotMatcher = Constants.DOT_PATTERN.matcher("");
        while (enumer.hasMoreElements()) {
            prop = (String) enumer.nextElement();
            // MIH: extract contextSetName
            // compare with fieldNames in LuceneIndex
            // if fieldName starts with <name>. that is contained in contextSets
            // then this field belongs to a contextSet
            if (contextSetMatcher.reset(prop).matches()) {
                contextSets.add(contextSetMatcher.group(1));
            }
            // MIH: extract sortSetName
            // compare with fieldNames in LuceneIndex
            // if fieldName starts with <name>. that is contained in sortSets
            // then this field belongs to a sortSet
            if (sortSetMatcher.reset(prop).matches()) {
                sortSets.add(sortSetMatcher.group(1));
            }
            if (qualifierMatcher.reset(prop).matches()) {
            	if (dotMatcher.reset(qualifierMatcher.group(1)).matches()) {
            		indexSet = dotMatcher.group(1);
            		index = dotMatcher.group(2);
            	} else {
            		indexSet = "local";
            		index = qualifierMatcher.group(1);
            	}
                if (!sets.contains(indexSet)) { // new set
                    sets.add(indexSet);
                }
                sb
                    .append("          <index>\n")
                    .append("            <title>").append(indexSet).append('.')
                    .append(index).append("</title>\n").append(
                        "            <map>\n").append(
                        "              <name set=\"").append(indexSet).append(
                        "\">").append(index).append("</name>\n").append(
                        "              </map>\n").append(
                        "            </index>\n");
            }
        }
        
        //Get index fields
        Collection<String> fieldList =
            ((EscidocTranslator) getCQLTranslator()).getIndexedFieldList();
        indexSet = null;
        index = null;
        StringBuffer sortKeywords = new StringBuffer("");
        if (fieldList != null) {
            for (String fieldName : fieldList) {
                if (!reservedSetMatcher.reset(fieldName).matches()) {
                    String prefix = "";
                    String indexName = fieldName;
                    if (dotMatcher.reset(fieldName).matches()) {
                        prefix = dotMatcher.group(1);
                        if (sortSets.contains(prefix)) {
                            sortKeywords.append("          <sortKeyword>").append(
                                    fieldName).append("</sortKeyword>\n");
                            continue;
                        }
                        if (!contextSets.contains(prefix)) {
                            prefix = "";
                        } else {
                            indexName = dotMatcher.group(2);
                        }
                    }
                    // get title from properties
                    String title =
                        dbProperties
                            .getProperty("description." + fieldName);
                    if (title == null || title.equals("")) {
                        title = fieldName;
                    }
                    sb
                        .append("          <index>\n")
                        .append("            <title>")
                        .append(title).append("</title>\n")
                        .append("            <map>\n")
                        .append("              <name set=\"")
                        .append(prefix)
                        .append("\">")
                        .append(indexName)
                        .append("</name>\n")
                        .append("              </map>\n")
                        .append("            </index>\n");
                }
            }
            
            if (sortKeywords.length() > 0) {
                sb.append(sortKeywords);
            }
        }
        if (sets != null && !sets.isEmpty()) {
            StringBuffer setsBuf = new StringBuffer("");
            for (String setName : sets) {
                setsBuf.append("          <set identifier=\"").append(
                    dbProperties.getProperty("contextSet." + setName)).append(
                    "\" name=\"").append(setName).append("\"/>\n");
            }
            sb.insert(0, setsBuf);
        }
        sb.insert(0, "         <indexInfo>\n");
        sb.append("          </indexInfo>\n");
        return sb.toString();
    }

}
