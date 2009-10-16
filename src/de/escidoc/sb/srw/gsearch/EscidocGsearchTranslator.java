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

package de.escidoc.sb.srw.gsearch;

import gov.loc.www.zing.srw.ExtraDataType;
import gov.loc.www.zing.srw.SearchRetrieveRequestType;
import gov.loc.www.zing.srw.TermType;

import java.io.ByteArrayInputStream;
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
import java.util.Vector;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexReader.FieldOption;
import org.apache.lucene.search.Hits;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.osuosl.srw.ResolvingQueryResult;
import org.osuosl.srw.SRWDiagnostic;
import org.z3950.zing.cql.CQLNode;
import org.z3950.zing.cql.CQLTermNode;

import ORG.oclc.os.SRW.QueryResult;
import de.escidoc.core.common.util.service.HttpRequester;
import de.escidoc.core.common.util.stax.StaxParser;
import de.escidoc.sb.srw.Constants;
import de.escidoc.sb.srw.EscidocTranslator;
import de.escidoc.sb.srw.stax.handler.SplitHandler;

/**
 * Class overwrites org.osuosl.srw.lucene.LuceneTranslator. This is done
 * because: -we dont retrieve and store all search-hits but only the ones
 * requested -we dont use result-sets -we do sorting while querying lucene and
 * not afterwards -we have to rewrite the CQLTermNodes because we have to
 * replace default search field cql.serverChoice with configured default-search
 * field -we have to analyze the terms with the analyzer -enable fuzzy search
 * 
 * @author MIH
 * @sb
 */
public class EscidocGsearchTranslator extends EscidocTranslator {

	/**
	 * construct.
	 * 
	 * @sb
	 */
	public EscidocGsearchTranslator() {
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
	public EscidocGsearchTranslator(final String indexPath) throws IOException {
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
	public EscidocGsearchTranslator(final String indexPath,
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
    public QueryResult search(final CQLNode queryRoot,
			final ExtraDataType extraDataType,
			final SearchRetrieveRequestType request) throws SRWDiagnostic {
		/**
		 * get indexName
		 */
		String indexName = getIndexPath().replaceFirst(".*(\\/|\\\\)", "");

		/**
		 * get query
		 */
		// convert the CQL search to lucene search
		// while doing that, call method get getAnalyzedCqlTermNode
		// Additionally replaces fieldname cql.serverChoice
		// (this is the case if user gives no field name)
		// with the defaultFieldName from configuration
		Query unanalyzedQuery = makeQuery(queryRoot);

		// Get gsearchSortString
		String gsearchSortString = getGsearchSortString(request.getSortKeys());

		// generate query
		StringBuffer parameters = new StringBuffer("?operation=gfindObjects");
		parameters.append("&indexName=").append(indexName);
		parameters.append("&query=").append(unanalyzedQuery.toString());
		if (request.getStartRecord() != null) {
			parameters.append("&hitPageStart=")
					.append(request.getStartRecord());
		}
		if (request.getMaximumRecords() != null) {
			parameters.append("&hitPageSize=").append(
					request.getMaximumRecords());
		}
		if (gsearchSortString != null && !gsearchSortString.equals("")) {
			parameters.append("&sortFields=").append(gsearchSortString);
		}

		String[] identifiers = null;
		try {
			HttpRequester httpRequester = new HttpRequester(
					Constants.GSEARCH_URL);
			String records = httpRequester.doGet(parameters.toString());
			StaxParser sp = new StaxParser();
			SplitHandler handler = new SplitHandler(sp, Constants.XML_HIT_PATH);
			sp.addHandler(handler);

			sp.parse(new ByteArrayInputStream(
					records.getBytes(Constants.CHARACTER_ENCODING)));

			Vector<String> parts = handler.getParts();
			identifiers = new String[parts.size()];
			int i = 0;
			for(String identifier : parts) {
				identifiers[i] = identifier;
				i++;
			}

		} catch (Exception e) {
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
    public TermType[] scan(final CQLTermNode queryRoot,
			final ExtraDataType extraDataType) throws Exception {

		TermType[] response = new TermType[0];
		Map termMap = new HashMap();
		IndexSearcher searcher = null;

		try {
			// convert the CQL search to lucene search
			Query query = makeQuery(queryRoot);
			log.info("lucene search=" + query);

			/**
			 * scan query should always be a single term, just get that term's
			 * qualifier
			 */
			String searchField = ((CQLTermNode) queryRoot).getIndex();
			boolean exact = ((CQLTermNode) queryRoot).getRelation().toCQL()
					.equalsIgnoreCase("exact");

			// perform search
			searcher = new IndexSearcher(getIndexPath());
			Hits results = searcher.search(query);
			int size = results.length();

			log.info(size + " handles found");

			if (size != 0) {
				// iterater through hits counting terms
				for (int i = 0; i < size; i++) {
					org.apache.lucene.document.Document doc = results.doc(i);

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

					} else {
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
						StringTokenizer tokenizer = new StringTokenizer(
								fieldValue.toString(), " ");
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

		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (searcher != null) {
				try {
					searcher.close();
				} catch (IOException e) {
					log.error("Exception while closing lucene index searcher",
							e);
				}
				searcher = null;
			}
		}

		return response;
	}

	/**
	 * Returns a list of all FieldNames currently in lucene-index that are
	 * indexed.
	 * 
	 * @return Collection all FieldNames currently in lucene-index that are
	 *         indexed.
	 * 
	 * @sb
	 */
	@Override
    public Collection<String> getIndexedFieldList() {
		Collection<String> fieldList = new ArrayList<String>();
		IndexReader reader = null;
		try {
			reader = IndexReader.open(getIndexPath());
			fieldList = reader.getFieldNames(FieldOption.INDEXED);
		} catch (Exception e) {
			log.error(e);
		} finally {
			if (reader != null) {
				try {
					reader.close();
				} catch (IOException e) {
					log.error("Exception while closing lucene index reader", e);
				}
				reader = null;
			}
		}
		return fieldList;
	}

	/**
	 * Returns a list of all FieldNames currently in lucene-index that are
	 * stored.
	 * 
	 * @return Collection all FieldNames currently in lucene-index that are
	 *         stored
	 * 
	 * @sb
	 */
	@Override
    public Collection<String> getStoredFieldList() {
		Collection<String> fieldList = new ArrayList<String>();
		IndexReader reader = null;
		try {
			reader = IndexReader.open(getIndexPath());
			// Hack, because its not possible to get all stored fields
			// of an index
			for (int i = 0; i < 10; i++) {
				try {
					Document doc = reader.document(i);
					List<Field> fields = doc.getFields();
					for (Field field : fields) {
						if (field.isStored()
								&& !fieldList.contains(field.name())) {
							fieldList.add(field.name());
						}
					}
				} catch (Exception e) {
					break;
				}
			}
		} catch (Exception e) {
			log.error(e);
		} finally {
			if (reader != null) {
				try {
					reader.close();
				} catch (IOException e) {
					log.error("Exception while closing lucene index reader", e);
				}
				reader = null;
			}
		}
		return fieldList;
	}

	/**
	 * Extracts sortKeys and convert them to gsearch-sortstring.
	 * 
	 * @param sortKeysString
	 *            String with sort keys
	 * @return String gsearch-sortstring
	 * 
	 * @sb
	 */
	private String getGsearchSortString(final String sortKeysString) {
		StringBuffer gsearchSortString = new StringBuffer("");
		String replacedSortKeysString = sortKeysString;
		if (replacedSortKeysString != null
				&& replacedSortKeysString.length() == 0) {
			replacedSortKeysString = null;
		}

		// extract sortKeys and fill them into StringBuffer according to
		// gsearch-spec
		String[] sortKeys = null;
		if (replacedSortKeysString != null) {
			sortKeys = replacedSortKeysString.split("\\s");
		}
		if (sortKeys != null && sortKeys.length > 0) {
			for (int i = 0; i < sortKeys.length; i++) {
				if (gsearchSortString.length() > 0) {
					gsearchSortString.append(";");
				}
				String sortField = sortKeys[i];
				String[] sortPart = sortField.split(",");
				if (sortPart != null && sortPart.length > 0) {
					gsearchSortString.append(sortPart[0]).append(",STRING");
					if (sortPart.length > 2 && sortPart[2].equals("0")) {
						gsearchSortString.append(",true");
					}
				}
			}
		}
		return gsearchSortString.toString();
	}

}
