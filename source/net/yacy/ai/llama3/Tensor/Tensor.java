/**
 * Tensor.java

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


package net.yacy.ai.llama3.Tensor;

import java.util.function.IntConsumer;
import java.util.function.LongConsumer;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

import net.yacy.ai.llama3.Tensor.FloatTensor.AggregateFunction;

public interface Tensor {

    @FunctionalInterface
    public interface MapFunction {
        float apply(float value);
    }

    @FunctionalInterface
    public interface MapWithIndexFunction {
        float apply(float value, int index);
    }
    
    public int size();

    public int argmax();
    
    public float getFloat(final int index);
    
    public void setFloat(final int index, final float value);

    public void copyTo(final int thisOffset, final Tensor that, final int thatOffset, final int size);
    
    public float dot(final int thisOffset, final Tensor that, final int thatOffset, final int size);
    
    public void matmul(final Tensor that, final Tensor out, final int dim0, final int dim1);
    
    public void matmul(final int context, final Tensor[] that, final Tensor[] out, final int dim0, final int dim1);
    
    public float reduce(final int thisOffset, final int size, final float seed, final AggregateFunction reduce);

    public Tensor mapInPlace(final MapFunction mapFunction);
    
    public Tensor mapInPlace(final int thisOffset, final int size, MapFunction mapFunction);
    
    public Tensor addInPlace(final Tensor that);
    
    public Tensor multiplyInPlace(final Tensor that);
    
    public Tensor divideInPlace(final int thisOffset, final int size, final float value);
    
    public Tensor fillInPlace(final int thisOffset, final int size, final float value);
    
    public Tensor softmaxInPlace(final int thisOffset, final int size);
    
    public Tensor saxpyInPlace(final int thisOffset, final Tensor that, final int thatOffset, final int size, final float a);
    
    public static void parallelFor(final int startInclusive, final int endExclusive, final IntConsumer action) {
        if (startInclusive == 0 && endExclusive == 1) {
            action.accept(0);
            return;
        }
        IntStream.range(startInclusive, endExclusive).parallel().forEach(action);
    }

    public static void parallelForLong(final long startInclusive, final long endExclusive, final LongConsumer action) {
        if (startInclusive == 0 && endExclusive == 1) {
            action.accept(0);
            return;
        }
        LongStream.range(startInclusive, endExclusive).parallel().forEach(action);
    }
}