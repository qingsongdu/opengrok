/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License (the "License").
 * You may not use this file except in compliance with the License.
 *
 * See LICENSE.txt included in this distribution for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at LICENSE.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information: Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 */

/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.suggest;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.util.BytesRef;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SuggesterUtils {

    private static final Logger logger = Logger.getLogger(SuggesterUtils.class.getName());

    private static final long DEFAULT_TERM_WEIGHT = 0;

    private static final int NORMALIZED_DOCUMENT_FREQUENCY_MULTIPLIER = 1000;

    private SuggesterUtils() {
    }

    static List<LookupResultItem> combineResults(final List<LookupResultItem> results, final int resultSize) {
        LookupPriorityQueue queue = new LookupPriorityQueue(resultSize);

        Map<String, LookupResultItem> map = new HashMap<>();

        for (LookupResultItem item : results) {
            LookupResultItem storedItem = map.get(item.getPhrase());
            if (storedItem == null) {
                map.put(item.getPhrase(), item);
            } else {
                storedItem.combine(item);
            }
        }

        // `queue` holds only `RESULT_COUNT` items with the highest weight
        map.values().forEach(queue::insertWithOverflow);

        return queue.getResult();
    }

    static long computeWeight(final IndexReader indexReader, final String field, final BytesRef bytesRef) {
        try {
            Term term = new Term(field, bytesRef);
            double normalizedDocumentFrequency = computeNormalizedDocumentFrequency(indexReader, term);

            return (long) (normalizedDocumentFrequency * NORMALIZED_DOCUMENT_FREQUENCY_MULTIPLIER);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Could not compute weight for " + bytesRef, e);
        }
        return DEFAULT_TERM_WEIGHT;
    }

    private static double computeNormalizedDocumentFrequency(final IndexReader indexReader, final Term term)
            throws IOException {
        int documentFrequency = indexReader.docFreq(term);

        return ((double) documentFrequency) / indexReader.numDocs();
    }

    public static List<Term> intoTerms(final Query query) {
        if (query == null) {
            return Collections.emptyList();
        }

        List<Term> terms = new LinkedList<>();

        LinkedList<Query> queue = new LinkedList<>();
        queue.add(query);

        while (!queue.isEmpty()) {
            Query q = queue.poll();

            if (q instanceof BooleanQuery) {
                for (BooleanClause bc : ((BooleanQuery) q).clauses()) {
                    queue.add(bc.getQuery());
                }
            } else if (q instanceof TermQuery) {
                terms.add(((TermQuery) q).getTerm());
            } else if (q instanceof PhraseQuery) {
                terms.addAll(Arrays.asList(((PhraseQuery) q).getTerms()));
            }
        }

        return terms;
    }

    public static List<Term> intoTermsExceptPhraseQuery(final Query query) {
        if (query == null) {
            return Collections.emptyList();
        }

        List<Term> terms = new LinkedList<>();

        LinkedList<Query> queue = new LinkedList<>();
        queue.add(query);

        while (!queue.isEmpty()) {
            Query q = queue.poll();

            if (q instanceof BooleanQuery) {
                for (BooleanClause bc : ((BooleanQuery) q).clauses()) {
                    queue.add(bc.getQuery());
                }
            } else if (q instanceof TermQuery) {
                terms.add(((TermQuery) q).getTerm());
            }
        }

        return terms;
    }

}
