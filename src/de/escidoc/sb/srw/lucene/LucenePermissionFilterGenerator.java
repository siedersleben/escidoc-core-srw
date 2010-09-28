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
 * Copyright 2010 Fachinformationszentrum Karlsruhe Gesellschaft
 * fuer wissenschaftlich-technische Information mbH and Max-Planck-
 * Gesellschaft zur Foerderung der Wissenschaft e.V.
 * All rights reserved.  Use is subject to license terms.
 */

package de.escidoc.sb.srw.lucene;

import java.io.ByteArrayInputStream;
import java.net.URL;

import org.apache.http.impl.cookie.BasicClientCookie;
import org.osuosl.srw.SRWDiagnostic;

import ORG.oclc.os.SRW.SRWServlet;
import de.escidoc.core.common.util.configuration.EscidocConfiguration;
import de.escidoc.core.common.util.service.ConnectionUtility;
import de.escidoc.core.common.util.stax.StaxParser;
import de.escidoc.sb.srw.Constants;
import de.escidoc.sb.srw.PermissionFilterGenerator;
import de.escidoc.sb.srw.stax.handler.PermissionFilterHandler;

/**
 * generates a lucene-subquery for permission-filtering.
 * 
 * @author MIH
 */
public class LucenePermissionFilterGenerator implements PermissionFilterGenerator {

    private ConnectionUtility connectionUtility = new ConnectionUtility();
    
    public LucenePermissionFilterGenerator() {
        connectionUtility.setTimeout(180000);
    }
    
    /**
     * get permission-filter subquery for user with given userId.
     * 
     * @param handle handle
     * @return permission-filter subquery
     * 
     */
    public String getPermissionFilter(
                                    final String dbName,
                                    final String handle, 
                                    final String asUserId, 
                                    final String withRoleId) throws SRWDiagnostic {
        try {
            StringBuffer url = new StringBuffer(EscidocConfiguration.getInstance()
                    .get(EscidocConfiguration.ESCIDOC_CORE_SELFURL));
            url.append(Constants.PERMISSION_FILTER_URI).append("?index=").append(dbName);
//            String permissionFilterXml = connectionUtility
//                    .postRequestURLAsString(
//                            new URL(url),
//                            getPostXml(handle, asUserId, withRoleId),
//                            new Cookie("", SRWServlet.COOKIE_LOGIN, handle));
            String permissionFilterXml = connectionUtility
            .getRequestURLAsString(
                    new URL(url.toString()),
                    new BasicClientCookie(SRWServlet.COOKIE_LOGIN, handle));
            StaxParser sp = new StaxParser();
            PermissionFilterHandler handler = new PermissionFilterHandler(sp);
            sp.addHandler(handler);

            sp.parse(new ByteArrayInputStream(permissionFilterXml.getBytes(
                    Constants.CHARACTER_ENCODING)));
            return handler.getPermissionFilterQuery();
        } catch (Exception e) {
            throw new SRWDiagnostic(
                    SRWDiagnostic.GeneralSystemError,
                    "couldnt retrieve permissionFilterQuery " + e.getMessage());
        } 
    }
    
}
