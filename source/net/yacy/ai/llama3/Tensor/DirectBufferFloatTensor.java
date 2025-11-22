/**
 *  DirectBufferFloatTensor
 *  Copyright 2025 by Michael Peter Christen
 *  First released 19.06.2025 at https://yacy.net
 *  
 **  This class was not part of the original llama3 implementation,
 **  but added later by the author to support different architectures.
 **  It therefore does not inherit the llama3 copyright.
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program in the file lgpl21.txt
 *  If not, see <http://www.gnu.org/licenses/>.
 */

package net.yacy.ai.llama3.Tensor;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import net.yacy.ai.llama3.Model.GGMLType;

public class DirectBufferFloatTensor extends FloatTensor implements Tensor {

    final ByteBuffer byteBuffer; // must be direct
    final FloatBuffer floatBuffer;

    public DirectBufferFloatTensor(ByteBuffer bb) {
        if (bb.isDirect()) {
            this.byteBuffer = bb;
        } else {
            int capacityBytes = bb.remaining();
            this.byteBuffer = ByteBuffer.allocateDirect(capacityBytes).order(bb.order());
            this.byteBuffer.put(bb.duplicate());
            this.byteBuffer.flip();
        }
        this.floatBuffer = this.byteBuffer.asFloatBuffer();
    }
    
    public DirectBufferFloatTensor(final float[] values) {
        int capacityBytes = values.length * Float.BYTES;
        this.byteBuffer = ByteBuffer.allocateDirect(capacityBytes).order(ByteOrder.nativeOrder());
        this.floatBuffer = this.byteBuffer.asFloatBuffer();
        this.floatBuffer.put(values);
    }

    public static Tensor allocate(final int... dims) {
        int numberOfElements = Tensor.numberOfElements(dims);
        int bytesNeeded = numberOfElements * Float.BYTES;
        ByteBuffer buffer = ByteBuffer.allocateDirect(bytesNeeded).order(ByteOrder.nativeOrder());
        return new DirectBufferFloatTensor(buffer);
    }

    @Override
    public final int size() {
        return this.floatBuffer.capacity();
    }

    @Override
    public final float getFloat(final int index) {
        return this.floatBuffer.get(index);
    }

    @Override
    public final void setFloat(final int index, final float value) {
        this.floatBuffer.put(index, value);
    }
    
    @Override
    public final GGMLType type() {
        return GGMLType.F32;
    }

    @Override
    public int argmax() {
        int size = this.size();
        assert size > 0;
        int maxIndex = 0;
        float maxValue = this.floatBuffer.get(maxIndex);
        int endIndex = size;
        for (int i = 0; i < endIndex; ++i) {
            float f = this.floatBuffer.get(i);
            if (f > maxValue) {
                maxValue = f;
                maxIndex = i;
            }
        }
        return maxIndex;
    }
    
    @Override
    public final float dot(final int thisOffset, final Tensor that, final int thatOffset, final int size) {
        float result = 0f;
        for (int j = 0; j < size; j++) {
            result += this.floatBuffer.get(thisOffset + j) * that.getFloat(thatOffset + j);
        }
        return result;
    }

    @Override
    public final Tensor mapWithIndexInPlace(final int thisOffset, final int size, final Tensor.MapWithIndexFunction mapWithIndexFunction) {
        int endOffset = thisOffset + size;
        for (int i = thisOffset; i < endOffset; ++i) {
            this.floatBuffer.put(i, mapWithIndexFunction.apply(this.floatBuffer.get(i), i));
        }
        return this;
    }
    
    @Override
    public final FloatTensor fillInPlace(final int thisOffset, final int size, final float value) {
        int end = thisOffset + size;
        for (int i = thisOffset; i < end; i++) {
            this.floatBuffer.put(i, value);
        }
        return this;
    }

    @Override
    public final FloatTensor mapInPlace(final int thisOffset, final int size, MapFunction mapFunction) {
        int end = thisOffset + size;
        for (int i = thisOffset; i < end; i++) {
            float current = this.floatBuffer.get(i);
            this.floatBuffer.put(i, mapFunction.apply(current));
        }
        return this;
    }

    @Override
    public Tensor saxpyInPlace(final int thisOffset, final Tensor that, final int thatOffset, final int size, final float a) {
        // this[thatOffset ... thatOffset + size) = a * that[thatOffset ... thatOffset + size) + this[thisOffset ... thisOffset + size)
        for (int i = 0; i < size; ++i) {
            this.floatBuffer.put(thisOffset + i, a * that.getFloat(thatOffset + i) + this.floatBuffer.get(thisOffset + i));
        }
        return this;
    }

}