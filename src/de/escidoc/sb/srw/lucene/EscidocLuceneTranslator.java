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
import gov.loc.www.zing.srw.ScanRequestType;
import gov.loc.www.zing.srw.SearchRetrieveRequestType;
import gov.loc.www.zing.srw.TermType;
import gov.loc.www.zing.srw.utils.Stream;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Properties;

import org.apache.axis.types.NonNegativeInteger;
import org.apache.axis.types.PositiveInteger;
import org.apache.commons.collections.map.LRUMap;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Fieldable;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.IndexReader.FieldOption;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermDocs;
import org.apache.lucene.index.TermEnum;
import org.apache.lucene.queryParser.QueryParser;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.EscidocTopDocsCollector;
import org.apache.lucene.search.EscidocTopFieldCollector;
import org.apache.lucene.search.EscidocTopScoreDocCollector;
import org.apache.lucene.search.FieldComparatorSource;
import org.apache.lucene.search.Filter;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.QueryWrapperFilter;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Similarity;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.util.Version;
import org.osuosl.srw.ResolvingQueryResult;
import org.osuosl.srw.SRWDiagnostic;
import org.z3950.zing.cql.CQLNode;
import org.z3950.zing.cql.CQLTermNode;

import ORG.oclc.os.SRW.QueryResult;
import de.escidoc.core.common.util.service.UserContext;
import de.escidoc.sb.srw.Constants;
import de.escidoc.sb.srw.EscidocTranslator;
import de.escidoc.sb.srw.PermissionFilterGenerator;
import de.escidoc.sb.srw.SearchExtraData;
import de.escidoc.sb.srw.lucene.document.LazyFieldSelector;
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
 * -check for permission-filtering
 * 
 * @author MIH
 */
public class EscidocLuceneTranslator extends EscidocTranslator {


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
     * @return boolean forceScoring.
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
     * Expand query with filter for permission.
     * only works if permission-filtering fields are indexed.
     */
    private boolean permissionFiltering = false;

    /**
     * @return boolean permissionFiltering.
     */
    public boolean getPermissionFiltering() {
        return permissionFiltering;
    }

    /**
     * @param inp permissionFiltering.
     */
    public void setPermissionFiltering(final boolean inp) {
        permissionFiltering = inp;
    }
    
    /**
     * filter out latestReleased version if
     * latestRelease and latestVersion exist in index.
     */
    private boolean filterLatestRelease = false;

    /**
     * @return boolean filterLatestRelease.
     */
    public boolean getFilterLatestRelease() {
        return filterLatestRelease;
    }

    /**
     * @param inp filterLatestRelease.
     */
    public void setFilterLatestRelease(final boolean inp) {
        filterLatestRelease = inp;
    }
    
    /**
     * @return IndexSearcher searcher.
     */
    public IndexSearcher getSearcher(final String indexPath) 
                        throws IOException, CorruptIndexException {
        return IndexSearcherCache.getInstance().getIndexSearcher(indexPath);
    }

    /**
     * @param IndexSearcher searcher.
     */
    public synchronized void releaseSearcher(final IndexSearcher searcher) {
    	if (searcher != null) {
            try {
    			searcher.getIndexReader().decRef();
    		} catch (IOException e) {
    		    log.error("couldnt dereference IndexReader", e);
    		}
    	}
    }

    /**
     * construct.
     * 
     * @sb
     */
    public EscidocLuceneTranslator() {
        setIdentifierTerm(DEFAULT_ID_TERM);
    }
    
    private PermissionFilterGenerator permissionFilterGenerator = 
                                    new LucenePermissionFilterGenerator();

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

        temp = (String) properties.get(PROPERTY_DEFAULT_NUMBER_OF_SCAN_TERMS);
        if (temp != null && temp.trim().length() != 0) {
            setDefaultNumberOfScanTerms(temp);
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
                analyzer = new StandardAnalyzer(Constants.LUCENE_VERSION);
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
            }
            catch (Exception e) {
                log.error(e);
            }
        }

        temp = (String) properties.get(Constants.PROPERTY_FORCE_SCORING);
        if (temp != null && temp.trim().length() != 0) {
            forceScoring = new Boolean(temp).booleanValue();
        }

        temp = (String) properties.get(Constants.PROPERTY_PERMISSION_FILTERING);
        if (temp != null && temp.trim().length() != 0) {
            permissionFiltering = new Boolean(temp).booleanValue();
        }

        temp = (String) properties.get(Constants.PROPERTY_FILTER_LATEST_RELEASE);
        if (temp != null && temp.trim().length() != 0) {
            filterLatestRelease = new Boolean(temp).booleanValue();
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
        final SearchRetrieveRequestType request, final String dbName) 
                                                    throws SRWDiagnostic {
        long time = 0;
        if (log.isInfoEnabled()) {
            time = System.currentTimeMillis();
        }
        // Increase maxClauseCount of BooleanQuery for Wildcard-Searches
        BooleanQuery.setMaxClauseCount(Integer.MAX_VALUE);
        
        //Fill extraSearchData in Object
        SearchExtraData searchExtraData = new SearchExtraData(extraDataType);
        
        Sort sort = null;

        Stream[] identifiers = null;
        IndexSearcher searcher = null;
        
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
            if (log.isInfoEnabled()) {
                log.info("unanalyzedQuery  " 
                        + unanalyzedQuery.toString());
                log.info("query converted at " 
                        + (System.currentTimeMillis() - time) + " ms");
            }

            //execute fuzzy-queries with lower maxClauseCount
            if (unanalyzedQuery.toString().indexOf("~") > -1) {
                BooleanQuery.setMaxClauseCount(
                        Constants.FUZZY_BOOLEAN_MAX_CLAUSE_COUNT);
            }

            // rewrite query to analyzed query
            // EscidocQueryParser also analyzes wildcard-queries
            // If you want scoring with wildcard-queries,
            // set property cqlTranslator.forceScoring in your configuration
            // NOTE: this will slow down search approx by factor 10!!
            QueryParser parser =
                new EscidocQueryParser(
                        getDefaultIndexField(), analyzer, forceScoring);
            Query query = parser.parse(unanalyzedQuery.toString());
            if (log.isInfoEnabled()) {
                log.info("query after parsing: " + query.toString());
            }
            Filter permissionFilter = null;
            if (permissionFiltering && !searchExtraData.isSkipPermissions()) {
                if (log.isInfoEnabled()) {
                    log.info("starting getting permission filter query at " 
                            + (System.currentTimeMillis() - time) + " ms");
                }

                if (log.isInfoEnabled()) {
                    log.info("direct pre permission-filter at " 
                            + (System.currentTimeMillis() - time) + " ms");
                    log.info("calling permissionFilterGenerator with dbName:" 
                            + dbName + ",handle:" + UserContext.getHandle() 
                            + ",userId:" + searchExtraData.getUserId() 
                            + ",roleId:" + searchExtraData.getRoleId());
                }
                String permissionFilterStr = permissionFilterGenerator
                        .getPermissionFilter(dbName, UserContext.getHandle(), 
                            searchExtraData.getUserId(), searchExtraData.getRoleId());
                if (log.isInfoEnabled()) {
                    log.info("direct post permission-filter at " 
                            + (System.currentTimeMillis() - time) + " ms");
                }
                if (StringUtils.isNotEmpty(permissionFilterStr)) {
                    permissionFilter = new QueryWrapperFilter(parser.parse(permissionFilterStr));
                    if (log.isInfoEnabled()) {
                    	log.info("permission-filter:" + permissionFilterStr);
                    }
                }
            }
            
            searcher = getSearcher(getIndexPath());
            //check if custom scoring should be done
            if (similarity != null) {
                searcher.setSimilarity(similarity);
            }

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
            
            if (log.isInfoEnabled()) {
                log.info("search query preparation finished at " 
                            + (System.currentTimeMillis() - time) + " ms");
            }
            // perform sorted search?
            if (sort == null) {
                searcher.setDefaultFieldSortScoring(false, false);
                if (filterLatestRelease 
                    && !searchExtraData.isSkipFilterLatestRelease()) {
                    EscidocTopDocsCollector<ScoreDoc> collector = 
                        EscidocTopScoreDocCollector.create(
                                maximumHits, true, searcher.getIndexReader(), 
                                Constants.DUPLICATE_IDENTIFIER_FIELD, 
                                Constants.DUPLICATE_DISTINGUISHER_FIELD, 
                                Constants.DISTINGUISHER_PRIORITY_VAL);
                    searcher.search(query, permissionFilter, collector);
                    results = collector.topDocs();
                    
                } 
                else {
                    results = searcher.search(query, permissionFilter, maximumHits);
                }
            }
            else {
                searcher.setDefaultFieldSortScoring(forceScoring, false);
                if (filterLatestRelease && !searchExtraData.isSkipFilterLatestRelease()) {
                    EscidocTopDocsCollector collector = EscidocTopFieldCollector.create(
                            sort, maximumHits, true, forceScoring, 
                            false, false, searcher.getIndexReader(),
                            Constants.DUPLICATE_IDENTIFIER_FIELD, 
                            Constants.DUPLICATE_DISTINGUISHER_FIELD, 
                            Constants.DISTINGUISHER_PRIORITY_VAL);
                    searcher.search(query, permissionFilter, collector);
                    results = collector.topDocs();
                }
                else {
                    results = searcher.search(query, permissionFilter, maximumHits, sort);
                }
            }
            size = results.totalHits;
            if (log.isInfoEnabled()) {
                log.info("search finished at " 
                            + (System.currentTimeMillis() - time) + " ms");
                log.info(size + " handles found");
            }

            // initialize Highlighter
            if (highlighter != null 
                && !searchExtraData.isOmitHighlighting()) {
                try {
                    highlighter.initialize(searcher, query);
                } catch (Exception e) {
                    log.error(e);
                }
            }

            if (log.isInfoEnabled()) {
                log.info("highlighter-initialization finished at " 
                        + (System.currentTimeMillis() - time) + " ms");
            }

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
            int maxRecords = getDefaultNumberOfRecords();
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
            if (log.isInfoEnabled()) {
                log.info("iterating resultset from record " + startRecord
                    + " to " + endRecord);
            }
            identifiers = createIdentifiers(
                searcher, results, searchExtraData, startRecord, endRecord);
            if (log.isInfoEnabled()) {
                log.info("identifier creation finished at " 
                            + (System.currentTimeMillis() - time) + " ms");
            }
        }
        catch (Exception e) {
        	if (identifiers != null) {
    			for (int i = 0; i < identifiers.length; i++) {
    				if (identifiers[i] != null) {
    					try {
    						identifiers[i].close();
    					} catch (IOException e1) {
    						log.info("couldnt close stream");
    					}
    				}
    			}
        	}
            if (log.isInfoEnabled()) {
                log.info(e.toString());
            }
            throw new SRWDiagnostic(SRWDiagnostic.GeneralSystemError, e
                .toString());
        } finally {
            releaseSearcher(searcher);
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
	public TermType[] scan(final CQLTermNode queryRoot,
			final ScanRequestType scanRequestType) throws Exception {

		String searchField = queryRoot.getIndex();
		String searchTerm = queryRoot.getTerm();

		int responsePosition = 1;
		//discard responsePosition if term is empty
		if (scanRequestType.getResponsePosition() != null
				&& searchTerm != null && !searchTerm.isEmpty()) {
			responsePosition = scanRequestType.getResponsePosition().intValue();
		}
		
		int maximumTerms = getDefaultNumberOfScanTerms();
		if (scanRequestType.getMaximumTerms() != null) {
			maximumTerms = scanRequestType.getMaximumTerms().intValue();
		}

		boolean exact = queryRoot.getRelation().toCQL()
				.equalsIgnoreCase("exact");

		if (!exact) {
			return scanByTermList(responsePosition, maximumTerms, searchField,
					searchTerm);

		} else {
			return scanBySearch(queryRoot, maximumTerms, searchField);
		}

	}

	/**
	 * Scans index for terms by getting terms with IndexReader.
	 * 
	 * @param responsePosition
	 *            position of searchTerm in response
	 * @param maximumTerms
	 *            maximum Terms to return
	 * @param searchField
	 *            field containing the terms
	 * @param searchTerm
	 *            searchTerm
	 * @return TermType[] Array of TermTypes
	 * @throws Exception
	 *             e
	 * 
	 */
	private TermType[] scanByTermList(final int responsePosition,
			final int maximumTerms, final String searchField,
			final String searchTerm) throws Exception {
		TermType[] response = new TermType[0];
		Collection<TermType> termList = new ArrayList<TermType>();
		IndexSearcher searcher = null;
		int termCounter = 0;
		TermEnum terms = null;
		try {
			searcher = getSearcher(getIndexPath());
			terms = searcher.getIndexReader().terms(
					new Term(searchField, searchTerm));
			if (terms.term() == null) {
				return (TermType[]) termList.toArray(response);
			}
			String firstTerm = terms.term().text();
			if (responsePosition < 2) {
				// just read termList starting with searchTerm
				if (responsePosition == 1) {
					if (hasTermDocs(terms.term(), searcher)) {
						String term = terms.term().text();
						String field = terms.term().field();
						int freq = terms.docFreq();
						if (field.equals(searchField)) {
							termList.add(fillTermType(term, freq));
							termCounter++;
						}
					}
				}
				while (terms.next() && terms.term().field().equals(searchField)
						&& termCounter < maximumTerms) {
					if (hasTermDocs(terms.term(), searcher)) {
						String term = terms.term().text();
						int freq = terms.docFreq();
						termList.add(fillTermType(term, freq));
						termCounter++;
					}
				}
				return (TermType[]) termList.toArray(response);

			} else {
				// get complete termList of search-field.
				// cache terms in list before search-term
				// because also Terms occurring before searchTerm have to be in
				// list.
				boolean termReached = false;
				LRUMap prev = new LRUMap(responsePosition - 1);
				terms = searcher.getIndexReader().terms(
						new Term(searchField, ""));
				if (terms.term() == null) {
					return (TermType[]) termList.toArray(response);
				}
				if (hasTermDocs(terms.term(), searcher)) {
					String term = terms.term().text();
					String field = terms.term().field();
					int freq = terms.docFreq();
					if (term.equals(firstTerm)) {
						termReached = true;
					}
					if (field.equals(searchField)) {
						if (termReached) {
							termList.add(fillTermType(term, freq));
							termCounter++;
						} else {
							prev.put(term, fillTermType(term, freq));
						}
					}
				}
				while (terms.next() && terms.term().field().equals(searchField)
						&& termCounter < maximumTerms) {
					if (hasTermDocs(terms.term(), searcher)) {
						String term = terms.term().text();
						int freq = terms.docFreq();
						if (term.equals(firstTerm)) {
							Collection<TermType> col = prev.values();
							for (TermType termType : col) {
								if (termCounter >= maximumTerms) {
									break;
								}
								termList.add(termType);
								termCounter++;
							}
							termReached = true;
						}
						if (termReached && termCounter < maximumTerms) {
							termList.add(fillTermType(term, freq));
							termCounter++;
						} else {
							prev.put(term, fillTermType(term, freq));
						}
					}
				}
				return (TermType[]) termList.toArray(response);
			}
		} catch (Exception e) {
			throw new SRWDiagnostic(SRWDiagnostic.GeneralSystemError,
					e.toString());
		} finally {
			releaseSearcher(searcher);
			if (terms != null) {
				try {
					terms.close();
				} catch (Exception e) {
					// Do Nothing
				}
			}
		}
	}

	/**
	 * Workaround, because TermEnum in Lucene also contains terms of deleted
	 * documents. Check if Term occurs in non-deleted Documents.
	 * 
	 * @param term
	 *            Term
	 * @return boolean true if Term occurs in non-deleted Documents
	 * 
	 */
	private boolean hasTermDocs(final Term term, final IndexSearcher searcher)
			throws IOException {
		TermDocs termDocs = null;
		try {
			termDocs = searcher.getIndexReader().termDocs(term);
			if (termDocs == null || !termDocs.next()) {
				return false;
			}
			return true;
		} finally {
			if (termDocs != null) {
				try {
					termDocs.close();
				} catch (Exception e) {
					// Do nothing
				}
			}
		}
	}

	/**
	 * Fill TermType-Object with given Values.
	 * 
	 * @param term
	 *            term-value
	 * @param freq
	 *            number of lucene-documents where term appears.
	 * @return TermType TermType
	 * 
	 */
	private TermType fillTermType(final String term, final int freq) {
		TermType termType = new TermType();
		termType.setValue(term);
		termType.setNumberOfRecords(new NonNegativeInteger(Integer
				.toString(freq)));
		return termType;
	}

	/**
	 * Scans index for terms by first searching with given CQL-Query and then
	 * reading values of search-field.
	 * 
	 * @param queryRoot
	 *            CQL-Query
	 * @param maximumTerms
	 *            maximum Terms to return
	 * @param searchField
	 *            field containing the terms
	 * @return TermType[] Array of TermTypes
	 * @throws Exception
	 *             e
	 * 
	 */
	private TermType[] scanBySearch(final CQLTermNode queryRoot,
			final int maximumTerms, final String searchField) throws Exception {
		TermType[] response = new TermType[0];
		Collection<TermType> termList = new ArrayList<TermType>();
		IndexSearcher searcher = null;
		try {
			// convert the CQL search to lucene search
			Query unanalyzedQuery = makeQuery(queryRoot);

			// rewrite query to analyzed query
			QueryParser parser = new EscidocQueryParser(getDefaultIndexField(),
					analyzer);
			Query query = parser.parse(unanalyzedQuery.toString());
			if (log.isInfoEnabled()) {
				log.info("lucene search=" + query);
			}

			searcher = getSearcher(getIndexPath());
			TopDocs results = searcher.search(query, maximumTerms);
			int size = results.scoreDocs.length;

			if (log.isInfoEnabled()) {
				log.info(size + " handles found");
			}

			if (size != 0) {
				// iterate through hits counting terms
				for (int i = 0; i < size; i++) {
					org.apache.lucene.document.Document doc = searcher.doc(
							results.scoreDocs[i].doc, new LazyFieldSelector());

					// MIH: Changed: get all fields and not only one.
					// Concat fieldValues into fieldString
					Fieldable[] fields = doc.getFieldables(searchField);
					StringBuffer fieldValue = new StringBuffer("");
					if (fields != null) {
						for (int j = 0; j < fields.length; j++) {
							fieldValue.append(fields[j].stringValue()).append(
									" ");
						}
					}
					// /////////////////////////////////////////////////////////

					// each field is counted as a term
					termList.add(fillTermType(fieldValue.toString(), 1));
				}
			}

			// done counting terms in all documents, convert map to array
			response = (TermType[]) termList.toArray(response);
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			releaseSearcher(searcher);
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
        IndexSearcher searcher = null;
        try {
            searcher = getSearcher(getIndexPath());
            fieldList = searcher.getIndexReader()
                        .getFieldNames(FieldOption.INDEXED);
            Collection<String> toRemove = new ArrayList<String>();
            for (String fieldName : fieldList) {
                if (fieldName.startsWith("permissions-filter")) {
                    toRemove.add(fieldName);
                }
            }
            fieldList.removeAll(toRemove);
        }
        catch (Exception e) {
            log.error(e);
        } finally {
            releaseSearcher(searcher);
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
        IndexSearcher searcher = null;
        try {
            searcher = getSearcher(getIndexPath());
            //Hack, because its not possible to get all stored fields
            //of an index
            for (int i = 0; i < 10 ; i++) {
            	try {
                	Document doc = searcher.getIndexReader().document(i);
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
        } finally {
            releaseSearcher(searcher);
        }
        return fieldList;
    }

    /**
     * Creates the identifiers (search-xmls that are returned as response) with
     * extra information (highlight, score....)
     * 
     * @param searcher
     *            Lucene IndexSearcher
     * @param hits
     *            Lucene TopDocs
     * @param startRecord
     *            startRecord
     * @param endRecord endRecord
     * @return String[] with search-result xmls
     * @throws Exception
     *             e
     * 
     * @sb
     */
    private Stream[] createIdentifiers(
                    final IndexSearcher searcher,
                    final TopDocs hits, 
                    final SearchExtraData searchExtraData,
                    final int startRecord, 
                    final int endRecord)
                                throws Exception {
        Stream[] searchResultXmls = new Stream[hits.totalHits];

        long time = 0;
        long docTime = 0;
        long docTime2 = 0;
        long docTime3 = 0;
        long docTime4 = 0;
        for (int i = startRecord - 1; i < endRecord; i++) {
            //get next hit
            long docTime1 = 0;
            if (log.isInfoEnabled()) {
                docTime1 = System.currentTimeMillis();
            }
            org.apache.lucene.document.Document doc = 
                        searcher.doc(hits.scoreDocs[i].doc, 
                            new LazyFieldSelector());
            if (log.isInfoEnabled()) {
                docTime += (System.currentTimeMillis() - docTime1);
            }
            
            searchResultXmls[i] = new Stream();

            //initialize surrounding xml
            searchResultXmls[i].write(Constants.SEARCH_RESULT_START_ELEMENT.getBytes(Constants.CHARACTER_ENCODING));
            
            //append score-element
            searchResultXmls[i].write(Constants.SCORE_START_ELEMENT.getBytes(Constants.CHARACTER_ENCODING));
            if (log.isInfoEnabled()) {
                docTime1 = System.currentTimeMillis();
            }
            searchResultXmls[i].write(Float.toString(hits.scoreDocs[i].score).getBytes(Constants.CHARACTER_ENCODING));
            if (log.isInfoEnabled()) {
                docTime2 += (System.currentTimeMillis() - docTime1);
            }
            searchResultXmls[i].write(Constants.SCORE_END_ELEMENT.getBytes(Constants.CHARACTER_ENCODING));
            
            //append highlighting
            long time1 = 0;
            if (log.isInfoEnabled()) {
                time1 = System.currentTimeMillis();
            }
            if (highlighter != null 
                && !searchExtraData.isOmitHighlighting()) {
                String highlight = null;
                try {
                    highlight = highlighter.getFragments(doc, hits.scoreDocs[i].doc);
                } catch (Exception e) {
                    log.error(e);
                }
                if (highlight != null && !highlight.equals("")) {
                	searchResultXmls[i].write(highlight.getBytes(Constants.CHARACTER_ENCODING));
                }
            }
            if (log.isInfoEnabled()) {
                time += (System.currentTimeMillis() - time1);
            }
            
            //get field containing the search-result-xml from lucene for this hit
            if (log.isInfoEnabled()) {
                docTime1 = System.currentTimeMillis();
            }
            Fieldable idField = doc.getFieldable(getIdentifierTerm());
            String idFieldStr = null;
            if (idField != null) {
                idFieldStr = idField.stringValue().trim();
            }
            if (log.isInfoEnabled()) {
                docTime3 += (System.currentTimeMillis() - docTime1);
            }

            if (log.isInfoEnabled()) {
                docTime1 = System.currentTimeMillis();
            }
			if (StringUtils.isNotBlank(idFieldStr)) {
				if (idFieldStr.startsWith("&")) {
					idFieldStr = StringEscapeUtils.unescapeXml(idFieldStr);
				}

				if (idFieldStr.startsWith("<?xml")) {
					idFieldStr = idFieldStr.replaceFirst("<\\?xml.*?>", "");
				}

				// append search-result-xml from lucene
				searchResultXmls[i].write(idFieldStr
						.getBytes(Constants.CHARACTER_ENCODING));
				searchResultXmls[i].write("\n"
						.getBytes(Constants.CHARACTER_ENCODING));
			} else {
            	searchResultXmls[i].write(Constants.DEFAULT_SEARCH_RESULT_START_ELEMENT.getBytes(Constants.CHARACTER_ENCODING));
            	searchResultXmls[i].write(doc.getFieldable("PID").stringValue().getBytes(Constants.CHARACTER_ENCODING));
            	searchResultXmls[i].write(Constants.DEFAULT_SEARCH_RESULT_END_ELEMENT.getBytes(Constants.CHARACTER_ENCODING));
			}
            
            if (log.isInfoEnabled()) {
                docTime4 += (System.currentTimeMillis() - docTime1);
            }

            //close surrounding xml
            searchResultXmls[i].write(Constants.SEARCH_RESULT_END_ELEMENT.getBytes(Constants.CHARACTER_ENCODING));
            searchResultXmls[i].lock();

        }
        if (log.isInfoEnabled()) {
            log.info("highlighting-time was " + time + " ms");
            log.info("hits.scoreDocs[i].doc-time was " + docTime + " ms");
            log.info("hits.scoreDocs[i].score-time was " + docTime2 + " ms");
            log.info("doc.getField(getIdentifierTerm())-time was " + docTime3 + " ms");
            log.info("Stream-writing-time was " + docTime4 + " ms");
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
