/**
 * F16FloatTensor.java

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

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import net.yacy.ai.llama3.Model.GGMLType;

public final class F16FloatTensor extends FloatTensor {

    final int size;
    final ByteBuffer buffer;

    public F16FloatTensor(int size, ByteBuffer buffer) {
        if (buffer.remaining() < size * GGMLType.FLOAT16_BYTES) {
            throw new IllegalArgumentException("Buffer too small");
        }
        this.size = size;
        this.buffer = buffer.duplicate().order(ByteOrder.nativeOrder());
    }
    
    public F16FloatTensor(final float[] values) {
        this.size = values.length;
        this.buffer = ByteBuffer.allocateDirect(size * GGMLType.FLOAT16_BYTES).order(ByteOrder.nativeOrder());
        for (int i = 0; i < size; i++) {
            setFloat(i, values[i]);
        }
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public GGMLType type() {
        return GGMLType.F16;
    }

    @Override
    public final void setFloat(int index, float value) {
        assert 0 <= index && index < size;
        short hBits = floatToFloat16(value);
        buffer.putShort(index * GGMLType.FLOAT16_BYTES, hBits);
    }
    
    @Override
    public final float getFloat(int index) {
        assert 0 <= index && index < size;
        return float16ToFloat(buffer.getShort(index * GGMLType.FLOAT16_BYTES));
    }

}
