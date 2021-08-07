package Common;

import java.util.Collection;
import java.util.Iterator;

public class LongVector {
    protected long[] data = new long[10];
    int capacityIncr = 10;
    int elt = 0;
    
    public LongVector() {
    }
    
    public int size() {
        return elt;
    }
    
    public void ensureAdditionalCapacity(int addCapacity) {
    	ensureCapacity(data.length + addCapacity);
    }
    
    private void ensureCapacity(int minCapacity) {
        int oldCapacity = data.length;
        if (minCapacity > oldCapacity) {
            long [] oldData = data;
            int newCapacity = oldCapacity + capacityIncr;
            if (newCapacity < minCapacity) {
                newCapacity = minCapacity;
            }
            data = new long[newCapacity];
            System.arraycopy(oldData, 0, data, 0, elt);
        }
    }
    
    public long get(int index) {
        if (index >= elt)
            throw new ArrayIndexOutOfBoundsException(index);
        
        return data[index];
    }
    
    public void Reserve(int s) {
        ensureCapacity(s);
    }
    
    public void add(long b) {
        ensureCapacity(elt + 1);
        data[elt++] = b;
    }
    
    public void addAll(Collection c) {
    	ensureCapacity(elt + c.size());
    	Iterator it = c.iterator();
    	while (it.hasNext())
    		data[elt++] = ((Long)it.next()).longValue();
    }
    
    public void addAll(Long[] b) {
    	ensureCapacity(elt + b.length);
    	for (int i=0; i<b.length; i++)
    		data[elt++] = ((Long)b[i]).longValue();
    }
    
    public void set(int index, long value) {
    	if (index >= data.length)
    		throw new ArrayIndexOutOfBoundsException(index);
    	data[index] = value;
    	elt = index + 1;
    }
    
    public void setRange(int start, long value) {
    	setRange(start, data.length - start, value);
    }
    
    public void setRange(int start, int length, long value) {
    	if (start + length > data.length)
    		throw new ArrayIndexOutOfBoundsException("start = " + start + ", length = " + length);
    	for (int i=0; i<length; i++)
    		data[start + i] = value;
    	elt = start + length;
    }
    
    public void clear() {
        elt = 0;
    }
    
    public boolean isEmpty() {
        return elt == 0;
    }
    
    public long Back() {
        if (elt < 1)
            throw new ArrayIndexOutOfBoundsException(0);
        
        return data[elt-1];
    }
    
    public long Front() {
        if (elt < 1)
            throw new ArrayIndexOutOfBoundsException(0);
        
        return data[0];
    }
    
    public void DeleteBack() {
        // Delete(_size - 1);
        remove(elt-1);
    }
    
    // TODO
    public long remove(int index) {
        if (index >= elt)
            throw new ArrayIndexOutOfBoundsException(index);
        long oldValue = data[index];
        
        int numMoved = elt - index - 1;
        Integer n = Integer.valueOf(elt);
        if (numMoved > 0)
            System.arraycopy(n, index+1, n, index,numMoved);
        elt = n.intValue();
        // data[--elt] = null; // Let gc do its work
        
        return oldValue;
    }
    
}
