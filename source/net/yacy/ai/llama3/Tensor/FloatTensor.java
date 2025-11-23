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

import net.yacy.ai.llama3.Tensor.AbstractFloatTensor.AggregateFunction;

public interface FloatTensor {

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

    public void copyTo(final int thisOffset, final FloatTensor that, final int thatOffset, final int size);
    
    public float dot(final int thisOffset, final FloatTensor that, final int thatOffset, final int size);
    
    public void matmul(final FloatTensor that, final FloatTensor out, final int dim0, final int dim1);
    
    public void matmul(final int context, final FloatTensor[] that, final FloatTensor[] out, final int dim0, final int dim1);
    
    public float reduce(final int thisOffset, final int size, final float seed, final AggregateFunction reduce);

    public FloatTensor mapInPlace(final MapFunction mapFunction);
    
    public FloatTensor mapInPlace(final int thisOffset, final int size, MapFunction mapFunction);
    
    public FloatTensor addInPlace(final FloatTensor that);
    
    public FloatTensor multiplyInPlace(final FloatTensor that);
    
    public FloatTensor divideInPlace(final int thisOffset, final int size, final float value);
    
    public FloatTensor fillInPlace(final int thisOffset, final int size, final float value);
    
    public FloatTensor softmaxInPlace(final int thisOffset, final int size);
    
    public FloatTensor saxpyInPlace(final int thisOffset, final FloatTensor that, final int thatOffset, final int size, final float a);

}