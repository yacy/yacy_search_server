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
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Abstract base extended by any concrete classifier.  It implements the basic
 * functionality for storing categories or features and can be used to calculate
 * basic probabilities – both category and feature probabilities. The classify
 * function has to be implemented by the concrete classifier class.
 *
 * @author Philipp Nolte
 *
 * @param <T> A feature class
 * @param <K> A category class
 */
public abstract class Classifier<T, K> {

    /**
     * Initial capacity of category dictionaries.
     */
    private static final int INITIAL_CATEGORY_DICTIONARY_CAPACITY = 16;

    /**
     * Initial capacity of feature dictionaries. It should be quite big, because
     * the features will quickly outnumber the categories. 
     */
    private static final int INITIAL_FEATURE_DICTIONARY_CAPACITY = 32;

    /**
     * The initial memory capacity or how many classifications are memorized.
     */
    private int memoryCapacity = 1000;

    /**
     * A dictionary mapping features to their number of occurrences in each
     * known category.
     */
    private Map<K, Map<T, Integer>> featureCountPerCategory;

    /**
     * A dictionary mapping features to their number of occurrences.
     */
    private Map<T, Integer> totalFeatureCount;

    /**
     * A dictionary mapping categories to their number of occurrences.
     */
    private Map<K, Integer> totalCategoryCount;

    /**
     * The classifier's memory. It will forget old classifications as soon as
     * they become too old.
     */
    private Queue<Classification<T, K>> memoryQueue;

    /**
     * Constructs a new classifier without any trained knowledge.
     */
    public Classifier() {
        this.reset();
    }

    /**
     * Resets the <i>learned</i> feature and category counts.
     */
    public void reset() {
        this.featureCountPerCategory =
                new ConcurrentHashMap<K, Map<T,Integer>>(
                        Classifier.INITIAL_CATEGORY_DICTIONARY_CAPACITY);
        this.totalFeatureCount =
                new ConcurrentHashMap<T, Integer>(
                        Classifier.INITIAL_FEATURE_DICTIONARY_CAPACITY);
        this.totalCategoryCount =
                new ConcurrentHashMap<K, Integer>(
                        Classifier.INITIAL_CATEGORY_DICTIONARY_CAPACITY);
        this.memoryQueue = new LinkedList<Classification<T, K>>();
    }

    /**
     * Returns a <code>Set</code> of features the classifier knows about.
     *
     * @return The <code>Set</code> of features the classifier knows about.
     */
    public Set<T> getFeatures() {
        return this.totalFeatureCount.keySet();
    }

    /**
     * Returns a <code>Set</code> of categories the classifier knows about.
     *
     * @return The <code>Set</code> of categories the classifier knows about.
     */
    public Set<K> getCategories() {
        return this.totalCategoryCount.keySet();
    }

    /**
     * Retrieves the total number of categories the classifier knows about.
     *
     * @return The total category count.
     */
    public int getCategoriesTotal() {
        int toReturn = 0;
        for (Integer c: this.totalCategoryCount.values()) {
            toReturn += c;
        }
        return toReturn;
    }

    /**
     * Retrieves the memory's capacity.
     *
     * @return The memory's capacity.
     */
    public int getMemoryCapacity() {
        return memoryCapacity;
    }

    /**
     * Sets the memory's capacity.  If the new value is less than the old
     * value, the memory will be truncated accordingly.
     *
     * @param memoryCapacity The new memory capacity.
     */
    public void setMemoryCapacity(int memoryCapacity) {
        for (int i = this.memoryCapacity; i > memoryCapacity; i--) {
            this.memoryQueue.poll();
        }
        this.memoryCapacity = memoryCapacity;
    }

    /**
     * Increments the count of a given feature in the given category.  This is
     * equal to telling the classifier, that this feature has occurred in this
     * category.
     *
     * @param feature The feature, which count to increase.
     * @param category The category the feature occurred in.
     */
    public void incrementFeature(T feature, K category) {
        Map<T, Integer> features =
                this.featureCountPerCategory.get(category);
        if (features == null) {
            this.featureCountPerCategory.put(category,
                    new ConcurrentHashMap<T, Integer>(Classifier.INITIAL_FEATURE_DICTIONARY_CAPACITY));
            features = this.featureCountPerCategory.get(category);
        }
        Integer count = features.get(feature);
        if (count == null) {
            features.put(feature, 0);
            count = features.get(feature);
        }
        features.put(feature, ++count);

        Integer totalCount = this.totalFeatureCount.get(feature);
        if (totalCount == null) {
            this.totalFeatureCount.put(feature, 0);
            totalCount = this.totalFeatureCount.get(feature);
        }
        this.totalFeatureCount.put(feature, ++totalCount);
    }

    /**
     * Increments the count of a given category.  This is equal to telling the
     * classifier, that this category has occurred once more.
     *
     * @param category The category, which count to increase.
     */
    public void incrementCategory(K category) {
        Integer count = this.totalCategoryCount.get(category);
        if (count == null) {
            this.totalCategoryCount.put(category, 0);
            count = this.totalCategoryCount.get(category);
        }
       this.totalCategoryCount.put(category, ++count);
    }

    /**
     * Decrements the count of a given feature in the given category.  This is
     * equal to telling the classifier that this feature was classified once in
     * the category.
     *
     * @param feature The feature to decrement the count for.
     * @param category The category.
     */
    public void decrementFeature(T feature, K category) {
        Map<T, Integer> features =
                this.featureCountPerCategory.get(category);
        if (features == null) {
            return;
        }
        Integer count = features.get(feature);
        if (count == null) {
            return;
        }
        if (count.intValue() == 1) {
            features.remove(feature);
            if (features.size() == 0) {
                this.featureCountPerCategory.remove(category);
            }
        } else {
            features.put(feature, --count);
        }

        Integer totalCount = this.totalFeatureCount.get(feature);
        if (totalCount == null) {
            return;
        }
        if (totalCount.intValue() == 1) {
            this.totalFeatureCount.remove(feature);
        } else {
            this.totalFeatureCount.put(feature, --totalCount);
        }
    }

    /**
     * Decrements the count of a given category.  This is equal to telling the
     * classifier, that this category has occurred once less.
     *
     * @param category The category, which count to increase.
     */
    public void decrementCategory(K category) {
        Integer count = this.totalCategoryCount.get(category);
        if (count == null) {
            return;
        }
        if (count.intValue() == 1) {
            this.totalCategoryCount.remove(category);
        } else {
            this.totalCategoryCount.put(category, --count);
        }
    }

    /**
     * Retrieves the number of occurrences of the given feature in the given
     * category.
     *
     * @param feature The feature, which count to retrieve.
     * @param category The category, which the feature occurred in.
     * @return The number of occurrences of the feature in the category.
     */
    public int featureCount(T feature, K category) {
        Map<T, Integer> features =
                this.featureCountPerCategory.get(category);
        if (features == null)
            return 0;
        Integer count = features.get(feature);
        return (count == null) ? 0 : count.intValue();
    }

    /**
     * Retrieves the number of occurrences of the given category.
     * 
     * @param category The category, which count should be retrieved.
     * @return The number of occurrences.
     */
    public int categoryCount(K category) {
        Integer count = this.totalCategoryCount.get(category);
        return (count == null) ? 0 : count.intValue();
    }

    public float featureProbability(T feature, K category) {
        if (this.categoryCount(category) == 0)
            return 0;
        return (float) this.featureCount(feature, category)
                / (float) this.categoryCount(category);
    }

    /**
     * Retrieves the weighed average <code>P(feature|category)</code> with
     * overall weight of <code>1.0</code> and an assumed probability of
     * <code>0.5</code>. The probability defaults to the overall feature
     * probability.
     *
     * @see de.daslaboratorium.machinelearning.classifier.Classifier#featureProbability(Object, Object)
     * @see de.daslaboratorium.machinelearning.classifier.Classifier#featureWeighedAverage(Object, Object, IFeatureProbability, float, float)
     *
     * @param feature The feature, which probability to calculate.
     * @param category The category.
     * @return The weighed average probability.
     */
    public float featureWeighedAverage(T feature, K category) {
        return this.featureWeighedAverage(feature, category, null, 1.0f, 0.5f);
    }

    /**
     * Retrieves the weighed average <code>P(feature|category)</code> with
     * overall weight of <code>1.0</code>, an assumed probability of
     * <code>0.5</code> and the given object to use for probability calculation.
     *
     * @see de.daslaboratorium.machinelearning.classifier.Classifier#featureWeighedAverage(Object, Object, IFeatureProbability, float, float)
     *
     * @param feature The feature, which probability to calculate.
     * @param category The category.
     * @param calculator The calculating object.
     * @return The weighed average probability.
     */
    public float featureWeighedAverage(T feature, K category, Classifier<T, K> calculator) {
        return this.featureWeighedAverage(feature, category,
                calculator, 1.0f, 0.5f);
    }

    /**
     * Retrieves the weighed average <code>P(feature|category)</code> with
     * the given weight and an assumed probability of <code>0.5</code> and the
     * given object to use for probability calculation.
     *
     * @see de.daslaboratorium.machinelearning.classifier.Classifier#featureWeighedAverage(Object, Object, IFeatureProbability, float, float)
     *
     * @param feature The feature, which probability to calculate.
     * @param category The category.
     * @param calculator The calculating object.
     * @param weight The feature weight.
     * @return The weighed average probability.
     */
    public float featureWeighedAverage(T feature, K category, Classifier<T, K> calculator, float weight) {
        return this.featureWeighedAverage(feature, category,
                calculator, weight, 0.5f);
    }

    /**
     * Retrieves the weighed average <code>P(feature|category)</code> with
     * the given weight, the given assumed probability and the given object to
     * use for probability calculation.
     *
     * @param feature The feature, which probability to calculate.
     * @param category The category.
     * @param calculator The calculating object.
     * @param weight The feature weight.
     * @param assumedProbability The assumed probability.
     * @return The weighed average probability.
     */
    public float featureWeighedAverage(T feature, K category, Classifier<T, K> calculator, float weight, float assumedProbability) {

        /*
         * use the given calculating object or the default method to calculate
         * the probability that the given feature occurred in the given
         * category.
         */
        final float basicProbability =
                (calculator == null)
                    ? this.featureProbability(feature, category)
                            : calculator.featureProbability(feature, category);

        Integer totals = this.totalFeatureCount.get(feature);
        if (totals == null)
            totals = 0;
        return (weight * assumedProbability + totals  * basicProbability)
                / (weight + totals);
    }

    /**
     * Train the classifier by telling it that the given features resulted in
     * the given category.
     *
     * @param category The category the features belong to.
     * @param features The features that resulted in the given category.
     */
    public void learn(K category, Collection<T> features) {
        this.learn(new Classification<T, K>(features, category));
    }

    /**
     * Train the classifier by telling it that the given features resulted in
     * the given category.
     *
     * @param classification The classification to learn.
     */
    public void learn(Classification<T, K> classification) {

        for (T feature : classification.getFeatureset())
            this.incrementFeature(feature, classification.getCategory());
        this.incrementCategory(classification.getCategory());

        this.memoryQueue.offer(classification);
        if (this.memoryQueue.size() > this.memoryCapacity) {
            Classification<T, K> toForget = this.memoryQueue.remove();

            for (T feature : toForget.getFeatureset())
                this.decrementFeature(feature, toForget.getCategory());
            this.decrementCategory(toForget.getCategory());
        }
    }

    /**
     * The classify method.  It will retrieve the most likely category for the
     * features given and depends on the concrete classifier implementation.
     *
     * @param features The features to classify.
     * @return The category most likely.
     */
    public abstract Classification<T, K> classify(Collection<T> features);

}
