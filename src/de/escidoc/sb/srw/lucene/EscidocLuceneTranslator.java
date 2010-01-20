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

package de.escidoc.sb.srw.lucene;

import gov.loc.www.zing.srw.ExtraDataType;
import gov.loc.www.zing.srw.SearchRetrieveRequestType;
import gov.loc.www.zing.srw.TermType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.StringTokenizer;

import org.apache.axis.types.NonNegativeInteger;
import org.apache.axis.types.PositiveInteger;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.Fieldable;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexReader.FieldOption;
import org.apache.lucene.queryParser.QueryParser;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.FieldComparatorSource;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Similarity;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;
import org.osuosl.srw.ResolvingQueryResult;
import org.osuosl.srw.SRWDiagnostic;
import org.z3950.zing.cql.CQLNode;
import org.z3950.zing.cql.CQLTermNode;

import ORG.oclc.os.SRW.QueryResult;
import de.escidoc.core.common.util.configuration.EscidocConfiguration;
import de.escidoc.sb.srw.Constants;
import de.escidoc.sb.srw.EscidocTranslator;
import de.escidoc.sb.srw.lucene.highlighting.SrwHighlighter;
import de.escidoc.sb.srw.lucene.queryParser.EscidocQueryParser;
import de.escidoc.sb.srw.lucene.sorting.EscidocSearchResultComparator;

/**
 * Class overwrites org.osuosl.srw.lucene.LuceneTranslator. This is done
 * because: 
 * -we dont retrieve and store all search-hits but only the ones
 * requested 
 * -we dont use result-sets 
 * -we do sorting while querying lucene and not afterwards 
 * -we have to rewrite the CQLTermNodes because we have to
 * replace default search field cql.serverChoice with configured default-search
 * field 
 * -we have to analyze the terms with the analyzer 
 * -enable fuzzy search
 * 
 * @author MIH
 * @sb
 */
public class EscidocLuceneTranslator extends EscidocTranslator {

    /**
     * IndexSearcher.
     */
    private IndexSearcher searcher = null;

    /**
     * @return String searcher.
     */
    public IndexSearcher getIndexSearcher() throws SRWDiagnostic {
        if (searcher == null) {
            try {
                setIndexSearcher(new IndexSearcher(FSDirectory.open(
                        new File(getIndexPath()))));
            } catch (Exception e) {
                throw new SRWDiagnostic(SRWDiagnostic.GeneralSystemError, 
                            "indexSearcher is null due to previous error");
            }
        }
        return searcher;
    }

    /**
     * @param inp
     *            searcher.
     */
    public void setIndexSearcher(final IndexSearcher inp) {
        searcher = inp;
    }

    /**
     * SrwHighlighter.
     */
    private SrwHighlighter highlighter = null;

    /**
     * @return String highlighter.
     */
    public SrwHighlighter getHighlighter() {
        return highlighter;
    }

    /**
     * @param inp
     *            highlighter.
     */
    public void setHighlighter(final SrwHighlighter inp) {
        highlighter = inp;
    }

    /**
     * Analyzer. Is static because it is used in overwritten static method
     * Default: StandardAnalyzer
     */
    private Analyzer analyzer;

    /**
     * @return String analyzer.
     */
    public Analyzer getAnalyzer() {
        return analyzer;
    }

    /**
     * @param inp
     *            analyzer.
     */
    public void setAnalyzer(final Analyzer inp) {
        analyzer = inp;
    }

    /**
     * Comparator for custom sorting of search-result.
     * Default: EscidocSearchResultComparator
     */
    private FieldComparatorSource comparator = null;

    /**
     * @return String comparator.
     */
    public FieldComparatorSource getComparator() {
        return comparator;
    }

    /**
     * @param inp comparator.
     */
    public void setComparator(final FieldComparatorSource inp) {
    	comparator = inp;
    }

    /**
     * Similarity for custom ranking of search-result.
     */
    private Similarity similarity = null;

    /**
     * @return String similarity.
     */
    public Similarity getSimilarity() {
        return similarity;
    }

    /**
     * @param inp similarity.
     */
    public void setSimilarity(final Similarity inp) {
        similarity = inp;
    }

    /**
     * Force score-calculation, even for wildcard- and range-queries.
     * (Slows down search)
     */
    private boolean forceScoring = false;

    /**
     * @return String similarity.
     */
    public boolean getForceScoring() {
        return forceScoring;
    }

    /**
     * @param inp forceScoring.
     */
    public void setForceScoring(final boolean inp) {
        forceScoring = inp;
    }

    /**
     * construct.
     * 
     * @sb
     */
    public EscidocLuceneTranslator() {
        setIdentifierTerm(DEFAULT_ID_TERM);
    }

    /**
     * construct with path to lucene-index.
     * 
     * @param indexPath
     *            path to lucene-index
     * @throws IOException
     *             e
     * @sb
     */
    public EscidocLuceneTranslator(final String indexPath) throws IOException {
        setIndexPath(indexPath);
        setIdentifierTerm(DEFAULT_ID_TERM);
    }

    /**
     * construct with path to lucene-index and identifierTerm(field that
     * contains xml that gets returned by search).
     * 
     * @param indexPath
     *            path to lucene-index
     * @param identifierTerm
     *            field that contains xml that gets returned by search
     * @throws IOException
     *             e
     * @sb
     */
    public EscidocLuceneTranslator(final String indexPath,
        final String identifierTerm) throws IOException {
        setIndexPath(indexPath);
        setIdentifierTerm(identifierTerm);
    }

    /**
     * initialize.
     * 
     * @param properties
     *            properties
     * 
     * @sb
     */
    @Override
    public void init(final Properties properties) {
        String temp;

        temp = (String) properties.get(PROPERTY_INDEXPATH);
        if (temp != null && temp.trim().length() != 0) {
            temp = replaceEnvVariables(temp);
            setIndexPath(temp);
        }

        temp = (String) properties.get(PROPERTY_DEFAULT_INDEX_FIELD);
        if (temp != null && temp.trim().length() != 0) {
            setDefaultIndexField(temp);
        }

        temp = (String) properties.get(PROPERTY_DEFAULT_NUMBER_OF_RECORDS);
        if (temp != null && temp.trim().length() != 0) {
            setDefaultNumberOfRecords(temp);
        }

        temp = (String) properties.get(PROPERTY_IDENTIFIER_TERM);
        if (temp != null && temp.trim().length() != 0) {
            setIdentifierTerm(temp);
        }

        temp = (String) properties.get(Constants.PROPERTY_HIGHLIGHTER);
        if (temp != null && temp.trim().length() != 0) {
            try {
                highlighter =
                    (SrwHighlighter) Class.forName(temp).newInstance();
                highlighter.setProperties(properties);
            }
            catch (Exception e) {
                log.error(e);
                highlighter = null;
            }
        }

        temp = (String) properties.get(Constants.PROPERTY_ANALYZER);
        if (temp != null && temp.trim().length() != 0) {
            try {
                analyzer =
                    (Analyzer) Class.forName(temp).newInstance();
            }
            catch (Exception e) {
                log.error(e);
                analyzer = new StandardAnalyzer(Version.LUCENE_CURRENT);
            }
        }

        temp = (String) properties.get(Constants.PROPERTY_COMPARATOR);
        if (temp != null && temp.trim().length() != 0) {
            try {
                comparator = (FieldComparatorSource) Class.forName(temp).newInstance();
            }
            catch (Exception e) {
                log.error(e);
                comparator = new EscidocSearchResultComparator();
            }
        }

        temp = (String) properties.get(Constants.PROPERTY_SIMILARITY);
        if (temp != null && temp.trim().length() != 0) {
            try {
                similarity = (Similarity) Class.forName(temp).newInstance();
                searcher.setSimilarity(similarity);
            }
            catch (Exception e) {
                log.error(e);
            }
        }

        temp = (String) properties.get(Constants.PROPERTY_FORCE_SCORING);
        if (temp != null && temp.trim().length() != 0) {
            forceScoring = new Boolean(temp).booleanValue();
        }

    }

    /**
     * New implemented method in this class with
     * SearchRetrieveRequestType-object. SearchRetrieveRequestType-object is
     * needed to get startRecord, maximumRecords, sortKeys. Method searches
     * Lucene-index with sortKeys, only gets requested records from Hits, get
     * data from identifierTerm-field and puts it into queryResult.
     * 
     * @param queryRoot
     *            cql-query
     * @param extraDataType
     *            extraDataType
     * @param request
     *            SearchRetrieveRequestType
     * @return QueryResult queryResult-Object
     * @throws SRWDiagnostic
     *             e
     * 
     * @sb
     */
    @Override
    public QueryResult search(
        final CQLNode queryRoot, final ExtraDataType extraDataType,
        final SearchRetrieveRequestType request) throws SRWDiagnostic {
        // Increase maxClauseCount of BooleanQuery for Wildcard-Searches
        BooleanQuery.setMaxClauseCount(Integer.MAX_VALUE);

        Sort sort = null;

        String[] identifiers = null;

        try {
            if (request.getSortKeys() != null 
                    && !request.getSortKeys().equals("")) {
                // Get Lucene Sort-Object (CQL 1.0)
                sort = getLuceneSortObject(request.getSortKeys());
            } else {
                // Get Lucene Sort-Object (CQL 2.0)
                sort = makeSort(queryRoot, null, comparator);
            }

            // convert the CQL search to lucene search
            // Additionally replaces fieldname cql.serverChoice
            // (this is the case if user gives no field name)
            // with the defaultFieldName from configuration
            Query unanalyzedQuery = makeQuery(queryRoot);

            // rewrite query to analyzed query
            // EscidocQueryParser also analyzes wildcard-queries
            // If you want scoring with wildcard-queries,
            // set property cqlTranslator.forceScoring in your configuration
            // NOTE: this will slow down search approx by factor 10!!
            QueryParser parser =
                new EscidocQueryParser(
                        getDefaultIndexField(), analyzer, forceScoring);
            Query query = parser.parse(unanalyzedQuery.toString());
            
            //execute fuzzy-queries with lower maxClauseCount
            if (query.toString().indexOf("~") > -1) {
                BooleanQuery.setMaxClauseCount(
                        Constants.FUZZY_BOOLEAN_MAX_CLAUSE_COUNT);
            }

            log.info("escidoc lucene search=" + query);

            TopDocs results = null;
            int size = 0;
            //calculate maximum hits
            int maximumHits = 0;
            if (request.getStartRecord() != null) {
                maximumHits += request.getStartRecord().intValue();
            }
            if (request.getMaximumRecords() != null) {
                maximumHits += request.getMaximumRecords().intValue();
            } else {
                maximumHits += getDefaultNumberOfRecords();
            }
            // perform sorted search?
            if (sort == null) {
                getIndexSearcher().setDefaultFieldSortScoring(false, false);
                results = getIndexSearcher().search(query, maximumHits);
            }
            else {
                getIndexSearcher().setDefaultFieldSortScoring(true, false);
                results = getIndexSearcher().search(query, null, maximumHits, sort);
            }
            size = results.totalHits;

            // initialize Highlighter
            if (highlighter != null) {
                highlighter.initialize(getIndexPath(), query);
            }

            log.info(size + " handles found");

            /**
             * get startRecord
             */
            if (size == 0 && request.getStartRecord() != null) {
                throw new SRWDiagnostic(
                    SRWDiagnostic.FirstRecordPositionOutOfRange,
                    "StartRecord > endRecord");
            }
            int startRecord = 1;
            PositiveInteger startRecordInt = request.getStartRecord();
            if (startRecordInt != null) {
                startRecord = startRecordInt.intValue();
            }

            /**
             * get endRecord
             */
            int maxRecords = DIAGNOSTIC_CODE_TWENTY;
            int endRecord = 0;
            NonNegativeInteger maxRecordsInt = request.getMaximumRecords();
            if (maxRecordsInt != null) {
                maxRecords = maxRecordsInt.intValue();
            }
            endRecord = startRecord - 1 + maxRecords;
            if (endRecord > size) {
                endRecord = size;
            }
            if (endRecord < startRecord && endRecord > 0) {
                throw new SRWDiagnostic(
                    SRWDiagnostic.FirstRecordPositionOutOfRange,
                    "StartRecord > endRecord");
            }

            // now instantiate the results and put them into the response object
            if (log.isDebugEnabled()) {
                log.debug("iterating resultset from record " + startRecord
                    + " to " + endRecord);
            }
            identifiers = createIdentifiers(results, startRecord, endRecord);
            
        }
        catch (Exception e) {
            throw new SRWDiagnostic(SRWDiagnostic.GeneralSystemError, e
                .toString());
        }
        return new ResolvingQueryResult(identifiers);
    }

    /**
     * Scan-Request. Scans index for terms that are alphabetically around the
     * search-term.
     * 
     * @param queryRoot
     *            cql-query
     * @param extraDataType
     *            extraDataType
     * @return TermType[] Array of TermTypes
     * @throws Exception
     *             e
     * 
     * @sb
     */
    @Override
    public TermType[] scan(
        final CQLTermNode queryRoot, final ExtraDataType extraDataType)
        throws Exception {

        TermType[] response = new TermType[0];
        Map termMap = new HashMap();

        try {
            // convert the CQL search to lucene search
            Query unanalyzedQuery = makeQuery(queryRoot);

            // rewrite query to analyzed query
            QueryParser parser =
                new EscidocQueryParser(
                        getDefaultIndexField(), analyzer);
            Query query = parser.parse(unanalyzedQuery.toString());
            log.info("lucene search=" + query);

            /**
             * scan query should always be a single term, just get that term's
             * qualifier
             */
            String searchField = queryRoot.getIndex();
            boolean exact =
                queryRoot
                    .getRelation().toCQL().equalsIgnoreCase("exact");

            // perform search
            TopDocs results = getIndexSearcher().search(query, 1000);
            int size = results.scoreDocs.length;

            log.info(size + " handles found");

            if (size != 0) {
                // iterater through hits counting terms
                for (int i = 0; i < size; i++) {
                    org.apache.lucene.document.Document doc = 
                            getIndexSearcher().doc(results.scoreDocs[i].doc);

                    // MIH: Changed: get all fileds and not only one.
                    // Concat fieldValues into fieldString
                    Field[] fields = doc.getFields(searchField);
                    StringBuffer fieldValue = new StringBuffer("");
                    if (fields != null) {
                        for (int j = 0; j < fields.length; j++) {
                            fieldValue.append(fields[j].stringValue()).append(
                                " ");
                        }
                    }
                    // /////////////////////////////////////////////////////////

                    if (exact) {
                        // each field is counted as a term
                        countTerm(termMap, fieldValue.toString());

                    }
                    else {
                        /**
                         * each word in the field is counted as a term. A term
                         * should only be counted once per document so use a
                         * tokenizer and a Set to create a list of unique terms
                         * 
                         * this is the default scan but can be explicitly
                         * invoked with the "any" keyword.
                         * 
                         * example: 'dc.title any fish'
                         */
                        StringTokenizer tokenizer =
                            new StringTokenizer(fieldValue.toString(), " ");
                        Set termSet = new HashSet();
                        while (tokenizer.hasMoreTokens()) {
                            termSet.add(tokenizer.nextToken());
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

        }
        catch (IOException e) {
            e.printStackTrace();
        }

        return response;
    }

    /**
     * Returns a list of all FieldNames currently in lucene-index
     * that are indexed.
     * 
     * @return Collection all FieldNames currently in lucene-index
     * that are indexed.
     * 
     * @sb
     */
    @Override
    public Collection<String> getIndexedFieldList() {
        Collection<String> fieldList = new ArrayList<String>();
        IndexReader reader = null;
        try {
            reader = IndexReader.open(FSDirectory.open(
                    new File(getIndexPath())));
            fieldList = reader.getFieldNames(FieldOption.INDEXED);
        }
        catch (Exception e) {
            log.error(e);
        }
        finally {
            if (reader != null) {
                try {
                    reader.close();
                }
                catch (IOException e) {
                    log.error("Exception while closing lucene index reader", e);
                }
                reader = null;
            }
        }
        return fieldList;
    }

    /**
     * Returns a list of all FieldNames currently in lucene-index
     * that are stored.
     * 
     * @return Collection all FieldNames currently in lucene-index
     * that are stored
     * 
     * @sb
     */
    @Override
    public Collection<String> getStoredFieldList() {
        Collection<String> fieldList = new ArrayList<String>();
        IndexReader reader = null;
        try {
            reader = IndexReader.open(FSDirectory.open(
                    new File(getIndexPath())));
            //Hack, because its not possible to get all stored fields
            //of an index
            for (int i = 0; i < 10 ; i++) {
            	try {
                	Document doc = reader.document(i);
                	List<Fieldable> fields = doc.getFields();
                	for (Fieldable field : fields) {
                		if (field.isStored() && !fieldList.contains(field.name())) {
                			fieldList.add(field.name());
                		}
                	}
            	} catch (Exception e) {}
            }
        }
        catch (Exception e) {
            log.error(e);
        }
        finally {
            if (reader != null) {
                try {
                    reader.close();
                }
                catch (IOException e) {
                    log.error("Exception while closing lucene index reader", e);
                }
                reader = null;
            }
        }
        return fieldList;
    }

    /**
     * Creates the identifiers (search-xmls that are returned as response) with
     * extra information (highlight, score....)
     * 
     * @param hits
     *            Lucene Hit-Documents
     * @param startRecord
     *            startRecord
     * @param endRecord endRecord
     * @return String[] with search-result xmls
     * @throws Exception
     *             e
     * 
     * @sb
     */
    private String[] createIdentifiers(
                    final TopDocs hits, 
                    final int startRecord, 
                    final int endRecord)
                                throws Exception {
        String[] searchResultXmls = new String[hits.totalHits];

        for (int i = startRecord - 1; i < endRecord; i++) {
            //get next hit
            org.apache.lucene.document.Document doc = 
                        getIndexSearcher().doc(hits.scoreDocs[i].doc);

            //initialize surrounding xml
            StringBuffer complete = new StringBuffer(
                    Constants.SEARCH_RESULT_START_ELEMENT);
            
            //append score-element
            complete.append(Constants.SCORE_START_ELEMENT);
            complete.append(hits.scoreDocs[i].score);
            complete.append(Constants.SCORE_END_ELEMENT);
            
            //append highlighting
            if (highlighter != null) {
                String highlight = null;
                try {
                    highlight = highlighter.getFragments(doc);
                } catch (Exception e) {
                    log.error(e);
                }
                if (highlight != null && !highlight.equals("")) {
                    complete.append(highlight);
                }
            }
            
            //get field containing the search-result-xml from lucene for this hit
            if (log.isDebugEnabled()) {
                log.debug("identifierTerm: " + getIdentifierTerm());
            }
            Field idField = doc.getField(getIdentifierTerm());
            String idFieldStr = null;
            if (idField != null) {
                idFieldStr = idField.stringValue();
                if (StringUtils.isNotBlank(idFieldStr)) {
                    if (idFieldStr.trim().startsWith("&")) {
                        idFieldStr = StringEscapeUtils.unescapeXml(idFieldStr);
                    }

                    //append search-result-xml from lucene
                    complete.append(idFieldStr).append("\n");
                }
            }
            
            if (StringUtils.isBlank(idFieldStr)) {
                complete.append("<default-search-result>")
                    .append(doc.getField("PID"))
                    .append("</default-search-result>");
            }

            //close surrounding xml
            complete.append(Constants.SEARCH_RESULT_END_ELEMENT);
            
            //append xml to search-results
            searchResultXmls[i] = complete.toString();
        }
        return searchResultXmls;
    }

    /**
     * Extracts sortKeys from request-parameter 
     * and fills them into a Lucene Sort-Object.
     * 
     * @param sortKeysString
     *            String with sort keys
     * @return Sort Lucene Sort-Object
     * 
     * @sb
     */
    private Sort getLuceneSortObject(final String sortKeysString) {
        Sort sort = null;
        String replacedSortKeysString = sortKeysString;
        if (replacedSortKeysString != null 
            && replacedSortKeysString.length() == 0) {
            replacedSortKeysString = null;
        }

        // extract sortKeys and fill them into a Lucene Sort-Object
        Collection<String> sortFields = new ArrayList<String>();
        if (replacedSortKeysString != null) {
            String[] sortKeys = replacedSortKeysString.split("\\s+");
            for (int i = 0; i < sortKeys.length; i++) {
                sortFields.add(sortKeys[i]);
            }
        }
        if (sortFields != null && !sortFields.isEmpty()) {
            int i = 0;
            SortField[] sortFieldArr = new SortField[sortFields.size()];
            for (String sortField : sortFields) {
                String[] sortPart = sortField.split(",");
                if (sortPart != null && sortPart.length > 0) {
                    if (sortPart.length > 2 && sortPart[2].equals("0")) {
                        //check for sorting for score
                        if (sortPart[0].equals(
                                Constants.RELEVANCE_SORT_FIELD_NAME)) {
                            sortFieldArr[i] = SortField.FIELD_SCORE;
                        } else {
                            if (comparator == null) {
                                sortFieldArr[i] = new SortField(sortPart[0], SortField.STRING, true);
                            } else {
                                sortFieldArr[i] = new SortField(sortPart[0], comparator, true);
                            }
                        }
                    }
                    else {
                        //check for sorting for score
                        if (sortPart[0].equals(
                                Constants.RELEVANCE_SORT_FIELD_NAME)) {
                            sortFieldArr[i] = SortField.FIELD_SCORE;
                        } else {
                            if (comparator == null) {
                                sortFieldArr[i] = new SortField(sortPart[0], SortField.STRING);
                            } else {
                                sortFieldArr[i] = new SortField(sortPart[0], comparator);
                            }
                        }
                    }
                    i++;
                }
            }
            sort = new Sort(sortFieldArr);
        }
        return sort;
    }

    /**
     * Extracts sortKeys from cql-query 
     * and fills them into a Lucene Sort-Object.
     * 
     * @param cqlNode cqlNode
     * @return Sort Lucene Sort-Object
     * 
     * @sb
     */
    private Sort getLuceneSortObject(final CQLNode cqlNode) {
        Sort sort = null;
        return sort;
    }

}
