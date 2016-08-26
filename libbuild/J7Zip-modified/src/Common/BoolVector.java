package Common;

import java.util.Collection;
import java.util.Iterator;

public class BoolVector {
    
    protected boolean[] data;
    int capacityIncr = 10;
    int elt = 0;
    
    public BoolVector() {
    	this.data = new boolean[10];
    }
    
    public BoolVector(int size) {
    	this.data = new boolean[size];
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
            boolean [] oldData = data;
            int newCapacity = oldCapacity + capacityIncr;
            if (newCapacity < minCapacity) {
                newCapacity = minCapacity;
            }
            data = new boolean[newCapacity];
            System.arraycopy(oldData, 0, data, 0, elt);
        }
    }
    
    public boolean get(int index) {
        if (index >= elt)
            throw new ArrayIndexOutOfBoundsException(index);
        
        return data[index];
    }
    
    public void Reserve(int s) {
        ensureCapacity(s);
    }
    
    public void add(boolean b) {
        ensureCapacity(elt + 1);
        data[elt++] = b;
    }
    
    public void addAll(Collection c) {
    	ensureCapacity(elt + c.size());
    	Iterator it = c.iterator();
    	while (it.hasNext())
    		data[elt++] = ((Boolean)it.next()).booleanValue();
    }
    
    public void addAll(Boolean[] b) {
    	ensureCapacity(elt + b.length);
    	for (int i=0; i<b.length; i++)
    		data[elt++] = ((Boolean)b[i]).booleanValue();
    }
    
    public void set(int index, boolean value) {
    	if (index >= data.length)
    		throw new ArrayIndexOutOfBoundsException(index);
    	data[index] = value;
    	elt = index + 1;
    }
    
    public void setRange(int start, boolean value) {
    	setRange(start, data.length - start, value);
    }
    
    public void setRange(int start, int length, boolean value) {
    	if (start + length > data.length)
    		throw new ArrayIndexOutOfBoundsException("start = " + start + ", length = " + length);
    	for (int i=0; i<length; i++)
    		data[start + i] = value;
    	elt = start + length;
    }
    
    public void setBoolVector(BoolVector v) {
    	this.data = v.data;
    	this.elt = v.elt;
    }
    
    public void clear() {
        elt = 0;
    }
    
    public boolean isEmpty() {
        return elt == 0;
    }
}
