/**
 * Sampler.java

 * This file was extracted from the llama3/qwen2 projects
 * https://github.com/mukel/llama3.java
 * https://github.com/mukel/qwen2.svm.java
 * 
 * License: MIT License
 * 
 * Copyright (c) 2024 Andrej Karpathy (for llama2.c)
 * Copyright (c) 2024 Alfonso² Peterssen (for llama3/qwen2)
 * Copyright (c) 2023 Georgi Gerganov et al. (for llama.cpp)
 * Copyright (c) 2025 Michael Peter Christen for modifications:
 * The code was modified to fit the YaCy AI project:
 * - back-port to Java 11 (removal of Vector API operations and record types)
 * - removal of interactive mode and system.out printing
 * - separation of the classes in the single java and refactoring
 * - run-time performance optimizations for dot product computation of quantized values
 * - joining of llama3/qwen2 into one code base; multi-arch options
 * - alignment with code from https://github.com/ggml-org/llama.cpp/
 */

package net.yacy.ai.llama3;

import java.util.Comparator;
import java.util.Random;

import net.yacy.ai.llama3.Tensor.FloatTensor;

@FunctionalInterface
interface Sampler {
    int sampleToken(FloatTensor logits);

    Sampler ARGMAX = FloatTensor::argmax;
    
    static Sampler selectSampler(int vocabularySize, float temperature, float topp, long rngSeed) {
        Sampler sampler;
        if (temperature == 0.0f) {
            // greedy argmax sampling: take the token with the highest probability
            sampler = Sampler.ARGMAX;
        } else {
            // we sample from this distribution to get the next token
            // RandomGeneratorFactory.getDefault().create(rngSeed); requires additonal native-image configuration.
            Random rng = new Random(rngSeed);
            Sampler innerSampler;
            if (topp <= 0 || topp >= 1) {
                // simply sample from the predicted probability distribution
                innerSampler = new CategoricalSampler(rng);
            } else {
                // top-p (nucleus) sampling, clamping the least likely tokens to zero
                innerSampler = new ToppSampler(vocabularySize, topp, rng);
            }
            sampler = logits -> {
                // apply the temperature to the logits
                logits.divideInPlace(0, logits.size(), temperature);
                // apply softmax to the logits to get the probabilities for next token
                logits.softmaxInPlace(0, logits.size());
                return innerSampler.sampleToken(logits);
            };
        }
        return sampler;
    }
    
    static class CategoricalSampler implements Sampler {

        final Random rng;
        
        public CategoricalSampler(Random rng) {
            this.rng = rng;
        }
        
        @Override
        public int sampleToken(FloatTensor logits) {
            // sample index from probabilities (they must sum to 1!)
            float random0to1 = rng.nextFloat();
            float cdf = 0.0f;
            for (int i = 0; i < logits.size(); i++) {
                cdf += logits.getFloat(i);
                if (random0to1 < cdf) {
                    return i;
                }
            }
            return logits.size() - 1; // in case of rounding errors
        }
    }
    
    static class ToppSampler implements Sampler {

        final int[] indices;
        final float topp;
        final Random rng;

        public ToppSampler(int maxNumberOfElements, float topp, Random rng) {
            this.indices = new int[maxNumberOfElements];
            this.topp = topp;
            this.rng = rng;
        }

        static void swap(int[] array, int from, int to) {
            int tmp = array[from];
            array[from] = array[to];
            array[to] = tmp;
        }

        static void siftDown(int[] array, int from, int n, Comparator<Integer> comparator) {
            int prev = from, next;
            while ((next = 2 * prev + 1) < n) {
                int r = 2 * prev + 2;
                if (r < n && comparator.compare(array[r], array[next]) < 0) {
                    next = r;
                }
                if (comparator.compare(array[next], array[prev]) < 0) {
                    swap(array, prev, next);
                    prev = next;
                } else {
                    break;
                }
            }
        }

        @Override
        public int sampleToken(FloatTensor logits) {
            // top-p sampling (or "nucleus sampling") samples from the smallest set of
            // tokens that exceed probability topp. This way we never sample tokens that
            // have very low probabilities and are less likely to go "off the rails".
            Comparator<Integer> comparator = Comparator.comparingDouble(logits::getFloat).reversed();

            int n = logits.size();
            int head = 0;
            int tail = n - 1;
            // values smaller than (1 - topp) / (n - 1) cannot be part of the result
            // so for efficiency we crop these out as candidates before sorting
            float cutoff = (1.0f - topp) / (n - 1);
            for (int i = 0; i < indices.length; i++) {
                if (logits.getFloat(i) >= cutoff) {
                    indices[head++] = i;
                } else {
                    indices[tail--] = i;
                }
            }

            int n0 = head;
            // build heap O(n0)
            for (int i = n0 / 2 - 1; i >= 0; --i) {
                siftDown(indices, i, n0, comparator);
            }

            // truncate the list where cumulative probability of the largest k elements exceeds topp
            // O(k lg n0)
            float cumulativeProb = 0.0f;
            int lastIndex = 0;
            for (int i = n0 - 1; i >= 0; i--) {
                swap(indices, 0, i);
                cumulativeProb += logits.getFloat(indices[i]);
                if (cumulativeProb > topp) {
                    lastIndex = i;
                    break; // we've exceeded topp by including lastIndex
                }
                siftDown(indices, 0, i - 1, comparator);
            }

            // sample from the truncated list
            float r = rng.nextFloat() * cumulativeProb;
            float cdf = 0.0f;
            for (int i = n0 - 1; i >= lastIndex; i--) {
                cdf += logits.getFloat(indices[i]);
                if (r < cdf) {
                    return indices[i];
                }
            }

            return indices[lastIndex]; // in case of rounding errors
        }
    }
}
