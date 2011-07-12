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

import gov.loc.www.zing.srw.ExtraDataType;

import org.apache.axis.message.MessageElement;

/**
 * Class Holding Extra Data of Search.
 * 
 * @author MIH
 */
public class SearchExtraData {
	
    /**
     * extraData in Search indicating if Highlight-Snippet should get generated
     */
    public static final String EXTENSION_OMIT_HIGHLIGHTING = 
    										"x-info5-omitHighlighting";
	private boolean omitHighlighting = false;

    /**
     * extraData in Search indicating the userId to search as
     */
    public static final String EXTENSION_USER_ID = "x-info5-userId";
	private String userId = null;

    /**
     * extraData in Search indicating the roleId to search as
     */
    public static final String EXTENSION_ROLE_ID = "x-info5-roleId";
	private String roleId = null;

    /**
     * extraData in Search indicating if Permission-Filtering should get skipped
     * Used for tests only. Define extension-element in SRWServer.props.
     */
    public static final String EXTENSION_SKIP_PERMISSIONS = 
    										"x-info5-skipPermissions";
	private boolean skipPermissions = false;

    /**
     * extraData in Search indicating if filtering search-result for 
     * lastestVersion<->latestRelease should get skipped
      * Used for tests only. Define extension-element in SRWServer.props.
    */
    public static final String EXTENSION_SKIP_FILTER_LATEST_RELEASE = 
        											"x-info5-skipFilterLatestRelease";
	private boolean skipFilterLatestRelease = false;

    /**
     * Fill Variables with elements from extraDataType-Object.
     * 
     * @param extraDataType extraDataType
     */
	public SearchExtraData(ExtraDataType extraDataType) {
        if (extraDataType != null && extraDataType.get_any() != null) {
            for (int i = 0; i < extraDataType.get_any().length; i++) {
                MessageElement messageElement = extraDataType.get_any()[i];
                if (messageElement.getName() != null) {
                	if (messageElement.getName().equals(
                			EXTENSION_OMIT_HIGHLIGHTING)) {
                        omitHighlighting = 
                        	new Boolean(messageElement.getValue());
                    }
                	else if (messageElement.getName().equals(
                			EXTENSION_USER_ID)) {
                        userId = messageElement.getValue();
                    }
                	else if (messageElement.getName().equals(
                			EXTENSION_ROLE_ID)) {
                        roleId = messageElement.getValue();
                    }
                	else if (messageElement.getName().equals(
                			EXTENSION_SKIP_PERMISSIONS)) {
                		skipPermissions = 
                			new Boolean(messageElement.getValue());
                	}
                	else if (messageElement.getName().equals(
                			EXTENSION_SKIP_FILTER_LATEST_RELEASE)) {
                        skipFilterLatestRelease = 
                        	new Boolean(messageElement.getValue());
                    }
                }
            }
        }
		
	}
	
	public boolean isSkipPermissions() {
		return skipPermissions;
	}

	public boolean isSkipFilterLatestRelease() {
		return skipFilterLatestRelease;
	}

	public boolean isOmitHighlighting() {
		return omitHighlighting;
	}

	public String getUserId() {
		return userId;
	}

	public String getRoleId() {
		return roleId;
	}

}
