/**
 * BF16FloatTensor.java

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

public final class BF16FloatTensor extends FloatTensor {

    final int size;
    final ByteBuffer buffer;

    public BF16FloatTensor(int size, ByteBuffer buffer) {
        if (buffer.remaining() < size * GGMLType.BFLOAT16_BYTES) {
            throw new IllegalArgumentException("Buffer too small");
        }
        this.size = size;
        this.buffer = buffer.duplicate().order(ByteOrder.nativeOrder());
    }
    
    public BF16FloatTensor(final float[] values) {
        this.size = values.length;
        this.buffer = ByteBuffer.allocateDirect(size * GGMLType.BFLOAT16_BYTES).order(ByteOrder.nativeOrder());
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
        return GGMLType.BF16;
    }
    
    @Override
    public final void setFloat(int index, float value) {
        assert 0 <= index && index < size;
        int hBits = Float.floatToIntBits(value) >>> 16; // convert float to bfloat16
        buffer.putShort(index * GGMLType.BFLOAT16_BYTES, (short) hBits);
    }

    @Override
    public final float getFloat(int index) {
        assert 0 <= index && index < size;
        return Float.intBitsToFloat(buffer.getShort(index * GGMLType.BFLOAT16_BYTES) << 16);
    }

}
