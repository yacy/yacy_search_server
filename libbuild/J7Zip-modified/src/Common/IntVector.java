package Common;

import java.util.Collection;
import java.util.Iterator;

public class IntVector {
    protected int[] data = new int[10];
    int capacityIncr = 10;
    int elt = 0;
    
    public IntVector() {
    }
    
    public IntVector(int size) {
    	this.data = new int[size];
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
            int [] oldData = data;
            int newCapacity = oldCapacity + capacityIncr;
            if (newCapacity < minCapacity) {
                newCapacity = minCapacity;
            }
            data = new int[newCapacity];
            System.arraycopy(oldData, 0, data, 0, elt);
        }
    }
    
    public int get(int index) {
        if (index >= elt)
            throw new ArrayIndexOutOfBoundsException(index);
        
        return data[index];
    }
    
    public void Reserve(int s) {
        ensureCapacity(s);
    }
    
    public void add(int b) {
        ensureCapacity(elt + 1);
        data[elt++] = b;
    }
    
    public void addAll(Collection c) {
    	ensureCapacity(elt + c.size());
    	Iterator it = c.iterator();
    	while (it.hasNext())
    		data[elt++] = ((Integer)it.next()).intValue();
    }
    
    public void addAll(Integer[] b) {
    	ensureCapacity(elt + b.length);
    	for (int i=0; i<b.length; i++)
    		data[elt++] = ((Integer)b[i]).intValue();
    }
    
    public void set(int index, int value) {
    	if (index >= data.length)
    		throw new ArrayIndexOutOfBoundsException(index);
    	data[index] = value;
    	elt = index + 1;
    }
    
    public void setRange(int start, int value) {
    	setRange(start, data.length - start, value);
    }
    
    public void setRange(int start, int length, int value) {
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
    
    public int Back() {
        if (elt < 1)
            throw new ArrayIndexOutOfBoundsException(0);
        
        return data[elt-1];
    }
    
    public int Front() {
        if (elt < 1)
            throw new ArrayIndexOutOfBoundsException(0);
        
        return data[0];
    }
    
    public void DeleteBack() {
        // Delete(_size - 1);
        remove(elt-1);
    }
    
    public int remove(int index) {
        if (index >= elt)
            throw new ArrayIndexOutOfBoundsException(index);
        int oldValue = data[index];
        
        int numMoved = elt - index - 1;
        Integer n = new Integer(elt);
        if (numMoved > 0)
            System.arraycopy(n, index+1, n, index,numMoved);
        elt = n.intValue();
        // data[--elt] = null; // Let gc do its work
        
        return oldValue;
    }
    
}
