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

import gov.loc.www.zing.srw.*;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.axis.types.PositiveInteger;
import org.apache.axis.types.NonNegativeInteger;
import org.apache.axis.MessageContext;
import org.apache.axis.message.MessageElement;
import org.apache.axis.message.Text;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.z3950.zing.cql.CQLNode;
import org.z3950.zing.cql.CQLParseException;
import org.z3950.zing.cql.CQLTermNode;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

import java.util.*;
import java.io.StringReader;

import ORG.oclc.os.SRW.*;

/**
 * @author peter
 *         Date: Oct 21, 2005
 *         Time: 9:29:14 AM
 */
public class SRWDatabaseImpl extends ORG.oclc.os.SRW.SRWDatabase{

    public void addRenderer(String schemaName, String schemaID, Properties props) throws InstantiationException {
        // unused.
    }

    /**
     * ==========================================================
     */

    static Log log= LogFactory.getLog(SRWDatabaseImpl.class);
    public static final String DEFAULT_SCHEMA = "default";

    /**
     *  Resolver
     */
    private RecordResolver resolver;
    public RecordResolver getResolver(){return resolver;}
    public void setResolver(RecordResolver inp){
        resolver = inp;
    }

    /**
     *  CQLTranslator
     */
    private CQLTranslator cqlTranslator;
    public CQLTranslator getCQLTranslator(){return cqlTranslator;}
    public void setCQLTranslator(CQLTranslator inp){
        cqlTranslator = inp;
    }

    protected String schemaInfo = null;

    /**
     *  This method is never used, because we do not store results.
     */
    public QueryResult getQueryResult(String s, SearchRetrieveRequestType searchretrieverequesttype)
    throws InstantiationException {
        return null;
    }

    /**
     *  Not implemented.
     */
    public String getExtraResponseData(
            QueryResult queryresult, SearchRetrieveRequestType searchretrieverequesttype) {
        return null;
    }
    /**
     *  Not implemented. Instead use method that throws Exception.
     */
    public TermList getTermList(
            CQLTermNode cqltermnode, int i, int j, ScanRequestType scanrequesttype)  {
        return null;
    }

    /**
     *  This method throws Exception.
     */
    public TermList getTermList(
            CQLTermNode cqltermnode, ScanRequestType scanrequesttype) 
    throws Exception {
        TermList termList = new TermList();
        TermType[] terms = cqlTranslator.scan(cqltermnode, scanrequesttype);
        termList.setTerms(terms);
        return termList;
    }
    
    public ScanResponseType doRequest(ScanRequestType request) throws ServletException {

        CQLTermNode root=null;
        int maxTerms=9, position=5;
        ScanResponseType response=new ScanResponseType();
        try {
            //determine max number of terms
            PositiveInteger pi=request.getMaximumTerms();
            if(pi!=null) {
                maxTerms=pi.intValue();
                position=maxTerms/2+1;
            }

            // determine response position
            NonNegativeInteger nni=request.getResponsePosition();
            if(nni!=null)
                position=nni.intValue();

            log.info("maxTerms="+maxTerms+", position="+position);

            // get search and parse into CQL objects
            String scanTerm=request.getScanClause();
            try{
                if(scanTerm!=null)
                    log.info("scanTerm:\n"+ORG.oclc.util.Util.byteArrayToString(scanTerm.getBytes("UTF-8")));
            }catch(Exception e){}
            root = (CQLTermNode)parser.parse(scanTerm);

            // check validity of CQL
            String resultSetID=root.getResultSetName();
            if(resultSetID!=null) { // you can't scan on resultSetId!
                return diagnostic(SRWDiagnostic.UnsupportedIndex,
                        "cql.resultSetId", response);
            }

            TermList termList = getTermList(root, request);
            
            TermsType terms=new TermsType();
            terms.setTerm(termList.getTerms());
            response.setTerms(terms);
            return response;
        }

        catch(IllegalArgumentException e) {
            if(e.getMessage().equals("Illegal positionInResponse"))
                return diagnostic(SRWDiagnostic.ResponsePositionOutOfRange,
                        Integer.toString(position), response);
            log.error(e, e);
            //throw new RemoteException(e.getMessage());
            return diagnostic(SRWDiagnostic.GeneralSystemError, e.getMessage(), response);
        }

        catch(Exception e) {
            //throw new RemoteException(e.getMessage());
            return diagnostic(SRWDiagnostic.GeneralSystemError, e.getMessage(), response);
        }
    }


    public String getIndexInfo() {
        Enumeration     enumer=dbProperties.propertyNames();
        Hashtable       sets=new Hashtable();
        String          index, indexSet, prop;
        StringBuffer    sb=new StringBuffer("        <indexInfo>\n");
        StringTokenizer st;
        while(enumer.hasMoreElements()) {
            prop=(String)enumer.nextElement();
            if(prop.startsWith("qualifier.")) {
                st=new StringTokenizer(prop.substring(10));
                index=st.nextToken();
                st=new StringTokenizer(index, ".");
                if(st.countTokens()==1)
                    indexSet="local";
                else
                    indexSet=st.nextToken();
                index=st.nextToken();
                if(sets.get(indexSet)==null) {  // new set
                    sb.append("          <set identifier=\"")
                      .append(dbProperties.getProperty("contextSet."+indexSet))
                      .append("\" name=\"").append(indexSet).append("\"/>\n");
                    sets.put(indexSet, indexSet);
                }
                sb.append("          <index>\n")
                  .append("            <title>").append(indexSet).append('.').append(index).append("</title>\n")
                  .append("            <map>\n")
                  .append("              <name set=\"").append(indexSet).append("\">").append(index).append("</name>\n")
                  .append("              </map>\n")
                  .append("            </index>\n");
            }
        }
        sb.append("          </indexInfo>\n");
        return sb.toString();
    }


    public void init(String dbname, String srwHome, String dbHome, String dbPropertiesFileName, Properties dbProperties, HttpServletRequest request) throws Exception {
        /**
         * Initialize CQLTranslator
         */
        String translatorClassName = dbProperties.getProperty("cqlTranslator");
        cqlTranslator = (CQLTranslator) Class.forName(translatorClassName).newInstance();
        cqlTranslator.init(dbProperties);


        /**
         * Initialize Resolver
         */
        String resolverClassName = dbProperties.getProperty("recordResolver");
        resolver = (RecordResolver) Class.forName(resolverClassName).newInstance();
        resolver.init(dbProperties);

        /**
         * populate schemainfo
         */
        schemaInfo = resolver.getSchemaInfo().toString();

        /**
         * Set default schema
         */
        String defaultSchemaId=dbProperties.getProperty("defaultSchema");
        if (defaultSchemaId!=null) {
            schemas.put("default", defaultSchemaId);
        }

        /**
         * Initialize other properties
         */
        initDB(dbname, srwHome, dbHome, dbPropertiesFileName, dbProperties);

    }

    public boolean supportsSort() {
        return true;
    }

    public SearchRetrieveResponseType doRequest(SearchRetrieveRequestType request) throws ServletException {

        SearchRetrieveResponseType response = null; // search response
//        int resultSetTTL;                           // time result set should expire
//        String recordPacking;                       // how record is packed (xml|string)
//        String query;                               // Search String
//        String sortKeysString;                      // keys to sort on
//        PositiveInteger startRec;                   // record number to start with
//        ExtraDataType extraData = null;             // extra params sent with request
//        ResolvingQueryResult results;                        // results of search, array of Ids/handles
//        ResolvingQueryResult sortedResults;                  // sorted results, array of indexs to results

        try {
//            MessageContext msgContext=MessageContext.getCurrentContext();
//            response = new SearchRetrieveResponseType();
//            response.setNumberOfRecords(new NonNegativeInteger("0"));
//            startRec=request.getStartRecord();
//            extraData =request.getExtraRequestData();
//
//            /**
//             * Get schema name and verify its supported.
//             */
//            String schemaName=request.getRecordSchema();
//            if(schemaName==null)
//                schemaName="default";
//            log.info("recordSchema="+schemaName);
//
//            if (schemaName != null && !schemaName.equals(DEFAULT_SCHEMA)) {
//                if (!resolver.containsSchema(schemaName)) {
//                    log.error("no handler for schema "+schemaName);
//                    return diagnostic(SRWDiagnostic.UnknownSchemaForRetrieval,
//                            schemaName, response);
//                }
//            }
//
//            /**
//             * RecordXPath - UNSUPPORTED
//             */
//            if (request.getRecordXPath() != null && request.getRecordXPath().trim().length() != 0) {
//                return diagnostic(8, request.getRecordXPath(), response);
//            }
//
//            /**
//             * set result set TTL
//             */
//            if(request.getResultSetTTL()!=null) {
//                resultSetTTL=request.getResultSetTTL().intValue();
//            } else {
//                resultSetTTL=defaultResultSetTTL;
//            }
//
//
//            /**
//             * Set Record Packing
//             */
//            recordPacking=request.getRecordPacking();
//            if(recordPacking==null) {
//                if(msgContext!=null && msgContext.getProperty("sru")!=null)
//                    recordPacking="xml"; // default for sru
//                else
//                    recordPacking="string"; // default for srw
//            }
//
//            /**
//             * get sort keys
//             */
//            sortKeysString = request.getSortKeys();
//            if(sortKeysString!=null && sortKeysString.length()==0)
//                sortKeysString=null;
//
//            /**
//             * Parse and Execute Query
//             */
//            query=request.getQuery();
//            try{
//                log.info("search:\n"+ORG.oclc.util.Util.byteArrayToString(query.getBytes("UTF-8")));
//            }catch(Exception e){}
//
//            CQLNode queryRoot = null;
//            try {
//                queryRoot = parser.parse(escapeBackslash(query));
//            } catch (CQLParseException e) {
//                return diagnostic(SRWDiagnostic.QuerySyntaxError,e.getMessage(), response );
//            }
//
//            String resultSetID=queryRoot.getResultSetName();
//            if (resultSetID != null) {
//                // look for existing result set
//                log.info("resultSetID="+resultSetID);
//                results = (ResolvingQueryResult) oldResultSets.get(resultSetID);
//                if (results == null) {
//                    return diagnostic(SRWDiagnostic.ResultSetDoesNotExist,resultSetID, response);
//                }
//
//            } else {
//                if (sortKeysString!=null && startRec!=null && startRec.intValue()>1) {
//                    return diagnostic(
//                            SRWDiagnostic.SortUnsupportedWhenStartRecordNotOneAndQueryNotAResultSetId,
//                            null, response);
//                }
//
//
//                results = (ResolvingQueryResult) cqlTranslator.search(queryRoot, extraData);
//
//                results.setResolver(resolver);
//                results.setExtraDataType(extraData);
//
//                /**
//                 * if results were found save the result set and setup
//                 * the timer for when it expires
//                 *
//                 */
//                if(results.getNumberOfRecords()>0 && resultSetTTL>0) {
//                    resultSetID = makeResultSetID();
//                    oldResultSets.put(resultSetID, results);
//                    log.info("keeping resultSet '"+resultSetID+"' for "+resultSetTTL+ " seconds");
//                    timers.put(resultSetID, new Long(System.currentTimeMillis()+(resultSetTTL*1000)));
//                    response.setResultSetId(resultSetID);
//                    response.setResultSetIdleTime(new PositiveInteger(Integer.toString(resultSetTTL)));
//                }
//            }
//
//            int postings= (int) results.getNumberOfRecords();
//            response.setNumberOfRecords(new NonNegativeInteger(Long.toString(postings)));
//
//            /**
//             * Sort Records
//             */
//            log.info("'" + query + "'==> " + postings);
//
//            SRWSortTool sortTool=null;
//
//            sortedResults = null;
//            if(postings>0 && sortKeysString!=null && sortKeysString.length()>0) {
//
//                // look for an existing result set
//                sortedResults = (ResolvingQueryResult) results.getSortedResult(sortKeysString);
//
//                if(sortedResults == null) {
//                    /**
//                     * existing result set not found.  sort a new list
//                     */
//                    log.info("sorting resultSet");
//                    sortTool = new SRWSortTool();
//
//                    // create the sort keys
//                    sortTool.setSortKeys(sortKeysString);
//
//                    //iterate through list adding documents to the sort too
//                    RecordIterator iter = results.newRecordIterator(0,(int) results.getNumberOfRecords(),null);
//                    for(int i=0; iter.hasNext(); i++) {
//                        Record record = iter.nextRecord();
//                        if (record instanceof IdentifiableRecord) {
//                            sortTool.addEntry(((IdentifiableRecord)record).getIdentifier(), record);
//                        }
//                    }
//
//                    //sort
//                    sortedResults = new ResolvingQueryResult(sortTool.sort());
//                    sortedResults.setResolver(resolver);
//                    sortedResults.setExtraDataType(extraData);
//
//                    //store result set
//                    results.putSortedResult(sortKeysString, sortedResults);
//                }
//                else {
//                    log.info("reusing old sorted resultSet");
//                }
//                results = sortedResults;
//            }
//
//            int numRecs=defaultNumRecs;
//            NonNegativeInteger maxRecs=request.getMaximumRecords();
//            if(maxRecs!=null)
//                numRecs=(int)java.lang.Math.min(maxRecs.longValue(), maximumRecords);
//
//            int startPoint=1;
//            if(startRec!=null)
//                startPoint=(int)startRec.longValue();
//            if(postings>0 && startPoint>postings)
//                diagnostic(SRWDiagnostic.FirstRecordPositionOutOfRange, null, response);
//            if((startPoint-1+numRecs)>postings)
//                numRecs=postings-(startPoint-1);
//            if(postings>0 && numRecs==0)
//                response.setNextRecordPosition(new PositiveInteger("1"));
//            if(postings>0 && numRecs>0) { // render some records into SGML
//                RecordsType records=new RecordsType();
//                log.info("trying to get "+numRecs+
//                        " records starting with record "+startPoint+
//                        " from a set of "+postings+" records");
//                if(!recordPacking.equals("xml") &&
//                        !recordPacking.equals("string")) {
//                    return diagnostic(71, recordPacking, response);
//                }
//
//                records.setRecord(new RecordType[numRecs]);
//                Document               domDoc;
//                DocumentBuilder        db=null;
//                DocumentBuilderFactory dbf=null;
//                int                    i, listEntry=-1;
//                MessageElement         elems[];
//                RecordType             record;
//                StringOrXmlFragment    frag;
//                if(recordPacking.equals("xml")) {
//                    dbf=DocumentBuilderFactory.newInstance();
//                    dbf.setNamespaceAware(true);
//                    db=dbf.newDocumentBuilder();
//                }
//
//               /**
//                 * One at a time, retrieve and display the requested documents.
//                 */
//                RecordIterator list=null;
//                try {
//                    log.info("making RecordIterator, startPoint="+startPoint+", schemaID="+schemaName);
//                    list=results.recordIterator(startPoint-1, numRecs, schemaName);
//                }
//                catch(InstantiationException e) {
//                    diagnostic(SRWDiagnostic.GeneralSystemError,
//                            e.getMessage(), response);
//                }
//
//
//                for(i=0; list.hasNext(); i++) {
//                    try {
//
//                        Record rec = list.nextRecord();
//
//                        /**
//                         * create record container
//                         */
//                        record=new RecordType();
//                        record.setRecordPacking(recordPacking);
//                        frag=new StringOrXmlFragment();
//                        elems=new MessageElement[1];
//                        frag.set_any(elems);
//                        record.setRecordSchema(rec.getRecordSchemaID());
//
//                        if(recordPacking.equals("xml")) {
//                            domDoc=db.parse(new InputSource(new StringReader(rec.getRecord())));
//                            Element el=domDoc.getDocumentElement();
//                            log.debug("got the DocumentElement");
//                            elems[0]=new MessageElement(el);
//                            log.debug("put the domDoc into elems[0]");
//                        } else { // string
//                            Text t=new Text(rec.getRecord());
//                            elems[0]=new MessageElement(t);
//                        }
//
//                        record.setRecordData(frag);
//                        log.debug("setRecordData");
//
//                        record.setRecordPosition(new PositiveInteger(Integer.toString(startPoint+i)));
//                        records.setRecord(i, record);
//
//                    } catch (NoSuchElementException e) {
//                        log.error("Read beyond the end of list!!");
//                        log.error(e);
//                        break;
//                    }
//                    response.setRecords(records);
//                }
//                if(startPoint+i<=postings)
//                    response.setNextRecordPosition(new PositiveInteger(
//                            Long.toString(startPoint+i)));
//            }
//            log.debug("exit doRequest");
            return response;

        } catch (Exception e) {
            return diagnostic(SRWDiagnostic.GeneralSystemError, e.getMessage(), response);
        }

    }

    static protected String escapeBackslash(String s) {
        boolean      changed=false;
        char         c;
        StringBuffer sb=null;
        for(int i=0; i<s.length(); i++) {
            c=s.charAt(i);
            if(c=='\\') {
                if(!changed) {
                    if(i>0)
                        sb=new StringBuffer(s.substring(0, i));
                    else
                        sb=new StringBuffer();
                    changed=true;
                }
                sb.append("\\\\");
            }
            else
            if(changed)
                sb.append(c);
        }
        if(!changed)
            return s;
        return sb.toString();
    }




    /**
     *
     * @return fragment of xml containing schema info
     */
    public String getSchemaInfo() {
        return schemaInfo;
    }

}
