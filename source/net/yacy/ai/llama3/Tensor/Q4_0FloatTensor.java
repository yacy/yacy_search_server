/**
 * Q4_0FloatTensor.java

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
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

import net.yacy.ai.llama3.Model.GGMLType;

public final class Q4_0FloatTensor extends FloatTensor {

    private final int size;
    private final ByteBuffer buffer;

    public Q4_0FloatTensor(final int size, final ByteBuffer buffer) {
        this.size = size;
        this.buffer = buffer;
    }
    
    private static final VarHandle FLOAT_ARRAY_HANDLE;

    static {
        try {
            FLOAT_ARRAY_HANDLE = MethodHandles.arrayElementVarHandle(float[].class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to get VarHandle for float[]", e);
        }
    }

    @Override
    public final int size() {
        return size;
    }

    @Override
    public final void setFloat(final int index, final float value) {
        throw new UnsupportedOperationException("setFloat");
    }

    @Override
    public final GGMLType type() {
        return GGMLType.Q4_0;
    }

    private final static int LOG2_QUANT_BLOCK_SIZE = Integer.numberOfTrailingZeros(GGMLType.Q4_0.blockSize); // only if QUANT_BLOCK_SIZE == 2^LOG2_QUANT_BLOCK_SIZE
    private final static int QUANT_HALF_BLOCK = GGMLType.Q4_0.blockSize / 2; // 16
    private final static int QUANT_FLOAT16_BYTES = GGMLType.FLOAT16_BYTES; // 2
    
    @Override
    public final float getFloat(final int index) {
        assert 0 <= index && index < size;
        
        int blockIndex = index >>> LOG2_QUANT_BLOCK_SIZE; // index / QUANT_BLOCK_SIZE;
        int blockOffset = blockIndex * GGMLType.Q4_0.typeSize;
        final long offset = blockOffset;
        float scale = FloatTensor.float16ToFloat(buffer.getShort((int) offset));
        final int modIndex = index & (GGMLType.Q4_0.blockSize - 1); //index % QUANT_BLOCK_SIZE;
        final boolean isLow = modIndex < QUANT_HALF_BLOCK;
        final int adjustedIndex = modIndex - (isLow ? 0 : QUANT_HALF_BLOCK);
        final int dataIndex = blockOffset + QUANT_FLOAT16_BYTES + adjustedIndex;
        final int packed = (buffer.get((int) (long) dataIndex)) & 0xFF;
        final int nibble = isLow ? (packed & 0x0F) : ((packed >>> 4) & 0x0F);
        //final float quant = nibble - 8;
        return (nibble - 8) * scale;
    }
    
    public final void getFloatArray(final int index, final float[] out, final int outOffset, final int length) {
        int inPos = index;
        int outPos = outOffset;
        final int end = index + length;

        while (inPos < end) {
            final int blockIndex = inPos >>> LOG2_QUANT_BLOCK_SIZE;
            final int blockOffset = blockIndex * GGMLType.Q4_0.typeSize;
            float scale = FloatTensor.float16ToFloat(buffer.getShort((int) (long) blockOffset));

            final int blockStart = blockIndex * GGMLType.Q4_0.blockSize;
            final int blockEnd = Math.min(blockStart + GGMLType.Q4_0.blockSize, end);

            // Process two values at a time
            int i = inPos;
            while (i + 1 < blockEnd) {
                int blockMod0 = i & (GGMLType.Q4_0.blockSize - 1);
                int blockMod1 = blockMod0 + 1;

                int byteOffset0 = blockOffset + QUANT_FLOAT16_BYTES + (blockMod0 < QUANT_HALF_BLOCK ? blockMod0 : blockMod0 - QUANT_HALF_BLOCK);
                int byteOffset1 = blockOffset + QUANT_FLOAT16_BYTES + (blockMod1 < QUANT_HALF_BLOCK ? blockMod1 : blockMod1 - QUANT_HALF_BLOCK);
                final long offset = byteOffset0;

                int packed0 = buffer.get((int) offset) & 0xFF;
                final long offset1 = byteOffset1;
                int packed1 = (byteOffset1 == byteOffset0) ? packed0 : (buffer.get((int) offset1) & 0xFF);

                // Decode first value from packed0
                int nibble0 = (blockMod0 < QUANT_HALF_BLOCK) ? (packed0 & 0x0F) : ((packed0 >>> 4) & 0x0F);
                out[outPos++] = (nibble0 - 8) * scale;

                // Decode second value from packed1
                int nibble1 = (blockMod1 < QUANT_HALF_BLOCK) ? (packed1 & 0x0F) : ((packed1 >>> 4) & 0x0F);
                out[outPos++] = (nibble1 - 8) * scale;

                i += 2;
            }

            // Handle last element if length is odd
            if (i < blockEnd) {
                int blockMod = i & (GGMLType.Q4_0.blockSize - 1);
                int byteOffset = blockOffset + QUANT_FLOAT16_BYTES + (blockMod < QUANT_HALF_BLOCK ? blockMod : blockMod - QUANT_HALF_BLOCK);
                final long offset = byteOffset;
                int packed = buffer.get((int) offset) & 0xFF;
                int nibble = (blockMod < QUANT_HALF_BLOCK) ? (packed & 0x0F) : ((packed >>> 4) & 0x0F);
                out[outPos++] = (nibble - 8) * scale;
                i++;
            }

            int copied = blockEnd - inPos;
            inPos += copied;
        }
    }
    
    public static final ThreadLocal<float[]> scratchBuffer = ThreadLocal.withInitial(() -> new float[GGMLType.Q4_0.blockSize]);

    @Override
    public void copyTo(final int thisOffset, final FloatTensor that, final int thatOffset, final int size) {
        final float[] decoded = scratchBuffer.get();
        int remaining = size;
        int srcIndex = thisOffset;
        int dstIndex = thatOffset;

        // Decode and copy in QUANT_BLOCK_SIZE chunks
        while (remaining >= GGMLType.Q4_0.blockSize) {
            getFloatArray(srcIndex, decoded, 0, GGMLType.Q4_0.blockSize);
            for (int i = 0; i < GGMLType.Q4_0.blockSize; i++) {
                that.setFloat(dstIndex + i, (float) FLOAT_ARRAY_HANDLE.get(decoded, i));
            }
            srcIndex += GGMLType.Q4_0.blockSize;
            dstIndex += GGMLType.Q4_0.blockSize;
            remaining -= GGMLType.Q4_0.blockSize;
        }

        // Decode and copy any leftover elements one by one
        if (remaining > 0) {
            getFloatArray(srcIndex, decoded, 0, remaining);
            for (int i = 0; i < remaining; i++) {
                that.setFloat(dstIndex + i, (float) FLOAT_ARRAY_HANDLE.get(decoded, i));
            }
        }
    }
    
    /**
     * dot product which has the getFloat method inlined in such a way that it processes full blocks at once.
     * This gains a > 2.5 times token/s performance increase compared to the generic dot-getFloat implementation.
     */
    public final float dot(final int thisOffset, final FloatTensor that, final int thatOffset, final int size) {
        float result = 0.0f;
        int index = 0;
        final int blockLimit = size - (size % GGMLType.Q4_0.blockSize);
        
        // Process full blocks, one block are 32 elements, 16 quantized values and 1 scale
        while (index < blockLimit) {
            
            // Get this block
            final int thisBlockIndex = (thisOffset + index) >>> LOG2_QUANT_BLOCK_SIZE; // (thisOffset + index) / QUANT_BLOCK_SIZE;
            final int thisBlockOffset = thisBlockIndex * GGMLType.Q4_0.typeSize;
            final float thisScale = FloatTensor.float16ToFloat(buffer.getShort((int) (long) thisBlockOffset));
            
            // Process block: read all quantized values from this block at once
            final int quantOffset = thisBlockOffset + QUANT_FLOAT16_BYTES;
            float blockResult = 0.0f;
            final int thatIndex = thatOffset + index;
            if (that instanceof ArrayFloatTensor) {
                final ArrayFloatTensor thatArray = (ArrayFloatTensor) that;
                final float[] b = thatArray.values;
                for (int i = 0; i < QUANT_HALF_BLOCK; ++i) {
                    final byte packed = buffer.get(quantOffset + i);
                    final float valB0 = (float) FLOAT_ARRAY_HANDLE.get(b, thatIndex + i);
                    final float valB1 = (float) FLOAT_ARRAY_HANDLE.get(b, thatIndex + i + QUANT_HALF_BLOCK);
                    blockResult += ((packed & 0x0F) - 8) * valB0 + (((packed >>> 4) & 0x0F) - 8) * valB1;
                }
            } else {
                for (int i = 0; i < QUANT_HALF_BLOCK; ++i) {
                    final byte packed = buffer.get(quantOffset + i);
                    blockResult += ((packed & 0x0F) - 8) * that.getFloat(thatIndex + i) + (((packed >>> 4) & 0x0F) - 8) * that.getFloat(thatIndex + i + QUANT_HALF_BLOCK);
                }
            }
            result += blockResult * thisScale;
            index += GGMLType.Q4_0.blockSize;
        }
        
        // Process remaining elements
        for (; index < size; index++) {
            result += this.getFloat(thisOffset + index) * that.getFloat(thatOffset + index);
        }
        
        return (float) result;
    }
    
}