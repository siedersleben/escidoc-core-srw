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
 * Copyright 2006-2008 Fachinformationszentrum Karlsruhe Gesellschaft
 * fuer wissenschaftlich-technische Information mbH and Max-Planck-
 * Gesellschaft zur Foerderung der Wissenschaft e.V.  
 * All rights reserved.  Use is subject to license terms.
 */
package de.escidoc.sb.srw.lucene;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.FSDirectory;

import de.escidoc.core.common.exceptions.system.SystemException;
import de.escidoc.core.common.util.logger.AppLogger;

/**
 * @author mih
 * 
 *         Singleton for caching IndexSearchers (one for each index)
 * 
 */
public final class IndexSearcherCache {

    private static IndexSearcherCache instance = null;

    private static AppLogger log =
        new AppLogger(IndexSearcherCache.class.getName());

    /** Holds IndexSeracher for each index. */
    private Map<String, IndexSearcher> indexSearchers = 
                        new HashMap<String, IndexSearcher>();

    /**
     * private Constructor for Singleton.
     * 
     */
    private IndexSearcherCache() {
    }

    /**
     * Only initialize Object once. Check for old objects in cache.
     * 
     * @return IndexerResourceCache IndexerResourceCache
     * 
     * @om
     */
    public static synchronized IndexSearcherCache getInstance() {
        if (instance == null) {
            instance = new IndexSearcherCache();
        }
        return instance;
    }

    /**
     * get IndexSearcher for given indexPath and write it into
     * cache.
     * 
     * @param identifier
     *            identifier
     * @throws SystemException
     *             e
     */
	public synchronized IndexSearcher getIndexSearcher(final String indexPath)
			throws IOException, CorruptIndexException {
		if (indexSearchers.get(indexPath) == null
				|| !indexSearchers.get(indexPath).getIndexReader().isCurrent()) {
			if (indexSearchers.get(indexPath) != null) {
				try {
					indexSearchers.get(indexPath).close();
				} catch (Exception e) {
				}
			}
			indexSearchers.put(indexPath,
					new IndexSearcher(FSDirectory.open(new File(indexPath)),
							true));
		}
		IndexSearcher current = indexSearchers.get(indexPath);
		current.getIndexReader().incRef();
		return current;
	}

}
