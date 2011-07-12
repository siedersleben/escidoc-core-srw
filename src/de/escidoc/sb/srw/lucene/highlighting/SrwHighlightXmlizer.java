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

package de.escidoc.sb.srw.lucene.highlighting;

import java.util.HashMap;
import java.util.Properties;

/**
 * Interface for Classes that make xml out of highlight-data.
 * 
 * @author MIH
 * @sb
 */
public interface SrwHighlightXmlizer {
    
    /**
     * set properties from config-file into variables.
     * 
     * @param props
     *            properties
     * 
     * @sb
     */
    void setProperties(final Properties props) throws Exception;

    /**
     * Adds highlightFragmentData to Collection of all highlightFragmentDatas.
     * 
     * @param highlightFragmentData
     *            HashMap.
     * @sb
     */
    void addHighlightFragmentData(
            final HashMap<String, String> highlightFragmentData);

    /**
     * empties Collection of all highlightFragmentDatas.
     * 
     * @sb
     */
    void clearHighlightFragmentData();

    /**
     * Make xml out of highlightFragmentDatas Array. 1. Write head of xml 2.
     * Iterate over highlightFragmentDatas 3. Get one highlightFragmentData
     * HashMap HahsMap contains the following elements: -highlightLocator (path
     * to fulltext where highlight-snippet comes from) -type (fulltext or
     * metadata , only fulltext gets path to content) -highlightSnippet
     * (text-snippet with highlight start + end information) 4. Write
     * head-information for the highlight-snippet 5. Write text-information for
     * the highlight-snippet by calling method xmlizeTextFragment
     * 
     * @return String xml
     * @throws Exception
     *             e
     * @sb
     */
    String xmlize() throws Exception;

}
