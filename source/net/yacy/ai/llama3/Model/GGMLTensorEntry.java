/**
 * GGMLTensorEntry.java

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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Arrays;
import java.util.Objects;

import net.yacy.ai.llama3.Tensor.FloatTensor;
import net.yacy.ai.llama3.Tensor.Q4_0FloatTensor;
import net.yacy.ai.llama3.Tensor.Q8_0FloatTensor;

public final class GGMLTensorEntry {

    private final ByteBuffer buffer;
    private final String name;
    private final GGMLType ggmlType;
    private final int[] shape;

    
    public GGMLTensorEntry(String name, GGMLType ggmlType, int[] shape, ByteBuffer buffer) throws IOException {
        assert buffer.isDirect() : "Buffer must be a direct ByteBuffer";
        this.buffer = buffer;
        buffer.order(ByteOrder.nativeOrder()); // Set correct byte order
        this.name = Objects.requireNonNull(name);
        this.ggmlType = Objects.requireNonNull(ggmlType);
        this.shape = Arrays.copyOf(Objects.requireNonNull(shape), shape.length);
    }
    
    public float getFloat(int index) {
        return buffer.getFloat(index * Float.BYTES);
    }
    
    public String name() {
        return name;
    }

    public GGMLType ggmlType() {
        return ggmlType;
    }

    public int[] shape() {
        return Arrays.copyOf(shape, shape.length);
    }

    public FloatBuffer toFloatBuffer() {
        switch (ggmlType) {
            case F32: return buffer.order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer();
            default: throw new UnsupportedOperationException("Conversion to " + ggmlType);
        }
    }
    
    public FloatTensor loadQuantized() {
        FloatTensor tensor = null;
        switch (ggmlType) {
            //case F32: return new F32FloatTensor(FloatTensor.numberOfElements(entry.shape()), entry.memorySegment());
            case Q8_0: tensor = new Q8_0FloatTensor(FloatTensor.numberOfElements(this.shape()), this.buffer); break;
            case Q4_0: tensor = new Q4_0FloatTensor(FloatTensor.numberOfElements(this.shape()), this.buffer); break;
            default: throw new UnsupportedOperationException("Quantization format " + ggmlType);
        }
        return tensor;
        
        // create a new ArrayFloatTensor(final float[] values) 
        //float[] values = new float[tensor.size()];
        // copy the values from the tensor to the float array
        //for (int i = 0; i < values.length; i++) {
        ///    values[i] = tensor.getFloat(i);
        //}
        //return new DirectBufferFloatTensor(values);
        //return new F16FloatTensor(values);
        //return new BF16FloatTensor(values);
        //return new ArrayFloatTensor(values);
    }
}