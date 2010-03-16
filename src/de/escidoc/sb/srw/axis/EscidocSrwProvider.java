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
package de.escidoc.sb.srw.axis;

import java.lang.reflect.InvocationTargetException;
import java.util.Vector;

import org.apache.axis.MessageContext;
import org.apache.axis.providers.java.RPCProvider;
import org.apache.ws.security.WSConstants;
import org.apache.ws.security.WSSecurityEngineResult;
import org.apache.ws.security.WSUsernameTokenPrincipal;
import org.apache.ws.security.handler.WSHandlerConstants;
import org.apache.ws.security.handler.WSHandlerResult;

import de.escidoc.core.common.exceptions.system.SystemException;
import de.escidoc.core.common.util.logger.AppLogger;
import de.escidoc.core.common.util.service.UserContext;

/**
 * Axis provider implementation that extends
 * {@link org.apache.axis.providers.java.RPCProvider}.
 * This class extracts ws-security-header and puts it in UserContext-Object.
 * <br>
 * 
 * @author MIH
 * @common
 */
public class EscidocSrwProvider extends RPCProvider {

    /**
     * The serial version uid.
     */
    private static final long serialVersionUID = 8212241336639346861L;

    /**
     * The logger.
     */
    private static final AppLogger LOG =
        new AppLogger(EscidocSrwProvider.class.getName());

    // CHECKSTYLE:JAVADOC-OFF

    /**
     * See Interface for functional description.<br>
     * Realizes spring bean lookup and security context initialization.
     * 
     * @param messageContext
     * @param className
     * @return
     * @throws Exception
     * @see org.apache.axis.providers.java.EJBProvider#makeNewServiceObject(
     *      org.apache.axis.MessageContext, java.lang.String)
     */
    @Override
    protected Object makeNewServiceObject(
        final MessageContext messageContext, final String className)
        throws Exception {

        // initialize user context from webservice security data
        try {
            UserContext.setUserContext(getHandle(messageContext));
            try {
                UserContext.getSecurityContext();
            }
            catch (SystemException e1) {
                throw new InvocationTargetException(e1);
            }
        }
        catch (Exception ex) {
            LOG.error("Setting UserContext failed.", ex);
        }

        return super.makeNewServiceObject(messageContext, className);
    }

    // CHECKSTYLE:JAVADOC-ON

    /**
     * Method that fetches the user credentials from the axis message context
     * and returns them as a array of {username, password}.
     * 
     * @param messageContext
     *            The axis message context
     * @return user name and password as {@link String} array {username,
     *         password}
     */
    private String getHandle(final MessageContext messageContext) {

        String eSciDocUserHandle = null;
        Vector results = null;
        // get the result Vector from the property
        results =
            (Vector) messageContext
                .getProperty(WSHandlerConstants.RECV_RESULTS);
        if (results == null) {
            if (messageContext.getUsername() != null
                && messageContext.getPassword() != null) {
                eSciDocUserHandle = messageContext.getPassword();
            }
            else {
                LOG
                    .info("No security results!! Setting empty username and password");
                eSciDocUserHandle = "";
            }
        }
        else {
            for (int i = 0; i < results.size(); i++) {
                WSHandlerResult hResult = (WSHandlerResult) results.get(i);
                Vector hResults = hResult.getResults();
                for (int j = 0; j < hResults.size(); j++) {
                    WSSecurityEngineResult eResult =
                        (WSSecurityEngineResult) hResults.get(j);
                    // Note: an encryption action does not have an associated
                    // principal
                    // only Signature and UsernameToken actions return a
                    // principal
                    if ((Integer)eResult.get(WSSecurityEngineResult.TAG_ACTION) 
                                                            != WSConstants.ENCR) {
                        WSUsernameTokenPrincipal principal =
                            (WSUsernameTokenPrincipal) eResult
                                .get(WSSecurityEngineResult.TAG_PRINCIPAL);
                        eSciDocUserHandle = principal.getPassword();
                    }
                }
            }
        }
        return eSciDocUserHandle;
    }

}
