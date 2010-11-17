package de.anomic.data;

/**
 * @author apfelmaennchen
 *
 * @param <K>
 * @param <V>
 */
public class YMarkKeyValueEntry<K extends Comparable<K>,V extends Comparable<V>> extends Object implements Comparable<YMarkKeyValueEntry<K,V>> {
	
	private K key;
	private V value;
	
	
	public YMarkKeyValueEntry() {
		this.key = null;
		this.value = null;
	}
	
	public YMarkKeyValueEntry(K key, V value) {
		this.key = key;
		this.value = value;
	}

	/** 
	 * The natural order of objects in this class is determind by their value components<br/>
	 * <b>Note:</b> this class has a natural ordering that is inconsistent with equals.
	 * @see java.lang.Comparable#compareTo(java.lang.Object)
	 */
	public int compareTo(YMarkKeyValueEntry<K,V> e) {
		return this.value.compareTo(e.value);
	}
	
	/**
	 * Two objects of this class are considered to be equal, if their keys are equal.<br/>
	 * <b>Note:</b> this class has a natural ordering that is inconsistent with equals.
	 */
	@SuppressWarnings("unchecked")
	@Override
	public boolean equals(Object obj) {
		if(this.getClass() == obj.getClass())
			return this.key.equals(((YMarkKeyValueEntry<K,V>)obj).getKey());
		else return false;
	}
	
	public K getKey() {
		return this.key;
	}
	
	public V getValue() {
		return this.value;
	}
	
	public void setValue(V value) {
		this.value = value;
	}
	
	public void setKey(K key) {
		this.key = key;
	}
	
	public void set(K key, V value) {
		this.key = key;
		this.value = value;
	}
}
