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
import net.yacy.ai.llama3.Model.GGMLType;

public class DirectBufferFloatTensor extends FloatTensor implements Tensor {

    final ByteBuffer byteBuffer; // must be direct

    public DirectBufferFloatTensor(ByteBuffer bb) {
        if (bb.isDirect()) {
            this.byteBuffer = bb.slice().order(bb.order());
        } else {
            int capacityBytes = bb.remaining();
            ByteBuffer direct = ByteBuffer.allocateDirect(capacityBytes).order(bb.order());
            direct.put(bb.slice());
            direct.flip();
            this.byteBuffer = direct.slice().order(bb.order());
        }
    }
    
    public DirectBufferFloatTensor(final float[] values) {
        int capacityBytes = values.length * Float.BYTES;
        this.byteBuffer = ByteBuffer.allocateDirect(capacityBytes).order(ByteOrder.nativeOrder());
        for (int i = 0; i < values.length; i++) {
            this.byteBuffer.putFloat(i << 2, values[i]);
        }
    }

    public static Tensor allocate(final int... dims) {
        int numberOfElements = Tensor.numberOfElements(dims);
        int bytesNeeded = numberOfElements * Float.BYTES;
        ByteBuffer buffer = ByteBuffer.allocateDirect(bytesNeeded).order(ByteOrder.nativeOrder());
        return new DirectBufferFloatTensor(buffer);
    }

    @Override
    public final int size() {
        return this.byteBuffer.capacity() / Float.BYTES;
    }

    @Override
    public final float getFloat(final int index) {
        final int i = this.byteBuffer.getInt(index << 2);
        //final int base = index << 2;
        //int i = (this.byteBuffer.get(base) & 0xFF) | ((this.byteBuffer.get(base + 1) & 0xFF) << 8) | ((this.byteBuffer.get(base + 2) & 0xFF) << 16) | ((this.byteBuffer.get(base + 3) & 0xFF) << 24);
        return Float.intBitsToFloat(i);
    }

    @Override
    public final void setFloat(final int index, final float value) {
        final int i = Float.floatToRawIntBits(value);
        this.byteBuffer.putInt(index << 2, i);
        //int base = index << 2;
        //this.byteBuffer.put(base++, (byte) ( i        & 0xFF)); // Little-endian:
        //this.byteBuffer.put(base++, (byte) ((i >> 8)  & 0xFF));
        //this.byteBuffer.put(base++, (byte) ((i >> 16) & 0xFF));
        //this.byteBuffer.put(base, (byte) ((i >> 24) & 0xFF));
    }
    
    @Override
    public final GGMLType type() {
        return GGMLType.F32;
    }

    /*
    @Override
    public final FloatTensor fillInPlace(final int thisOffset, final int size, final float value) {
        int end = thisOffset + size;
        final int x = Float.floatToRawIntBits(value);
        
        if (x == 0) {
            int p = thisOffset << 2;
            int bytec = (end - thisOffset) << 2;
            for (int i = p; i < p + bytec; i++) {
                this.byteBuffer.put(i, (byte) 0);
            }
        } else {
            int p = thisOffset << 2;
            for (int i = thisOffset; i < end; i++) {
                this.byteBuffer.putInt(p, x);
                p += 4;
            }
        }
        return this;
    }
    
    @Override
    public final float dot(final int thisOffset, final Tensor that, final int thatOffset, final int size) {
        float result = 0f;
        int p = thisOffset << 2;
        for (int j = thatOffset; j < thatOffset + size; j++) {
            result += Float.intBitsToFloat(this.byteBuffer.getInt(p)) * that.getFloat(j);
            p += 4;
        }
        return result;
    }

    @Override
    public int argmax() {
        int size = this.size();
        assert size > 0;
        int maxIndex = 0;
        float maxValue = this.getFloat(maxIndex);
        int endIndex = size;
        for (int i = 0; i < endIndex; ++i) {
            float f = this.getFloat(i);
            if (f > maxValue) {
                maxValue = f;
                maxIndex = i;
            }
        }
        return maxIndex;
    }

    @Override
    public final Tensor mapWithIndexInPlace(final int thisOffset, final int size, final Tensor.MapWithIndexFunction mapWithIndexFunction) {
        int endOffset = thisOffset + size;
        for (int i = thisOffset; i < endOffset; ++i) {
            this.setFloat(i, mapWithIndexFunction.apply(this.getFloat(i), i));
        }
        return this;
    }
    
    @Override
    public final FloatTensor fillInPlace(final int thisOffset, final int size, final float value) {
        int end = thisOffset + size;
        for (int i = thisOffset; i < end; i++) {
            this.setFloat(i, value);
        }
        return this;
    }

    @Override
    public final FloatTensor mapInPlace(final int thisOffset, final int size, MapFunction mapFunction) {
        int end = thisOffset + size;
        for (int i = thisOffset; i < end; i++) {
            float current = this.getFloat(i);
            this.setFloat(i, mapFunction.apply(current));
        }
        return this;
    }

    @Override
    public Tensor saxpyInPlace(final int thisOffset, final Tensor that, final int thatOffset, final int size, final float a) {
        // this[thatOffset ... thatOffset + size) = a * that[thatOffset ... thatOffset + size) + this[thisOffset ... thisOffset + size)
        for (int i = 0; i < size; ++i) {
            this.setFloat(thisOffset + i, a * that.getFloat(thatOffset + i) + this.getFloat(thisOffset + i));
        }
        return this;
    }
    */
}
