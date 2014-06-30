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

package de.escidoc.sb.srw.lucene.queryParser;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.TermAttribute;
import org.apache.lucene.queryParser.ParseException;
import org.apache.lucene.queryParser.QueryParser;
import org.apache.lucene.search.MultiTermQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermRangeQuery;
import org.apache.lucene.util.Version;

import de.escidoc.sb.srw.Constants;

/**
 * Custom queryParser for eSciDoc. Also analyzes wildcard-queries which is not
 * done by the normal QueryParser
 * 
 * @author MIH
 * @sb
 */
public class EscidocQueryParser extends QueryParser {

    private static Log log = LogFactory.getLog(EscidocQueryParser.class);

    /**
     * Analyzer used for wildcard queries.
     */
    private Analyzer wildcardAnalyzer;
    
    /**
     * Constructs a query parser.
     * 
     * @param field
     *            the default field for query terms.
     * @param analyzer
     *            used to find terms in the query text.
     */
    public EscidocQueryParser(final String field, final Analyzer analyzer) {
        super(Constants.LUCENE_VERSION, field, analyzer);
        super.setAutoGeneratePhraseQueries(true);
        setWildcardAnalyzer(analyzer);
    }

    /**
     * Constructs a query parser.
     * 
     * @param field
     *            the default field for query terms.
     * @param analyzer
     *            used to find terms in the query text.
     * @param forceScoring
     *            used to indicate that scoring has to get forced.
     */
    public EscidocQueryParser(
            final String field, 
            final Analyzer analyzer, 
            final boolean forceScoring) {
        super(Constants.LUCENE_VERSION, field, analyzer);
        super.setAutoGeneratePhraseQueries(true);
        setWildcardAnalyzer(analyzer);
        if (forceScoring) {
            super.setMultiTermRewriteMethod(
                    MultiTermQuery.SCORING_BOOLEAN_QUERY_REWRITE);
        }
    }

    /**
     * Constructs a query parser.
     * 
     * @param field
     *            the default field for query terms.
     * @param analyzer
     *            used to find terms in the query text.
     * @param wildcardAnalyzer
     *            used to find terms in the query text with wildcards.
     */
    public EscidocQueryParser(final String field, final Analyzer analyzer,
        final Analyzer wildcardAnalyzer) {
        super(Constants.LUCENE_VERSION, field, analyzer);
        super.setAutoGeneratePhraseQueries(true);
        setWildcardAnalyzer(wildcardAnalyzer);
    }

    /**
     * Constructs a TermRangeQuery.
     * TermRangeQuery with null as upper 
     * or lower boundary creates QueryString containing *.
     * So if reparsing this String, handle * as null
     * 
     * @param field
     *            the field.
     * @param part1
     *            lower bound of range.
     * @param part2
     *            upper bound of range.
     * @param inclusive
     *            boundary-inclusive?.
     */
    protected Query newRangeQuery(
            String field, String part1, String part2, boolean inclusive) {
        //TermRangeQuery with null as upper or lower boundary creates QueryString containing *
        //So if reparsing this String, handle * as null
        if (part1 != null && part1.equals("*")) {
            part1 = null;
        }
        if (part2 != null && part2.equals("*")) {
            part2 = null;
        }
        TermRangeQuery query = new TermRangeQuery(
                field, part1, part2, inclusive, inclusive, super.getRangeCollator());
        query.setRewriteMethod(super.getMultiTermRewriteMethod());
        return query;
    }

    /**
     * Called when parser parses an input term token that contains one or more
     * wildcard characters (? and *), but is not a prefix term token (one that
     * has just a single * character at the end)
     * <p>
     * Depending on analyzer and settings, a wildcard term may (most probably
     * will) be lower-cased automatically. It <b>will</b> go through the
     * Wildcard Analyzer if set, otherwise through the Default Analyzer.
     * <p>
     * Overrides super class, by passing terms through analyzer.
     * 
     * @param field
     *            Name of the field query will use.
     * @param termStr
     *            Term token that contains one or more wild card characters (?
     *            or *), but is not simple prefix term
     * 
     * @return Resulting {@link Query} built for the term
     * @throws ParseException
     *             e
     */
    protected Query getWildcardQuery(final String field, final String termStr)
        throws ParseException {
        if (getWildcardAnalyzer() == null) {
            return super.getWildcardQuery(field, termStr);
        }
        List tlist = new ArrayList();
        List wlist = new ArrayList();
        /*
         * somewhat a hack: find/store wildcard chars in order to put them back
         * after analyzing
         */
        boolean isWithinToken =
            (!termStr.startsWith("?") && !termStr.startsWith("*"));
        StringBuffer tmpBuffer = new StringBuffer();
        char[] chars = termStr.toCharArray();
        for (int i = 0; i < termStr.length(); i++) {
            if (chars[i] == '?' || chars[i] == '*') {
                if (isWithinToken) {
                    tlist.add(tmpBuffer.toString());
                    tmpBuffer.setLength(0);
                }
                isWithinToken = false;
            }
            else {
                if (!isWithinToken) {
                    wlist.add(tmpBuffer.toString());
                    tmpBuffer.setLength(0);
                }
                isWithinToken = true;
            }
            tmpBuffer.append(chars[i]);
        }
        if (isWithinToken) {
            tlist.add(tmpBuffer.toString());
        }
        else {
            wlist.add(tmpBuffer.toString());
        }
        // get Wildcard-Analyzer and tokenize the term
        TokenStream source =
            getWildcardAnalyzer().tokenStream(field, new StringReader(termStr));
        TermAttribute termAtt = source.addAttribute(TermAttribute.class);

        int countTokens = 0;
        try {
            while (source.incrementToken()) {
                String termText = new String(termAtt.termBuffer(),0,termAtt.termLength());
                if (!"".equals(termText)) {
                    try {
                        tlist.set(countTokens++, termText);
                    }
                    catch (IndexOutOfBoundsException ioobe) {
                        countTokens = -1;
                    }
                }
            }
        } catch (IOException e) {
            throw new ParseException(e.getMessage());
        }
        try {
            source.close();
        }
        catch (IOException e) {
            log.info(e);
        }
        if (countTokens != tlist.size()) {
            /*
             * this means that the analyzer used either added or consumed
             * (common for a stemmer) tokens, and we can't build a WildcardQuery
             */
            return super.getWildcardQuery(field, termStr);
        }
        if (tlist.size() == 0) {
            return null;
        }
        else if (tlist.size() == 1) {
            if (wlist != null && wlist.size() == 1) {
                /*
                 * if wlist contains one wildcard, it must be at the end,
                 * because: 1) wildcards are not allowed in 1st position of a
                 * term by QueryParser 2) if wildcard was *not* in end, there
                 * would be *two* or more tokens
                 */
                return super.getWildcardQuery(field, (String) tlist.get(0)
                    + (((String) wlist.get(0)).toString()));
            }
            else {
                /*
                 * we should never get here! if so, this method was called with
                 * a termStr containing no wildcard ...
                 */
                throw new IllegalArgumentException(
                    "getWildcardQuery called without wildcard");
            }
        }
        else {
            /*
             * the term was tokenized, let's rebuild to one token with wildcards
             * put back in postion
             */
            StringBuffer sb = new StringBuffer();
            for (int i = 0; i < tlist.size(); i++) {
                sb.append((String) tlist.get(i));
                if (wlist != null && wlist.size() > i) {
                    sb.append((String) wlist.get(i));
                }
            }
            return super.getWildcardQuery(field, sb.toString());
        }
    }

    /**
     * Called when parser parses an input term token that uses prefix notation;
     * that is, contains a single '*' wildcard character as its last character.
     * Since this is a special case of generic wildcard term, and such a query
     * can be optimized easily, this usually results in a different query
     * object.
     * <p>
     * Depending on analyzer and settings, a prefix term may (most probably
     * will) be lower-cased automatically. It <b>will</b> go through the
     * Wildcard Analyzer if set, otherwise through the Default Analyzer.
     * <p>
     * Overrides super class, by passing terms through analyzer.
     * 
     * @param field
     *            Name of the field query will use.
     * @param termStr
     *            Term token to use for building term for the query (<b>without</b>
     *            trailing '*' character!)
     * 
     * @return Resulting {@link Query} built for the term
     * @throws ParseException
     *             e
     */
    protected Query getPrefixQuery(final String field, final String termStr)
        throws ParseException {
        if (getWildcardAnalyzer() == null) {
            return super.getPrefixQuery(field, termStr);
        }
        // get Wildcard-Analyzer and tokenize the term
        TokenStream source =
            getWildcardAnalyzer().tokenStream(field, new StringReader(termStr));
        TermAttribute termAtt = source.addAttribute(TermAttribute.class);
        List tlist = new ArrayList();
        try {
            while (source.incrementToken()) {
                tlist.add(new String(termAtt.termBuffer(),0,termAtt.termLength()));
            }
        } catch (IOException e) {
            throw new ParseException(e.getMessage());
        }
        try {
            source.close();
        }
        catch (IOException e) {
            log.info(e);
        }
        if (tlist.size() == 1) {
            return super.getPrefixQuery(field, (String) tlist.get(0));
        }
        else {
            /*
             * this means that the analyzer used consumed the only token we had,
             * and we can't build a PrefixQuery
             */
            return super.getPrefixQuery(field, termStr);
        }
    }

    /**
     * @return the wildcardAnalyzer
     */
    public Analyzer getWildcardAnalyzer() {
        return wildcardAnalyzer;
    }

    /**
     * @param wildcardAnalyzer
     *            the wildcardAnalyzer to set
     */
    public void setWildcardAnalyzer(final Analyzer wildcardAnalyzer) {
        this.wildcardAnalyzer = wildcardAnalyzer;
    }

}
