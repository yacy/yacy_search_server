/**
 * FloatTensor.java

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

import net.yacy.ai.llama3.Model.GGMLType;

/**
 * Over-simplified, shapeless, float tensor.
 * <p>
 * Not a strict tensor, but rather just a sequence of floats, not required to be backed by memory
 * e.g. can represent a sequence of quantized floats.
 */
public abstract class FloatTensor implements Tensor {

    /**
     * Converts a 16-bit float (half-precision) to a 32-bit float (single-precision).
     *
     * @param h the half-precision float as a short
     * @return the single-precision float
     */
    public final static float float16ToFloat(short h) {

        final int hBits = h & 0xFFFF; // treat as unsigned
        final int sign = (hBits >>> 15) & 0x00000001;

        int exp =  (hBits >>> 10) & 0x0000001F;
        int mant =  hBits & 0x000003FF;
        int fBits;

        if (exp == 0) {
            if (mant == 0) {
                // zero
                fBits = sign << 31;
            } else {
                // subnormal
                while ((mant & 0x00000400) == 0) {
                    mant <<= 1;
                    exp -= 1;
                }
                exp += 1;
                mant &= ~0x00000400;
                fBits = (sign << 31) | ((exp + 127 - 15) << 23) | (mant << 13);
            }
        } else if (exp == 31) {
            // Inf/NaN
            fBits = (sign << 31) | 0x7F800000 | (mant << 13);
        } else {
            // normalized number
            fBits = (sign << 31) | ((exp + 127 - 15) << 23) | (mant << 13);
        }

        return Float.intBitsToFloat(fBits);
    }
    
    /**
     * Converts a 32-bit float (single-precision) to a 16-bit float (half-precision).
     *
     * @param f the single-precision float
     * @return the half-precision float as a short
     */
    public final static short floatToFloat16(final float f) {
        final int fBits = Float.floatToIntBits(f);
        final int sign = (fBits >>> 31) & 0x00000001;
        final int exp =  (fBits >>> 23) & 0x000000FF;
        final int mant =  fBits & 0x007FFFFF;

        short hBits;

        if (exp == 0xFF) {
            // Inf/NaN
            hBits = (short) ((sign << 15) | 0x7C00 | (mant >>> 13));
        } else if (exp < 112) {
            // subnormal or zero
            hBits = (short) (sign << 15);
        } else if (exp > 143) {
            // overflow to Inf
            hBits = (short) ((sign << 15) | 0x7C00);
        } else {
            // normalized number
            hBits = (short) ((sign << 15) | ((exp - 112) << 10) | (mant >>> 13));
        }

        return hBits;
    }
    
    public abstract int size();

    public abstract float getFloat(final int index);

    public abstract void setFloat(final int index, final float value);    
    
    abstract GGMLType type();

    public static int numberOfElements(final int... dimensions) {
        assert Arrays.stream(dimensions).allMatch(i -> i > 0);
        return Arrays.stream(dimensions).reduce(Math::multiplyExact).orElseThrow();
    }

    public static float scalarDot(final FloatTensor thiz, final int thisOffset, final Tensor that, final int thatOffset, final int size) {
        float result = 0f;
        for (int j = 0; j < size; j++) {
            result += thiz.getFloat(thisOffset + j) * that.getFloat(thatOffset + j);
        }
        return result;
    }

    public float dot(final int thisOffset, final Tensor that, final int thatOffset, final int size) {
        return scalarDot(this, thisOffset, that, thatOffset, size);
    }

    public void matmul(final Tensor that, final Tensor out, final int dim0, final int dim1) {
        Tensor.parallelFor(0, dim0, i -> out.setFloat(i, dot(i * dim1, that, 0, dim1)));
    }

    public void matmul(final int context, final Tensor[] that, final Tensor[] out, final int dim0, final int dim1) {
        if (that.length != out.length) {
            throw new IllegalArgumentException(String.format("that.len=%d, out.len=%d", that.length, out.length));
        }
        Tensor.parallelForLong(0, dim0 * context, ti -> {
            int idxArr = (int) (ti / dim0);
            int i = (int) (ti % dim0);
            out[idxArr].setFloat(i, dot(i * dim1, that[idxArr], 0, dim1));
        });
    }

    @FunctionalInterface
    public interface AggregateFunction {
        float apply(float acc, float value);
    }

    public float reduce(final int thisOffset, final int size, final float seed, final AggregateFunction reduce) {
        float result = seed;
        for (int i = 0; i < size; ++i) {
            result = reduce.apply(result, getFloat(thisOffset + i));
        }
        return result;
    }

    private float sum(final int thisOffset, final int size) {
        return reduce(thisOffset, size, 0f, Float::sum);
    }

    private float max(final int thisOffset, final int size) {
        return reduce(thisOffset, size, Float.NEGATIVE_INFINITY, Float::max);
    }

    public void copyTo(final int thisOffset, final Tensor that, final int thatOffset, final int size) {
        int endOffset = thatOffset + size;
        for (int i = thatOffset; i < endOffset; ++i) {
        	that.setFloat(i, this.getFloat(i - thatOffset + thisOffset));
        }
    }
    
    private int argmax(final int thisOffset, final int size) {
        assert size > 0;
        int maxIndex = thisOffset;
        float maxValue = this.getFloat(maxIndex);
        int endIndex = thisOffset + size;
        for (int i = thisOffset; i < endIndex; ++i) {
            float f = this.getFloat(i);
            if (f > maxValue) {
                maxValue = f;
                maxIndex = i;
            }
        }
        return maxIndex;
    }

    public int argmax() {
        return argmax(0, size());
    }

    public Tensor mapInPlace(final int thisOffset, final int size, MapFunction mapFunction) {
        int endIndex = thisOffset + size;
        for (int i = thisOffset; i < endIndex; ++i) {
            setFloat(i, mapFunction.apply(getFloat(i)));
        }
        return this;
    }

    public final Tensor mapInPlace(final MapFunction mapFunction) {
        return mapInPlace(0, size(), mapFunction);
    }

    public final Tensor mapWithIndexInPlace(final int thisOffset, final int size, final Tensor.MapWithIndexFunction mapWithIndexFunction) {
        int endOffset = thisOffset + size;
        for (int i = thisOffset; i < endOffset; ++i) {
            setFloat(i, mapWithIndexFunction.apply(getFloat(i), i));
        }
        return this;
    }

    private final Tensor addInPlace(final int thisOffset, final Tensor that, final int thatOffset, int size) {
        return mapWithIndexInPlace(thisOffset, size, (value, index) -> value + that.getFloat(index - thisOffset + thatOffset));
    }

    public final Tensor addInPlace(final Tensor that) {
        return addInPlace(0, that, 0, size());
    }

    private final Tensor multiplyInPlace(final int thisOffset, final Tensor that, final int thatOffset, final int size) {
        return mapWithIndexInPlace(thisOffset, size, (value, index) -> value * that.getFloat(index - thisOffset + thatOffset));
    }

    public final Tensor multiplyInPlace(final Tensor that) {
        return multiplyInPlace(0, that, 0, size());
    }

    public final Tensor divideInPlace(final int thisOffset, final int size, final float value) {
        return mapInPlace(thisOffset, size, f -> f / value);
    }

    public Tensor fillInPlace(final int thisOffset, final int size, final float value) {
        return mapInPlace(thisOffset, size, unused -> value);
    }

    public final Tensor softmaxInPlace(final int thisOffset, final int size) {
        // find max value (for numerical stability)
        float maxVal = max(thisOffset, size);
        // exp and sum
        mapInPlace(thisOffset, size, f -> (float) Math.exp(f - maxVal));
        float sum = sum(thisOffset, size);
        // normalize
        return divideInPlace(thisOffset, size, sum);
    }

    public Tensor saxpyInPlace(final int thisOffset, final Tensor that, final int thatOffset, final int size, final float a) {
        // this[thatOffset ... thatOffset + size) = a * that[thatOffset ... thatOffset + size) + this[thisOffset ... thisOffset + size)
        for (int i = 0; i < size; ++i) {
            this.setFloat(thisOffset + i, a * that.getFloat(thatOffset + i) + this.getFloat(thisOffset + i));
        }
        return this;
    }
}