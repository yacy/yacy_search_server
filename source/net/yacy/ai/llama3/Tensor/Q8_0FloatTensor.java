/**
 * Q8_0FloatTensor.java

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

import net.yacy.ai.llama3.Model.GGMLType;

public final class Q8_0FloatTensor extends FloatTensor {

    final int size;
    final ByteBuffer buffer;
    

    public Q8_0FloatTensor(final int size, final ByteBuffer buffer) {
        this.size = size;
        this.buffer = buffer;
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public void setFloat(final int index, final float value) {
        throw new UnsupportedOperationException("setFloat");
    }

    @Override
    public GGMLType type() {
        return GGMLType.Q8_0;
    }
    
    @Override
    public final float getFloat(final int index) {
        assert 0 <= index && index < size;
        int blockIndex = index / GGMLType.Q8_0.blockSize;
        int withinBlockIndex = index % GGMLType.Q8_0.blockSize;
        int blockOffset = blockIndex * GGMLType.Q8_0.typeSize;
        byte quant = buffer.get((int) (long) (blockOffset + GGMLType.FLOAT16_BYTES + withinBlockIndex));
        final long offset = blockOffset;
        float scale = FloatTensor.float16ToFloat(buffer.getShort((int) offset));
        return quant * scale;
    }

    @Override
    public float dot(final int thisOffset, final FloatTensor that, final int thatOffset, final int size) {
        assert 0 <= thisOffset && thisOffset + size <= this.size;
        assert 0 <= thatOffset && thatOffset + size <= that.size();

        float result = 0f;
        
        // Calculate first and last block indices
        int firstBlock = thisOffset / GGMLType.Q8_0.blockSize;
        int lastBlock = (thisOffset + size - 1) / GGMLType.Q8_0.blockSize;
        
        for (int block = firstBlock; block <= lastBlock; block++) {
            // Calculate block boundaries and overlaps
            int blockStart = block * GGMLType.Q8_0.blockSize;
            int blockEnd = blockStart + GGMLType.Q8_0.blockSize;
            int start = Math.max(thisOffset, blockStart);
            int end = Math.min(thisOffset + size, blockEnd);
            int length = end - start;
            
            // Get common scale factor for this block
            int blockOffset = block * GGMLType.Q8_0.typeSize;
            final long offset = blockOffset;
            float thisScale = FloatTensor.float16ToFloat(buffer.getShort((int) offset));
            
            // Compute sum of products for this block
            float blockSum = 0f;
            int withinBlockStart = start % GGMLType.Q8_0.blockSize;
            
            int memOffset = blockOffset + GGMLType.FLOAT16_BYTES + withinBlockStart;
            int thatoffset = thatOffset + (start - thisOffset);
            if (that instanceof ArrayFloatTensor) {
                ArrayFloatTensor thatArray = (ArrayFloatTensor) that;
                for (int i = 0; i < length; i++) {
                    blockSum += buffer.get((int) (long) memOffset++) * thatArray.getFloat(thatoffset++);
                }
            } else {
                for (int i = 0; i < length; i++) {
                    blockSum += buffer.get((int) (long) memOffset++) * that.getFloat(thatoffset++);
                }
            }
            
            // Apply scale to the entire block sum
            result += thisScale * blockSum;
        }
        
        return result;
    }

    @Override
    public void copyTo(final int thisOffset, final FloatTensor that, final int thatOffset, final int size) {
        assert 0 <= thisOffset && thisOffset + size <= this.size;
        assert 0 <= thatOffset && thatOffset + size <= that.size();
        
        final int endOffset = thatOffset + size;
        
        for (int i = thatOffset; i < endOffset; ++i) {
            int index = i - thatOffset + thisOffset;
            int blockIndex = index / GGMLType.Q8_0.blockSize;
            int withinBlockIndex = index % GGMLType.Q8_0.blockSize;
            int blockOffset = blockIndex * GGMLType.Q8_0.typeSize;
            
            byte quant = buffer.get((int) (long) (blockOffset + GGMLType.FLOAT16_BYTES + withinBlockIndex));
            final long offset = blockOffset;
            float scale = FloatTensor.float16ToFloat(buffer.getShort((int) offset));
            that.setFloat(i, quant * scale);
        }
    }
}
