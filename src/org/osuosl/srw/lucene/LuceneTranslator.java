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

package org.osuosl.srw.lucene;

import gov.loc.www.zing.srw.ExtraDataType;
import gov.loc.www.zing.srw.ScanRequestType;
import gov.loc.www.zing.srw.TermType;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.StringTokenizer;

import org.apache.axis.types.NonNegativeInteger;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.lucene.document.Fieldable;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TermRangeQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.WildcardQuery;
import org.apache.lucene.store.FSDirectory;
import org.osuosl.srw.CQLTranslator;
import org.osuosl.srw.ResolvingQueryResult;
import org.osuosl.srw.SRWDiagnostic;
import org.z3950.zing.cql.CQLAndNode;
import org.z3950.zing.cql.CQLBooleanNode;
import org.z3950.zing.cql.CQLNode;
import org.z3950.zing.cql.CQLNotNode;
import org.z3950.zing.cql.CQLOrNode;
import org.z3950.zing.cql.CQLTermNode;

import de.escidoc.sb.srw.lucene.document.LazyFieldSelector;

import ORG.oclc.os.SRW.QueryResult;

/**
 * @author peter
 *         Date: Oct 25, 2005
 *         Time: 10:38:43 AM
 */
public class LuceneTranslator implements CQLTranslator {

    private static Log       log= LogFactory.getLog(LuceneTranslator.class);
    public static final String DEFAULT_ID_TERM = "uniqueidentifier";
    public static final String PROPERTY_INDEXPATH = "cqlTranslator.indexPath";
    public static final String PROPERTY_IDENTIFIER_TERM = "cqlTranslator.identifierTerm";

    /**
     *  IndexPath
     */
    private String indexPath;
    public String getIndexPath(){return indexPath;}
    public void setIndexPath(String inp){
        indexPath = inp;
    }

    /**
     *  IdentifierTerm
     */
    private String identifierTerm;
    public String getIdentifierTerm(){return identifierTerm;}
    public void setIdentifierTerm(String inp){
        identifierTerm = inp;
    }

    public LuceneTranslator() {
        identifierTerm = DEFAULT_ID_TERM;
    }

    public LuceneTranslator(String indexPath) throws IOException {
        this.indexPath = indexPath;
        identifierTerm = DEFAULT_ID_TERM;
    }

    public LuceneTranslator(String indexPath, String identifierTerm) throws IOException {
        this.indexPath = indexPath;
        this.identifierTerm = identifierTerm;
    }

    public void init(Properties properties) {
        String temp;

        temp = (String) properties.get(PROPERTY_INDEXPATH);
        if (temp != null && temp.trim().length() != 0) {
           indexPath = temp;
        }

        temp = (String) properties.get(PROPERTY_IDENTIFIER_TERM);
        if (temp != null && temp.trim().length() != 0) {
           identifierTerm = temp;
        }

    }

    public QueryResult search(CQLNode queryRoot, ExtraDataType extraDataType) throws SRWDiagnostic {

        String[] identifiers = null;
        IndexSearcher searcher = null;
        try {
            //convert the CQL search to lucene search
            Query query=makeQuery(queryRoot);
            log.info("lucene search="+query);

            // perform search
            searcher = new IndexSearcher(FSDirectory.open(
                    new File(indexPath)));
            TopDocs results = searcher.search(query, 1000000);
            int size = results.totalHits;

            log.info(size+" handles found");
            identifiers = new String[size];

            // now instantiate the results and put them into the response object
            for(int i = 0; i < size; i++ ) {
                org.apache.lucene.document.Document doc = 
                            searcher.doc(results.scoreDocs[i].doc, 
                            		new LazyFieldSelector());
                if (log.isDebugEnabled()) log.debug("identifierTerm: " + identifierTerm);
                Fieldable idField = doc.getFieldable(identifierTerm);
                if (log.isDebugEnabled()) log.debug("idField: " + idField);
                if (idField != null) {
                    if (log.isDebugEnabled()) log.debug("idField: " + idField.stringValue());
                    identifiers[i] = idField.stringValue();
                }
            }
        } catch (IOException e) {
            throw new SRWDiagnostic(SRWDiagnostic.GeneralSystemError, e.toString());
        } finally {
            if (searcher != null) {
                try {
                    searcher.close();
                } catch (IOException e) {
                    log.error("Exception while closing lucene index searcher", e);
                }
                searcher = null;
            }
        }

        return new ResolvingQueryResult(identifiers);
    }

    public TermType[] scan(
            CQLTermNode queryRoot, 
            ScanRequestType scanRequestType) 
                                throws Exception {
        return scan(queryRoot, scanRequestType.getExtraRequestData());
    }

    public TermType[] scan(CQLTermNode queryRoot, ExtraDataType extraDataType) throws Exception {

        TermType[] response = new TermType[0];
        Map termMap = new HashMap();
        IndexSearcher searcher = null;

        try {
            //convert the CQL search to lucene search
            Query query=makeQuery(queryRoot);
            log.info("lucene search="+query);

            /**
             * scan query should always be a single term, just get that term's qualifier
             */
            String searchField = queryRoot.getIndex();
            boolean exact = queryRoot.getRelation().toCQL().equalsIgnoreCase("exact");
            boolean any = queryRoot.getRelation().toCQL().equalsIgnoreCase("any");

            // perform search
            searcher = new IndexSearcher(FSDirectory.open(
                    new File(indexPath)));
            TopDocs results = searcher.search(query, 1000000);
            int size = results.scoreDocs.length;

            log.info(size+" handles found");

            if (size != 0) {
                // iterater through hits counting terms
                for(int i = 0; i < size; i++ ) {
                    org.apache.lucene.document.Document doc = 
                                searcher.doc(results.scoreDocs[i].doc, 
                                		new LazyFieldSelector());
                    Fieldable field = doc.getFieldable(searchField);

                    if (exact) {
                        // each field is counted as a term
                        countTerm(termMap, field.stringValue());

                    } else {
                        /**
                         * each word in the field is counted as a term.  A term should
                         * only be counted once per document so use a tokenizer and a Set
                         * to create a list of unique terms
                         *
                         * this is the default scan but can be explicitly invoked with the "any"
                         * keyword.
                         *
                         * example:   'dc.title any fish'
                         */
                        StringTokenizer tokenizer = new StringTokenizer(field.stringValue(), " ");
                        Set termSet = new HashSet();
                        while(tokenizer.hasMoreTokens()) {
                            termSet.add( tokenizer.nextToken() );
                        }
                        // count all terms
                        Iterator iter = termSet.iterator();
                        while (iter.hasNext()) {
                            String term = (String) iter.next();
                            countTerm(termMap, term);
                        }
                    }
                }

                // done counting terms in all documents, convert map to array
                response = (TermType[]) termMap.values().toArray(response);
            }

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (searcher != null) {
                searcher.close();
                searcher = null;
            }
        }

        return response;
    }

    /**
     * Counts the term.  If the term matches an existing term
     * it is added on to the count for that term.  If
     *
     * @param termMap - map of terms already counted
     * @param value - value of the term
     */
    private void countTerm(Map termMap, String value) {
        TermType termType = (TermType) termMap.get(value);
        if (termType == null) {
            // not found, create
            termType = new TermType();
            termType.setValue(value);
            termType.setNumberOfRecords(new NonNegativeInteger(Integer.toString(1)));
            termMap.put(value,termType);
        } else {
            NonNegativeInteger count = termType.getNumberOfRecords();
            int newValue = count.intValue() + 1;
            termType.setNumberOfRecords(new NonNegativeInteger(Integer.toString(newValue)));
        }
    }

    public Query makeQuery(CQLNode node) throws SRWDiagnostic{
        return makeQuery(node, null);
    }

    public Query makeQuery(CQLNode node, Query leftQuery) throws SRWDiagnostic{
        Query query = null;

        if(node instanceof CQLBooleanNode) {
            CQLBooleanNode cbn=(CQLBooleanNode)node;

            Query left = makeQuery(cbn.left);
            Query right = makeQuery(cbn.right, left);

            if(node instanceof CQLAndNode) {
                if (left instanceof BooleanQuery) {
                    query = left;
                    log.info("  Anding left and right");
                    AndQuery((BooleanQuery) left, right);
                } else {
                    query = new BooleanQuery();
                    log.info("  Anding left and right in new query");
                    AndQuery((BooleanQuery) query, left);
                    AndQuery((BooleanQuery) query, right);
                }

            } else if(node instanceof CQLNotNode) {

                if (left instanceof BooleanQuery) {
                    log.debug("  Notting left and right");
                    query = left;
                    NotQuery((BooleanQuery) left, right);
                } else {
                    query = new BooleanQuery();
                    log.debug("  Notting left and right in new query");
                    AndQuery((BooleanQuery) query, left);
                    NotQuery((BooleanQuery) query, right);
                }

            } else if(node instanceof CQLOrNode) {
                if (left instanceof BooleanQuery) {
                    log.debug("  Or'ing left and right");
                    query = left;
                    OrQuery((BooleanQuery) left, right);
                } else {
                    log.debug("  Or'ing left and right in new query");
                    query = new BooleanQuery();
                    OrQuery((BooleanQuery) query, left);
                    OrQuery((BooleanQuery) query, right);
                }
            } else {
                throw new RuntimeException("Unknown boolean");
            }

        } else if(node instanceof CQLTermNode) {
            CQLTermNode ctn=(CQLTermNode)node;

            String relation = ctn.getRelation().getBase();
            String index=ctn.getIndex();

            if (!index.equals("")) {
                if(relation.equals("=") || relation.equals("scr")) {
                    query = createTermQuery(index,ctn.getTerm(), relation);
                } else if (relation.equals("<")) {
                    // term is upperbound, exclusive
                    query = new TermRangeQuery(index, null, ctn.getTerm(), true, false);
                } else if (relation.equals(">")) {
                    // term is lowerbound, exclusive
                    query = new TermRangeQuery(index, ctn.getTerm(), null, false, true);
                } else if (relation.equals("<=")) {
                    // term is upperbound, inclusive
                    query = new TermRangeQuery(index, null, ctn.getTerm(), true, true);
                } else if (relation.equals(">=")) {
                    // term is lowerbound, inclusive
                    query = new TermRangeQuery(index, ctn.getTerm(), null, true, true);
                } else if (relation.equals("<>")) {
                    /**
                     * <> is an implicit NOT.
                     *
                     * For example the following statements are identical results:
                     *   foo=bar and zoo<>xar
                     *   foo=bar not zoo=xar
                     */

                    if (leftQuery == null) {
                        // first term in query create an empty Boolean query to NOT
                        query = new BooleanQuery();
                    } else {
                        if (leftQuery instanceof BooleanQuery) {
                            // left query is already a BooleanQuery use it
                            query = leftQuery;
                        } else {
                            // left query was not a boolean, create a boolean query
                            // and AND the left query to it
                            query = new BooleanQuery();
                            AndQuery((BooleanQuery)query, leftQuery);
                        }
                    }
                    //create a term query for the term then NOT it to the boolean query
                    Query termQuery = createTermQuery(index,ctn.getTerm(), relation);
                    NotQuery((BooleanQuery) query, termQuery);

                } else if (relation.equals("any")) {
                    //implicit or
                    query = createTermQuery(index,ctn.getTerm(), relation);

                } else if (relation.equals("all")) {
                    //implicit and
                    query = createTermQuery(index,ctn.getTerm(), relation);
                } else if (relation.equals("exact")) {
                    /**
                     * implicit and.  this query will only return accurate
                     * results for indexes that have been indexed using
                     * a non-tokenizing analyzer
                     */
                    query = createTermQuery(index,ctn.getTerm(), relation);
                } else {
                    //anything else is unsupported
                    throw new SRWDiagnostic(19, ctn.getRelation().getBase());
                }

            }
        } else {
            throw new SRWDiagnostic(47, "UnknownCQLNode: "+node+")");
        }
        if (query != null) {
            log.info("Query : " + query.toString());
        }
        return query;
    }

    public static Query createTermQuery(String field, String value, String relation) {

        Query termQuery = null;

        /**
         * check to see if there are any spaces.  If there are spaces each
         * word must be broken into a single term search and then all queries
         * must be combined using an and.
         */
        if (value.indexOf(" ") == -1) {
            // no space found, just create a single term search
            Term term = new Term(field, value.toLowerCase());
            if (value.indexOf("?") != -1 || value.indexOf("*")!=-1 ){
                termQuery = new WildcardQuery(term);
            } else {
                termQuery = new TermQuery(term);
            }

        } else {
            // space found, iterate through the terms to create a multiterm search

            if (relation == null || relation.equals("=") || relation.equals("<>") || relation.equals("exact")) {
                /**
                 * default is =, all terms must be next to eachother.
                 * <> uses = as its term query.
                 * exact is a phrase query
                 */
                PhraseQuery phraseQuery = new PhraseQuery();
                StringTokenizer tokenizer = new StringTokenizer(value, " ");
                while (tokenizer.hasMoreTokens()) {
                    String curValue = tokenizer.nextToken();
                    phraseQuery.add(new Term(field, curValue));
                }
                termQuery = phraseQuery;

            } else if(relation.equals("any")) {
                /**
                 * any is an implicit OR
                 */
                termQuery = new BooleanQuery();
                StringTokenizer tokenizer = new StringTokenizer(value, " ");
                while (tokenizer.hasMoreTokens()) {
                    String curValue = tokenizer.nextToken();
                    Query subSubQuery = createTermQuery(field, curValue, relation);
                    OrQuery((BooleanQuery) termQuery, subSubQuery);
                }

            } else if (relation.equals("all")) {
                /**
                 * any is an implicit AND
                 */
                termQuery = new BooleanQuery();
                StringTokenizer tokenizer = new StringTokenizer(value, " ");
                while (tokenizer.hasMoreTokens()) {
                    String curValue = tokenizer.nextToken();
                    Query subSubQuery = createTermQuery(field, curValue, relation);
                    AndQuery((BooleanQuery) termQuery, subSubQuery);
                }
            }

        }

        return termQuery;
    }

    /**
     * Join the two queries together with boolean AND
     * @param query
     * @param query2
     */
    public static void AndQuery(BooleanQuery query, Query query2) {
        /**
         * required = true (must match sub query)
         * prohibited = false (does not need to NOT match sub query)
         */
        query.add(query2, BooleanClause.Occur.MUST);
    }

    public static void OrQuery(BooleanQuery query, Query query2) {
        /**
         * required = false (does not need to match sub query)
         * prohibited = false (does not need to NOT match sub query)
         */
        query.add(query2, BooleanClause.Occur.SHOULD);
    }

    public static void NotQuery(BooleanQuery query, Query query2) {
        /**
         * required = false (does not need to match sub query)
         * prohibited = true (must not match sub query)
         */
        query.add(query2, BooleanClause.Occur.MUST_NOT);
    }

    private static void dumpQueryTree(CQLNode node) {
        if(node instanceof CQLBooleanNode) {
            CQLBooleanNode cbn=(CQLBooleanNode)node;
            dumpQueryTree(cbn.left);
            if(node instanceof CQLAndNode)
                log.info(" AND ");
            else if(node instanceof CQLNotNode)
                log.info(" NOT ");
            else if(node instanceof CQLOrNode)
                log.info(" OR ");
            else log.info(" UnknownBoolean("+cbn+") ");
            dumpQueryTree(cbn.right);
        }
        else if(node instanceof CQLTermNode) {
            CQLTermNode ctn=(CQLTermNode)node;
            log.info("term(qualifier=\""+ctn.getIndex()+"\" relation=\""+
                ctn.getRelation().getBase()+"\" term=\""+ctn.getTerm()+"\")");
        }
        else log.info("UnknownCQLNode("+node+")");
    }

}
