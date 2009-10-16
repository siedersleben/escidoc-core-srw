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
public class EscidocHighlightXmlizer implements SrwHighlightXmlizer{
    private Collection<HashMap<String, String>> highlightFragmentDatas = 
    							new ArrayList<HashMap<String, String>>();

    private String fragmentSeparator;

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
        temp = (String) props.get(Constants.PROPERTY_HIGHLIGHT_FRAGMENT_SEPARATOR);
        if (temp != null && temp.trim().length() != 0) {
            fragmentSeparator =
                props.getProperty(Constants.PROPERTY_HIGHLIGHT_FRAGMENT_SEPARATOR);
        }  else {
            throw new Exception(Constants.PROPERTY_HIGHLIGHT_FRAGMENT_SEPARATOR 
                    + " may not be null");
        }       
    }

    /**
     * Make xml out of highlightFragmentDatas Array. 
     * 1. Write head of xml 
     * 2.Iterate over highlightFragmentDatas 
     * 3. Get one highlightFragmentData
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
                    String[] textFragments =
                        textFragmentData.split(
                                replaceForRegex(fragmentSeparator));
                    if (textFragments != null) {
                        for (int i = 0; i < textFragments.length; i++) {
                            xml.append(xmlizeTextFragment(textFragments[i]));
                        }
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
     * Make xml out of one text-fragment. Replace highlight-start + end-marker,
     * calculate start + end-index of the hit-words with help of the
     * highlight-start + end-marker.
     * 
     * @param textFragment
     *            textFragment.
     * @return String xml
     * @sb
     */
    private String xmlizeTextFragment(final String textFragment) {
        StringBuffer xml = new StringBuffer("");
        StringBuffer replacedFragment = new StringBuffer("");
        if (textFragment != null) {
            String[] highlightFragments =
                textFragment.split(
                        replaceForRegex(highlightStartMarker));
            if (highlightFragments != null && highlightFragments.length > 1) {
                xml
                    .append("<").append(Constants.SEARCH_RESULT_NAMESPACE_PREFIX)
                    .append(":text-fragment>");
                int offset = highlightFragments[0].length();
                replacedFragment.append(highlightFragments[0]);
                StringBuffer indexFields = new StringBuffer("");
                for (int i = 1; i < highlightFragments.length; i++) {
                    String highlightFragment = highlightFragments[i];
                    int highlightEndMarkerIndex =
                        highlightFragment.indexOf(highlightEndMarker);
                    int end = offset + highlightEndMarkerIndex - 1;
                    highlightFragment =
                        highlightFragment.replaceAll(
                                replaceForRegex(highlightEndMarker), "");
                    replacedFragment.append(highlightFragment);
                    indexFields
                        .append("<")
                        .append(Constants.SEARCH_RESULT_NAMESPACE_PREFIX).append(
                            ":hit-word>");
                    indexFields.append("<").append(
                        Constants.SEARCH_RESULT_NAMESPACE_PREFIX).append(
                        ":start-index>").append(offset);
                    indexFields.append("</").append(
                        Constants.SEARCH_RESULT_NAMESPACE_PREFIX).append(
                        ":start-index>");
                    indexFields.append("<").append(
                        Constants.SEARCH_RESULT_NAMESPACE_PREFIX).append(
                        ":end-index>").append(end);
                    indexFields.append("</").append(
                        Constants.SEARCH_RESULT_NAMESPACE_PREFIX).append(
                        ":end-index>");
                    indexFields
                        .append("</").append(
                            Constants.SEARCH_RESULT_NAMESPACE_PREFIX).append(
                            ":hit-word>");
                    offset = offset + highlightFragment.length();
                }
                xml
                    .append("<").append(Constants.SEARCH_RESULT_NAMESPACE_PREFIX)
                    .append(":text-fragment-data>");
                xml.append("<![CDATA[").append(replacedFragment).append("]]>");
                xml
                    .append("</").append(Constants.SEARCH_RESULT_NAMESPACE_PREFIX)
                    .append(":text-fragment-data>");
                xml.append(indexFields);
                xml
                    .append("</").append(Constants.SEARCH_RESULT_NAMESPACE_PREFIX)
                    .append(":text-fragment>");
            }
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
                text.replaceAll("("
                        + replaceForRegex(highlightStartMarker) 
                        + ")([\\(\\)]+)",
                    "$2$1");
            text =
                text.replaceAll("([\\(\\)]+)(" 
                        + replaceForRegex(highlightEndMarker) 
                        + ")",
                    "$2$1");
            text =
                text.replaceAll("(" 
                        + replaceForRegex(highlightStartMarker) 
                        + ")(<[^<>]*?>)",
                    "$2$1");
            text =
                text.replaceAll("(<[^<>]*?>)(" 
                        + replaceForRegex(highlightEndMarker) 
                        + ")",
                    "$2$1");
            text = text.replaceAll("\\]\\]>", "");
        }
        return text;
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
    
    private String replaceForRegex(final String regexString) {
        return regexString.replaceAll("([\\*\\?\\+\\.])", "\\\\$1");
    }

}
