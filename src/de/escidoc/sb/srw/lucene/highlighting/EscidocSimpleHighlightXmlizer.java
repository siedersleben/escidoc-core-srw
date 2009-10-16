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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Properties;

import de.escidoc.sb.srw.Constants;

/**
 * Class makes xml out of highlight-data.
 * 
 * @author MIH
 * @sb
 */
public class EscidocSimpleHighlightXmlizer implements SrwHighlightXmlizer{

    private Collection<HashMap<String, String>> highlightFragmentDatas = 
    							new ArrayList<HashMap<String, String>>();

    private String highlightStartMarker;

    private String highlightEndMarker;

    public void setProperties(final Properties props) throws Exception {
        String temp;
        temp = (String) props.get(Constants.PROPERTY_HIGHLIGHT_START_MARKER);
        if (temp != null && temp.trim().length() != 0) {
            highlightStartMarker =
                props.getProperty(Constants.PROPERTY_HIGHLIGHT_START_MARKER);
        } else {
            throw new Exception(Constants.PROPERTY_HIGHLIGHT_START_MARKER 
                    + " may not be null");
        }
        temp = (String) props.get(Constants.PROPERTY_HIGHLIGHT_END_MARKER);
        if (temp != null && temp.trim().length() != 0) {
            highlightEndMarker =
                props.getProperty(Constants.PROPERTY_HIGHLIGHT_END_MARKER);
        } else {
            throw new Exception(Constants.PROPERTY_HIGHLIGHT_END_MARKER 
                    + " may not be null");
        }
    }

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
     * @param namespacePrefix
     *            namespacePrefix to use for xml.
     * @return String xml
     * @throws Exception
     *             e
     * @sb
     */
    public String xmlize() throws Exception {
        StringBuffer xml = new StringBuffer("");
        if (highlightFragmentDatas != null && !highlightFragmentDatas.isEmpty()) {
            xml.append(Constants.HIGHLIGHT_START_ELEMENT);
            for (HashMap<String, String> highlightFragmentData : highlightFragmentDatas) {
                String type = highlightFragmentData.get("type");
                String objid =
                    highlightFragmentData.get("highlightLocator");
                if (objid != null) {
                    objid = objid.replaceAll("\\/content", "");
                    objid = objid.replaceAll(".*\\/", "");
                }
                String textFragmentData =
                    highlightFragmentData.get("highlightSnippet");
                if (textFragmentData != null && !textFragmentData.equals("")) {
                    if (type == null || type.equals("")) {
                        throw new Exception("type may not be null");
                    }
                    textFragmentData =
                        replaceSpecialCharacters(textFragmentData);
                    xml
                        .append("<")
                        .append(Constants.SEARCH_RESULT_NAMESPACE_PREFIX).append(
                            ":search-hit type=\"").append(type).append("\"");
                    if (type.equals("fulltext")) {
                        if (objid == null || objid.equals("")) {
                            throw new Exception(
                                "highlightLocator may not be null");
                        }
                        xml.append(" objid=\"").append(objid).append("\"");
                    }
                    xml.append(">");
                    if (textFragmentData != null) {
                        xml.append(xmlizeTextFragment(textFragmentData));
                    }
                    xml
                        .append("</").append(
                            Constants.SEARCH_RESULT_NAMESPACE_PREFIX).append(
                            ":search-hit>");
                }

            }
            xml
                .append(Constants.HIGHLIGHT_END_ELEMENT);
        }
        return xml.toString();
    }

    /**
     * Make xml out of one text-fragment.
     * 
     * @param textFragment
     *            textFragment.
     * @return String xml
     * @sb
     */
    private String xmlizeTextFragment(
        final String textFragment) {
        StringBuffer xml = new StringBuffer("");
        if (textFragment != null && textFragment.length() > 0) {
            xml
                .append("<").append(Constants.SEARCH_RESULT_NAMESPACE_PREFIX)
                .append(":text-fragment>");
            xml
                .append("<").append(Constants.SEARCH_RESULT_NAMESPACE_PREFIX)
                .append(":text-fragment-data>");

            //Append Text-Fragment
            xml.append("<![CDATA[").append(textFragment).append("]]>");

            xml
                .append("</").append(Constants.SEARCH_RESULT_NAMESPACE_PREFIX)
                .append(":text-fragment-data>");
            xml
                .append("</").append(Constants.SEARCH_RESULT_NAMESPACE_PREFIX)
                .append(":text-fragment>");
        }
        return xml.toString();
    }

    /**
     * Sometimes, hit-words start with ( or < etc. Replace this
     * Ensure that CDATA-Section is not corrupted.
     * 
     * @param textFragment
     *            textFragment.
     * @return String text with replaced special characters
     * @sb
     */
    private String replaceSpecialCharacters(final String textFragment) {
        String text = textFragment;
        if (text != null) {
            text =
                text.replaceAll("(" + highlightStartMarker + ")([\\(\\)]+)",
                    "$2$1");
            text =
                text.replaceAll("([\\(\\)]+)(" + highlightEndMarker + ")",
                    "$2$1");
            text =
                text.replaceAll("(" + highlightStartMarker + ")(<[^<>]*?>)",
                    "$2$1");
            text =
                text.replaceAll("(<[^<>]*?>)(" + highlightEndMarker + ")",
                    "$2$1");
            text = text.replaceAll("\\]\\]>", "");
        }
        return text;
    }

    /**
     * Returns namespacePrefix with : if namespacePrefix is not null or empty.
     * 
     * @param namespacePrefix
     *            namespacePrefix to use for xml.
     * @return String namespacePrefix
     * @sb
     */
    private String getNamespacePrefix(final String namespacePrefix) {
        if (namespacePrefix != null && !namespacePrefix.equals("")) {
            return namespacePrefix + ":";
        }
        else {
            return "";
        }

    }

    /**
     * Adds highlightFragmentData to Collection of all highlifgtFragmentDatas.
     * 
     * @param highlightFragmentData
     *            HashMap.
     * @sb
     */
    public void addHighlightFragmentData(
    		final HashMap<String, String> highlightFragmentData) {
        highlightFragmentDatas.add(highlightFragmentData);
    }

    /**
     * empties Collection of all highlifgtFragmentDatas.
     * 
     * @sb
     */
    public void clearHighlightFragmentData() {
        highlightFragmentDatas.clear();
    }

}
