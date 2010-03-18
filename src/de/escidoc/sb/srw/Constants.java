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

import java.util.regex.Pattern;

/**
 * Constants for Search.
 * 
 * @author MIH
 */
public class Constants {

    public static final String CHARACTER_ENCODING = "UTF-8";

    public static final int FUZZY_BOOLEAN_MAX_CLAUSE_COUNT = 10000;
    
    public static final Pattern CONTEXT_SET_PATTERN = Pattern.compile("contextSet\\.(.*)");
    
    public static final Pattern RESERVED_SET_PATTERN = Pattern.compile(
            "xml_representation.*|xml_metadata.*|stored_fulltext.*|stored_filename.*");

    public static final Pattern SORT_SET_PATTERN = Pattern.compile("sortSet\\.(.*)");

    public static final Pattern QUALIFIER_PATTERN = Pattern.compile("qualifier\\.(.*)");

    public static final Pattern DOT_PATTERN = Pattern.compile("(.*?)\\.(.*)");

    public static final String GSEARCH_URL = "http://localhost:8080/fedoragsearch/rest";

    public static final String XML_HIT_PATH = "/hits/hit";
    
    public static final String RELEVANCE_SORT_FIELD_NAME = "_relevance_";
    
    //Constants for generating search-result output
    public static final String SEARCH_RESULT_NAMESPACE_PREFIX = "search-result";
    
    public static final String SEARCH_RESULT_START_ELEMENT = 
        "<" + SEARCH_RESULT_NAMESPACE_PREFIX + ":search-result-record "
        + "xmlns:" + SEARCH_RESULT_NAMESPACE_PREFIX 
        + "=\"http://www.escidoc.de/schemas/searchresult/0.8\">\n";

    public static final String SEARCH_RESULT_END_ELEMENT = 
        "</" + SEARCH_RESULT_NAMESPACE_PREFIX + ":search-result-record>";

    public static final String SCORE_START_ELEMENT = 
        "<" + SEARCH_RESULT_NAMESPACE_PREFIX + ":score>";

    public static final String SCORE_END_ELEMENT = 
        "</" + SEARCH_RESULT_NAMESPACE_PREFIX + ":score>\n";

    public static final String HIGHLIGHT_START_ELEMENT = 
        "<" + SEARCH_RESULT_NAMESPACE_PREFIX + ":highlight>";

    public static final String HIGHLIGHT_END_ELEMENT = 
        "</" + SEARCH_RESULT_NAMESPACE_PREFIX + ":highlight>\n";

    //SRW Property-Names
    //custom lucene analyzer
    public static final String PROPERTY_ANALYZER = "cqlTranslator.analyzer";

    //Class to generate highlight-snippets
    public static final String PROPERTY_HIGHLIGHTER =
        "cqlTranslator.highlighterClass";

    //Class to generate xml from highlight-snippets
    public static final String PROPERTY_HIGHLIGHT_XMLIZER =
        "cqlTranslator.highlightXmlizerClass";

    //used for custom sorting
    public static final String PROPERTY_COMPARATOR =
        "cqlTranslator.sortComparator";
    
    //used for custom lucene-scoring
    public static final String PROPERTY_SIMILARITY =
        "cqlTranslator.similarity";
    
    //used to indicate if scoring has to get calculated 
    //even for wildcard- and range-queries
    //(slows down search)
    public static final String PROPERTY_FORCE_SCORING =
        "cqlTranslator.forceScoring";
    
    //used to indicate if query has to get expanded with filter for permission
    //only works if permission-filtering fields are indexed
    public static final String PROPERTY_PERMISSION_FILTERING =
        "cqlTranslator.permissionFiltering";
    
    public static final String PROPERTY_HIGHLIGHT_TERM_FULLTEXT =
        "cqlTranslator.highlightTermFulltext";

    public static final String PROPERTY_HIGHLIGHT_TERM_FULLTEXT_ITERABLE =
        "cqlTranslator.highlightTermFulltextIterable";

    public static final String PROPERTY_HIGHLIGHT_TERM_FILENAME =
        "cqlTranslator.highlightTermFilename";

    public static final String PROPERTY_HIGHLIGHT_TERM_METADATA =
        "cqlTranslator.highlightTermMetadata";

    public static final String PROPERTY_HIGHLIGHT_TERM_METADATA_ITERABLE =
        "cqlTranslator.highlightTermMetadataIterable";

    public static final String PROPERTY_DEFAULT_INDEX_FIELD =
        "cqlTranslator.defaultIndexField";

    public static final String PROPERTY_FULLTEXT_INDEX_FIELD =
        "cqlTranslator.fulltextIndexField";

    public static final String PROPERTY_HIGHLIGHT_START_MARKER =
        "cqlTranslator.highlightStartMarker";

    public static final String PROPERTY_HIGHLIGHT_END_MARKER =
        "cqlTranslator.highlightEndMarker";

    public static final String PROPERTY_HIGHLIGHT_FRAGMENT_SIZE =
        "cqlTranslator.highlightFragmentSize";

    public static final String PROPERTY_HIGHLIGHT_MAX_FRAGMENTS =
        "cqlTranslator.highlightMaxFragments";

    public static final String PROPERTY_HIGHLIGHT_FRAGMENT_SEPARATOR =
        "cqlTranslator.highlightFragmentSeparator";

}
