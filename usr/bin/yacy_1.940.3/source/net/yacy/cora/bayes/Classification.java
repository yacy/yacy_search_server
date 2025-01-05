/*
 * The MIT License (MIT)
 * ------------------
 * 
 * Copyright (c) 2012-2014 Philipp Nolte
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

/*
 * This software was taken from https://github.com/ptnplanet/Java-Naive-Bayes-Classifier
 * and inserted into the loklak class hierarchy to be enhanced and extended
 * by @0rb1t3r. After optimization in loklak it was inserted into the net.yacy.cora.bayes
 * package. It shall be used to create custom search navigation filters.
 * The original copyright notice was copied from the README.mnd
 * from https://github.com/ptnplanet/Java-Naive-Bayes-Classifier/blob/master/README.md
 * The original package domain was de.daslaboratorium.machinelearning.classifier
 */

package net.yacy.cora.bayes;

import java.util.Collection;

/**
 * A basic wrapper reflecting a classification.  It will store both featureset
 * and resulting classification.
 *
 * @author Philipp Nolte
 *
 * @param <T> The feature class.
 * @param <K> The category class.
 */
public class Classification<T, K> {

    /**
     * The classified featureset.
     */
    private Collection<T> featureset;

    /**
     * The category as which the featureset was classified.
     */
    private K category;

    /**
     * The probability that the featureset belongs to the given category.
     */
    private float probability;

    /**
     * Constructs a new Classification with the parameters given and a default
     * probability of 1.
     *
     * @param featureset The featureset.
     * @param category The category.
     */
    public Classification(Collection<T> featureset, K category) {
        this(featureset, category, 1.0f);
    }

    /**
     * Constructs a new Classification with the parameters given.
     *
     * @param featureset The featureset.
     * @param category The category.
     * @param probability The probability.
     */
    public Classification(Collection<T> featureset, K category, float probability) {
        this.featureset = featureset;
        this.category = category;
        this.probability = probability;
    }

    /**
     * Retrieves the featureset classified.
     *
     * @return The featureset.
     */
    public Collection<T> getFeatureset() {
        return featureset;
    }

    /**
     * Retrieves the classification's probability.
     * @return
     */
    public float getProbability() {
        return this.probability;
    }

    /**
     * Retrieves the category the featureset was classified as.
     *
     * @return The category.
     */
    public K getCategory() {
        return category;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return "Classification [category=" + this.category
                + ", probability=" + this.probability
                + ", featureset=" + this.featureset
                + "]";
    }

}
