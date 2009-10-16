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
package de.escidoc.sb.srw.lucene.sorting;

import java.text.Collator;
import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexReader.FieldOption;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.ScoreDocComparator;
import org.apache.lucene.search.SortComparatorSource;
import org.apache.lucene.search.SortField;

/**
 * Custom sorter that sorts digits and text.
 * 
 * @author $Author$
 * @version $Revision$
 * 
 */
	public class EscidocSearchResultComparator implements SortComparatorSource, ScoreDocComparator
	{
		private IndexReader reader;
		private String fieldName;
		private boolean reverse;
		private Collator collator;
		private Pattern pattern = Pattern.compile(
				"([\u00e4\u00f6\u00fc\u00c4\u00d6\u00dc])");
		private Matcher matcher1 = pattern.matcher("");
		private Matcher matcher2 = pattern.matcher("");

		
		public EscidocSearchResultComparator ()
		{
			this( false );
		}
		
		public EscidocSearchResultComparator (boolean reverse)
		{
			this.reverse = reverse;
		}
		
		public EscidocSearchResultComparator (IndexReader reader, String fieldName)
		{
			this( reader, fieldName, false );
		}
		
		public EscidocSearchResultComparator (IndexReader reader, String fieldName, boolean reverse)
		{
			this.reader = reader;
			this.fieldName = fieldName;
			this.reverse = reverse;

			//check if field exists
			Collection<String> fieldNames = reader.getFieldNames(FieldOption.ALL);
			if (!fieldNames.contains(fieldName)) {
				throw new RuntimeException(
						"sortKey " + fieldName + " does not exist in the database");
			}
			
			this.collator = Collator.getInstance();
			collator.setStrength(Collator.SECONDARY);
		}
		
				
		public ScoreDocComparator newComparator (IndexReader reader, String fieldName)
		{
			EscidocSearchResultComparator dc = new EscidocSearchResultComparator( reader, fieldName, reverse );
			
			return dc;
		}
		
		public int compare(ScoreDoc i, ScoreDoc j) {
			int result = 0;
			try {
				String fieldvalue1 = reader.document(i.doc).get(fieldName);
				String fieldvalue2 = reader.document(j.doc).get(fieldName);
				if (fieldvalue1 == null && fieldvalue2 == null) {
					return 0;
				} else if (fieldvalue1 == null && fieldvalue2 != null) {
					return -1;
				} else if (fieldvalue1 != null && fieldvalue2 == null) {
					return 1;
				}
				matcher1.reset(fieldvalue1);
				matcher2.reset(fieldvalue2);
				fieldvalue1 = matcher1.replaceAll("$1e");
				fieldvalue2 = matcher2.replaceAll("$1e");
				result = collator.compare(fieldvalue1, fieldvalue2);
			} catch (Exception e) {
			}
			return result;
		}

		public int sortType() {
			return SortField.CUSTOM;
		}

		public Comparable sortValue(ScoreDoc i) {
			try {
				return reader.document(i.doc).get(fieldName);
			} catch (Exception e) {
				return "";
			}
		}
	}
		
