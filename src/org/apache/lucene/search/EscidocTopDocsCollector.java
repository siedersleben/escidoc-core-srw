package org.apache.lucene.search;

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

import java.util.HashSet;
import java.util.Set;

import org.apache.lucene.util.PriorityQueue;

/**
 * Class is basically copied from TopDocsCollector.
 * Changes are made for filtering (eSciDoc) Documents with same id
 * but diffent status.
 * -Added queueSize and hasSentinelObjects in Constructor.
 *  This is important when popping from the PriorityQueue.
 *  Queue can have less ScoreDocs than size, so first pop 
 *  empty ScoreDocs if queue has sentinel Objects.
 * -At startup queue is initialized with queueSize*2 because each 
 *  Document can appear twice.
 *  Duplicate documents are stated in HashSet entriesToRemove.
 *  So after filling, Queue contains queueSize + entriesToRemove.size Documents.
 *  
 *  
 */
public abstract class EscidocTopDocsCollector<T extends ScoreDoc> extends Collector {

  // This is used in case topDocs() is called with illegal parameters, or there
  // simply aren't (enough) results.
  protected static final TopDocs EMPTY_TOPDOCS = new TopDocs(0, new ScoreDoc[0], Float.NaN);
  
  /**
   * The priority queue which holds the top documents. Note that different
   * implementations of PriorityQueue give different meaning to 'top documents'.
   * HitQueue for example aggregates the top scoring documents, while other PQ
   * implementations may hold documents sorted by other criteria.
   */
  protected PriorityQueue<T> pq;

  /** The total number of documents that the collector encountered. */
  protected int totalHits;
  protected int filteredTotalHits;
  Set<String> entriesToRemove = new HashSet<String>();
  static String[] duplicateIdentifiers = null;
  static int[] duplicateDistinguishers = null;
  protected static int distinguisherPriorityValue = 0;
  protected int queueSize = 0;
  
  protected EscidocTopDocsCollector(
          PriorityQueue<T> pq, 
          int queueSize) {
    this.pq = pq;
    this.queueSize = queueSize;
  }
  
  /**
   * Populates the results array with the ScoreDoc instaces. This can be
   * overridden in case a different ScoreDoc type should be returned.
   */
  protected ScoreDoc[] populateResults(ScoreDoc results[], int howMany)
  {
      for(int i = howMany - 1; i >= 0; i--) {
          ScoreDoc scoreDoc = pq.pop();
          if (!entriesToRemove.contains(duplicateIdentifiers[scoreDoc.doc])
                  || duplicateDistinguishers[scoreDoc.doc] != 1) {
              results[i] = scoreDoc;
          } else {
              i++;
          }
          
      }
      return results;

  }

  /**
   * Returns a {@link TopDocs} instance containing the given results. If
   * <code>results</code> is null it means there are no results to return,
   * either because there were 0 calls to collect() or because the arguments to
   * topDocs were invalid.
   */
  protected TopDocs newTopDocs(ScoreDoc[] results, int start) {
    return results == null ? EMPTY_TOPDOCS : new TopDocs(filteredTotalHits, results);
  }
  
  /** The total number of documents that matched this query. */
  public int getTotalHits() {
    return filteredTotalHits;
  }
  
  /** Returns the top docs that were collected by this collector. */
  public final TopDocs topDocs() {
    // In case pq was populated with sentinel values, there might be less
    // results than pq.size(). Therefore return all results until either
    // pq.size() or filteredTotalHits.
    return topDocs(0, filteredTotalHits < queueSize/2 ? filteredTotalHits : queueSize/2);
  }

  /**
   * Returns the documents in the rage [start .. pq.size()) that were collected
   * by this collector. Note that if start >= pq.size(), an empty TopDocs is
   * returned.<br>
   * This method is convenient to call if the application always asks for the
   * last results, starting from the last 'page'.<br>
   * <b>NOTE:</b> you cannot call this method more than once for each search
   * execution. If you need to call it more than once, passing each time a
   * different <code>start</code>, you should call {@link #topDocs()} and work
   * with the returned {@link TopDocs} object, which will contain all the
   * results this search execution collected.
   */
  public final TopDocs topDocs(int start) {
    // In case pq was populated with sentinel values, there might be less
    // results than pq.size(). Therefore return all results until either
    // pq.size() or totalHits.
    return topDocs(start, filteredTotalHits < queueSize/2 ? filteredTotalHits : queueSize/2);
  }

  /**
   * Returns the documents in the rage [start .. start+howMany) that were
   * collected by this collector. Note that if start >= pq.size(), an empty
   * TopDocs is returned, and if pq.size() - start &lt; howMany, then only the
   * available documents in [start .. pq.size()) are returned.<br>
   * This method is useful to call in case pagination of search results is
   * allowed by the search application, as well as it attempts to optimize the
   * memory used by allocating only as much as requested by howMany.<br>
   * <b>NOTE:</b> you cannot call this method more than once for each search
   * execution. If you need to call it more than once, passing each time a
   * different range, you should call {@link #topDocs()} and work with the
   * returned {@link TopDocs} object, which will contain all the results this
   * search execution collected.
   */
  public final TopDocs topDocs(int start, int howMany) {
    
    // In case pq was populated with sentinel values, there might be less
    // results than pq.size(). Therefore return all results until either
    // pq.size() or totalHits.
    int size = filteredTotalHits < queueSize/2 ? filteredTotalHits : queueSize/2;

    // Don't bother to throw an exception, just return an empty TopDocs in case
    // the parameters are invalid or out of range.
    if (start < 0 || start >= size || howMany <= 0) {
      return newTopDocs(null, start);
    }

    // We know that start < pqsize, so just fix howMany. 
    howMany = Math.min(size - start, howMany);
    ScoreDoc[] results = new ScoreDoc[howMany];

    // pop empty Objects until there are twice as much
    // objects in the queue as we need minus non-duplicate objects.
    // Be aware that we could pop duplicate objects
    int neededSize = start*2 + howMany*2 - (start + howMany - entriesToRemove.size());
    for (int i = pq.size(); i > neededSize; i--) { 
        ScoreDoc d = pq.pop();
        if (d != null 
                && d.doc < Integer.MAX_VALUE 
                && entriesToRemove.contains(duplicateIdentifiers[d.doc])) {
            entriesToRemove.remove(duplicateIdentifiers[d.doc]);
            i++;
        }
    }
        
    // Get the requested results from pq.
    populateResults(results, howMany);
    
    return newTopDocs(results, start);
  }

}
