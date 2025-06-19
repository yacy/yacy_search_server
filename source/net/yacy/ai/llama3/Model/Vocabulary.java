/**
 * Vocabulary.java

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

package net.yacy.ai.llama3.Model;

import java.util.*;
import java.util.stream.*;

public final class Vocabulary {
    private final String[] tokens;
    private final float[] scores;
    private final Map<String, Integer> tokenToIndex;

    // Primary constructor
    public Vocabulary(String[] tokens, float[] scores, Map<String, Integer> tokenToIndex) {
        this.tokens = tokens == null ? null : Arrays.copyOf(tokens, tokens.length);
        this.scores = scores == null ? null : Arrays.copyOf(scores, scores.length);
        this.tokenToIndex = tokenToIndex == null ? null : new HashMap<>(tokenToIndex);
    }

    // Secondary constructor
    public Vocabulary(String[] vocabulary, float[] scores) {
        this(vocabulary, scores,
                IntStream.range(0, vocabulary.length)
                .boxed()
                .collect(Collectors.toMap(i -> vocabulary[i], i -> i))
        );
    }
    
    public String[] tokens() {
        return Arrays.copyOf(tokens, tokens.length);
    }

    public String get(int tokenIndex) {
        return tokens[tokenIndex];
    }

    public OptionalInt getIndex(String token) {
        Integer value = tokenToIndex.get(token);
        return value != null ? OptionalInt.of(value) : OptionalInt.empty();
    }

    public int size() {
        return tokens.length;
    }
}