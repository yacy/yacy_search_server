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

public class DirectBufferFloatTensor extends FloatTensor {

    final FloatBuffer floatBuffer;

    public DirectBufferFloatTensor(ByteBuffer byteBuffer) {
        if (byteBuffer.isDirect()) {
            this.floatBuffer = byteBuffer.asFloatBuffer();
        } else {
            int capacityBytes = byteBuffer.remaining();
            ByteBuffer directByteBuffer = ByteBuffer.allocateDirect(capacityBytes).order(byteBuffer.order());
            directByteBuffer.put(byteBuffer.duplicate());
            directByteBuffer.flip();
            this.floatBuffer = directByteBuffer.asFloatBuffer();
        }
    }
    
    public DirectBufferFloatTensor(final float[] values) {
        int capacityBytes = values.length * Float.BYTES;
        ByteBuffer directByteBuffer = ByteBuffer.allocateDirect(capacityBytes).order(ByteOrder.nativeOrder());
        this.floatBuffer = directByteBuffer.asFloatBuffer();
        this.floatBuffer.put(values);
    }

    public static FloatTensor allocate(final int... dims) {
        int numberOfElements = FloatTensor.numberOfElements(dims);
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
    public final float dot(final int thisOffset, final FloatTensor that, final int thatOffset, final int size) {
        float result = 0f;
        for (int j = 0; j < size; j++) {
            result += this.floatBuffer.get(thisOffset + j) * that.getFloat(thatOffset + j);
        }
        return result;
    }

    @Override
    public final FloatTensor fillInPlace(final int thisOffset, final int size, final float value) {
        int end = thisOffset + size;
        for (int i = thisOffset; i < end; i++) {
            floatBuffer.put(i, value);
        }
        return this;
    }

    @Override
    public final FloatTensor mapInPlace(final int thisOffset, final int size, MapFunction mapFunction) {
        int end = thisOffset + size;
        for (int i = thisOffset; i < end; i++) {
            float current = floatBuffer.get(i);
            floatBuffer.put(i, mapFunction.apply(current));
        }
        return this;
    }

}