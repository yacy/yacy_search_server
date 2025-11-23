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

public class DirectBufferFloatTensor extends AbstractFloatTensor implements FloatTensor {

    final ByteBuffer byteBuffer; // must be direct
    //private static final VarHandle VH = MethodHandles.byteBufferViewVarHandle(int[].class, ByteOrder.nativeOrder());
    
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

    public static FloatTensor allocate(final int... dims) {
        int numberOfElements = AbstractFloatTensor.numberOfElements(dims);
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
        return this.byteBuffer.getFloat(index << 2);
        //return Float.intBitsToFloat(this.byteBuffer.getInt(index << 2));
        //return Float.intBitsToFloat((int) VH.get(this.byteBuffer, index << 2));
    }

    @Override
    public final void setFloat(final int index, final float value) {
        this.byteBuffer.putFloat(index << 2, value);
        //VH.set(this.byteBuffer, index << 2, Float.floatToRawIntBits(value));
        //this.byteBuffer.putInt(index << 2, Float.floatToRawIntBits(value));
    }

    @Override
    public final GGMLType type() {
        return GGMLType.F32;
    }
    
    public float dot(final int thisOffset,
            final FloatTensor that,
            final int thatOffset,
            final int size) {

        float sum0 = 0f, sum1 = 0f, sum2 = 0f, sum3 = 0f;
        final int limit = size & ~3;

        if (that instanceof DirectBufferFloatTensor) {

            DirectBufferFloatTensor thatb = (DirectBufferFloatTensor) that;
            
            int i = thisOffset << 2;
            int k = thatOffset << 2;
    
            // Loop-Unrolling
            for (int j = 0; j < limit; j += 4) {
                sum0 += this.byteBuffer.getFloat(i     ) * thatb.byteBuffer.getFloat(k     );
                sum1 += this.byteBuffer.getFloat(i +  4) * thatb.byteBuffer.getFloat(k +  4);
                sum2 += this.byteBuffer.getFloat(i +  8) * thatb.byteBuffer.getFloat(k +  8);
                sum3 += this.byteBuffer.getFloat(i + 12) * thatb.byteBuffer.getFloat(k + 12);
                i += 16;
                k += 16;
            }
            
        } else {
        
            int i = thisOffset << 2;
            int k = thatOffset;
    
            // Loop-Unrolling
            for (int j = 0; j < limit; j += 4) {
                sum0 += this.byteBuffer.getFloat(i     ) * that.getFloat(k    );
                sum1 += this.byteBuffer.getFloat(i +  4) * that.getFloat(k + 1);
                sum2 += this.byteBuffer.getFloat(i +  8) * that.getFloat(k + 2);
                sum3 += this.byteBuffer.getFloat(i + 12) * that.getFloat(k + 3);
                i += 16;
                k += 4;
            }
    
        }

        float result = sum0 + sum1 + sum2 + sum3;

        // remaining values
        for (int j = limit; j < size; j++) {
            result += this.byteBuffer.getFloat(j << 2) * that.getFloat(j);
        }
        
        return result;
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
