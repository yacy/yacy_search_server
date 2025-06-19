/**
 * ArrayFloatTensor.java

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

import java.util.Arrays;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

import net.yacy.ai.llama3.Model.GGMLType;

public final class ArrayFloatTensor extends FloatTensor {

    public final float[] values;
    private static final VarHandle FLOAT_ARRAY_HANDLE;

    static {
        try {
            FLOAT_ARRAY_HANDLE = MethodHandles.arrayElementVarHandle(float[].class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to get VarHandle for float[]", e);
        }
    }

    public ArrayFloatTensor(final float[] values) {
        this.values = values;
    }

    public static FloatTensor allocate(final int... dims) {
        int numberOfElements = FloatTensor.numberOfElements(dims);
        return new ArrayFloatTensor(new float[numberOfElements]);
    }

    @Override
    public final int size() {
        return this.values.length;
    }

    @Override
    public final float getFloat(final int index) {
        return (float) FLOAT_ARRAY_HANDLE.get(this.values, index);
    }

    @Override
    public final void setFloat(final int index, final float value) {
    	FLOAT_ARRAY_HANDLE.set(this.values, index, value);
    }

    @Override
    public GGMLType type() {
        return GGMLType.F32;
    }

    @Override
    public final FloatTensor fillInPlace(final int thisOffset, final int size, final float value) {
        Arrays.fill(this.values, thisOffset, thisOffset + size, value);
        return this;
    }

    @Override
    public final FloatTensor mapInPlace(final int thisOffset, final int size, MapFunction mapFunction) {
        int endIndex = thisOffset + size;
        for (int i = thisOffset; i < endIndex; ++i) {
            this.values[i] = mapFunction.apply(this.values[i]);
        }
        return this;
    }
    
    @Override
    public final void copyTo(final int thisOffset, final FloatTensor that, final int thatOffset, final int size) {
    	final int delta = thisOffset - thatOffset;
    	if (that instanceof ArrayFloatTensor) {
    		final ArrayFloatTensor aft = (ArrayFloatTensor) that;
	        int endOffset = thatOffset + size;
	        for (int i = thatOffset; i < endOffset; ++i) {
	        	FLOAT_ARRAY_HANDLE.set(aft.values, i, (float) FLOAT_ARRAY_HANDLE.get(this.values, i + delta));
	        }
    	} else {
	        int endOffset = thatOffset + size;
	        for (int i = thatOffset; i < endOffset; ++i) {
	        	that.setFloat(i, (float) FLOAT_ARRAY_HANDLE.get(this.values, i + delta));
	        }
    	}
    }
    
    @Override
    public final float dot(final int thisOffset, final FloatTensor that, final int thatOffset, final int size) {
    	float result = 0f;
    	if (that instanceof ArrayFloatTensor) {
    		final ArrayFloatTensor aft = (ArrayFloatTensor) that;
            final float[] a = this.values;
            final float[] b = aft.values;

            for (int i = 0; i < size; i++) {
                final float valA = (float) FLOAT_ARRAY_HANDLE.get(a, thisOffset + i);
                final float valB = (float) FLOAT_ARRAY_HANDLE.get(b, thatOffset + i);
                result += valA * valB;
            }
    	} else {
            final float[] a = this.values;
	        for (int i = 0; i < size; i++) {
	        	final float valA = (float) FLOAT_ARRAY_HANDLE.get(a, thisOffset + i);
	            result += valA * that.getFloat(thatOffset + i);
	        }
    	}
        return result;
    }
    
    @Override
    public final void matmul(final FloatTensor that, final FloatTensor out, final int dim0, final int dim1) {
    	if (that instanceof ArrayFloatTensor) {
    		parallelFor(0, dim0, i -> ((ArrayFloatTensor) out).values[i] = this.dot(i * dim1, that, 0, dim1));
    	} else {
    		parallelFor(0, dim0, i -> out.setFloat(i, this.dot(i * dim1, that, 0, dim1)));
    	}
    }
    
    @Override
    public final void matmul(final int context, final FloatTensor[] that, final FloatTensor[] out, final int dim0, final int dim1) {
        if (that.length != out.length) {
            throw new IllegalArgumentException(String.format("that.len=%d, out.len=%d", that.length, out.length));
        }
        parallelForLong(0, dim0 * context, ti -> {
            int idxArr = (int) (ti / dim0);
            int i = (int) (ti % dim0);
            out[idxArr].setFloat(i, this.dot(i * dim1, that[idxArr], 0, dim1));
        });
    }

    @Override
    public final FloatTensor saxpyInPlace(final int thisOffset, final FloatTensor that, final int thatOffset, final int size, final float a) {
        if (that instanceof Q4_0FloatTensor) {
            Q4_0FloatTensor qft = (Q4_0FloatTensor) that;
            final float[] decodedBlock = Q4_0FloatTensor.scratchBuffer.get();
            int remaining = size;
            int i = 0;

            while (remaining > 0) {
                int chunkSize = Math.min(remaining, GGMLType.Q4_0.blockSize);
                qft.getFloatArray(thatOffset + i, decodedBlock, 0, chunkSize);
                for (int j = 0; j < chunkSize; ++j) {
                    int dstIdx = thisOffset + i + j;
                    this.setFloat(dstIdx, a * decodedBlock[j] + this.getFloat(dstIdx));
                }
                i += chunkSize;
                remaining -= chunkSize;
            }
        } else {
            for (int i = 0; i < size; ++i) {
                int idx = thisOffset + i;
                this.setFloat(idx, a * that.getFloat(thatOffset + i) + this.getFloat(idx));
            }
        }

        return this;
    }

}