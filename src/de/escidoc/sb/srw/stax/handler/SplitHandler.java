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
package de.escidoc.sb.srw.stax.handler;


import java.util.Vector;

import de.escidoc.core.common.util.stax.StaxParser;
import de.escidoc.core.common.util.xml.stax.events.EndElement;
import de.escidoc.core.common.util.xml.stax.events.StartElement;
import de.escidoc.core.common.util.xml.stax.handler.DefaultHandler;

public class SplitHandler extends DefaultHandler {
    protected StaxParser parser;

    protected Vector<String> parts = new Vector<String>();
    protected String splitElementPath = null;
    protected StringBuffer buf = new StringBuffer("");

    /*
     * 
     */public SplitHandler(StaxParser parser, String splitElementPath) {
        this.parser = parser;
        this.splitElementPath = splitElementPath;

    }

    public String characters(String data, StartElement element) {
        if (parser.getCurPath().equals(splitElementPath)) {
            buf.append(data);
        }
        return data;
    }

    public EndElement endElement(EndElement element) {
    	if (parser.getCurPath().equals(splitElementPath)) {
    		parts.add(buf.toString());
    		buf = new StringBuffer("");
    	}
        return element;
    }

	/**
	 * @return the parts
	 */
	public Vector<String> getParts() {
		return parts;
	}
    

}
