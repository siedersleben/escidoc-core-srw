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

import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Properties;

import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamSource;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.osuosl.srw.RecordResolver;

import ORG.oclc.os.SRW.Record;
import ORG.oclc.os.SRW.SRWDiagnostic;

/**
 * Class for resolving records and schemas. Also transforms between schemas.
 * 
 * @author MIH
 */
public class EscidocRecordResolver implements RecordResolver {

    private static Log log = LogFactory.getLog(EscidocRecordResolver.class);

    private HashMap<String, HashMap<String, String>> schemas = 
    				new HashMap<String, HashMap<String, String>>();
    
    private Hashtable<String, Transformer> transformers = 
    					new Hashtable<String, Transformer>();

    /**
     * initialize. Read all supported schema-names from properties. Save
     * schema-name and schema-location into HashMap.
     * 
     * @param properties
     *            properties
     */
    public void init(final Properties properties) {
        if (properties != null) {
            // get supported schema-names
            for (Iterator iter = properties.keySet().iterator(); iter.hasNext();) {
                String propertyName = (String) iter.next();
                if (propertyName.startsWith(("recordResolver.schema"))) {
                    schemas.put(propertyName.replaceFirst(
                        "recordResolver\\.schema\\.", ""), new HashMap());
                }
            }

            // get schema informations
            if (schemas != null) {
                for (Iterator iter = schemas.keySet().iterator(); iter
                    .hasNext();) {
                    String schemaId = (String) iter.next();
                    ((HashMap<String, String>) schemas.get(schemaId)).put("identifier",
                        properties.getProperty("recordResolver." + schemaId
                            + ".identifier"));
                    ((HashMap<String, String>) schemas.get(schemaId)).put("location",
                        properties.getProperty("recordResolver." + schemaId
                            + ".location"));
                    ((HashMap<String, String>) schemas.get(schemaId)).put("title", properties
                        .getProperty("recordResolver." + schemaId + ".title"));
                }
            }
        }
    }

    /**
     * transform a record from default schema into a new schema using XSL
     * transforms.
     * 
     * @param record
     *            record
     * @param schemaId
     *            schemaId
     * @return transformed xml
     * @throws SRWDiagnostic
     *             e
     */
    public String transform(
        final ORG.oclc.os.SRW.Record record, final String schemaId)
        throws SRWDiagnostic {
        // TODO: Implement transformation
        // from search-record-schema to given schema
        // In lucene.SRWDatabase.properties, dc is already included.
        // If dc shall be included:
        // Just uncomment dc-section in getSchemaInfo()
        // and add transformation from default to dc
        if (log.isDebugEnabled()) {
            log.debug("EscidocRecordResolver.transform: schemaId:" + schemaId);
        }
        return record.getRecord();

    }

    /**
     * ckeck if given schema can be found.
     * 
     * @param schema
     *            schema
     * @return boolean found
     */
    public boolean containsSchema(final String schema) {
        if (log.isDebugEnabled()) {
            log.debug("EscidocRecordResolver.containsSchema: schema:" + schema);
        }
        if (schemas.get(schema) != null) {
            return true;
        }
        return false;
    }

    /**
     * Retrieves schemas from the schemas-HashMap to create the schemaInfo xml
     * fragment.
     * 
     * @return schema info xml fragment
     */
    public StringBuffer getSchemaInfo() {
        StringBuffer schemaXML = new StringBuffer("");
        if (schemas != null && !schemas.isEmpty()) {
            schemaXML.append("        <schemaInfo>\n");
            for (Iterator iter = schemas.keySet().iterator(); iter.hasNext();) {
                String schemaId = (String) iter.next();
                HashMap<String, String> values = 
                	(HashMap<String, String>) schemas.get(schemaId);
                if (values != null) {
                    String identifier =
                        (String) ((HashMap<String, String>) schemas.get(schemaId))
                            .get("identifier");
                    String location =
                        (String) ((HashMap<String, String>) schemas.get(schemaId))
                            .get("location");
                    String title =
                        (String) ((HashMap<String, String>) schemas.get(schemaId)).get("title");
                    if (identifier != null && location != null && title != null) {
                        schemaXML
                            .append(
                             "          <schema sort=\"true\" retrieve=\"true\"")
                            .append(" name=\"").append(schemaId).append(
                                "\"\n              ").append("identifier=\"")
                            .append(identifier).append("\"\n              ")
                            .append("location=\"").append(location).append(
                                "\">\n").append("            ").append(
                                "<title>").append(title).append("</title>\n")
                            .append("            </schema>\n");
                    }
                }
            }
            schemaXML.append("</schemaInfo>");
        }

        return schemaXML;
    }

    /**
     * Resolve a record. In escidoc, the whole record is already in id. So we
     * dont have to get the data from somewhere.
     * 
     * @param id
     *            id
     * @param extraDataType
     *            extraDataType
     * @return record if found.
     */
    public ORG.oclc.os.SRW.Record resolve(
        final Object id, final ExtraDataType extraDataType) {

        if (log.isDebugEnabled()) {
            log.debug("return record in xml is: " + (String) id);
        }
        return new Record((String) id, "default");
    }
    
    private Transformer getTransformer(String schemaId) {
        Transformer transformer = null;
    	if (transformers.get(schemaId) == null) {
    		try {
                TransformerFactory tfactory = TransformerFactory.newInstance();
                String tFactoryImpl = System.getProperty(
                "javax.xml.transform.TransformerFactory");
                if (tFactoryImpl != null
                        && tFactoryImpl.contains("saxon")) {
                    tfactory = new org.apache.xalan.processor.TransformerFactoryImpl();
                }
                StreamSource xslt = new StreamSource("");
                transformer = tfactory.newTransformer(xslt);
                transformers.put(schemaId, transformer);
    		}
    		catch (TransformerConfigurationException e) {
    			
    		}
    	}
    	return transformers.get(schemaId);
    }

}