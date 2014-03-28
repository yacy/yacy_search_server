package net.yacy.data.ymark;

public class YMarkTag implements Comparable<YMarkTag>{
	private String name;
	private int size;
	
	public YMarkTag(final String tag) {
		this.name = tag.toLowerCase();
		this.size = 1;
	}
	
	public YMarkTag(final String tag, final int size) {
		this.name = tag.toLowerCase();
		this.size = size;
	}
	
	public int inc() {
		return this.size++;
	}
	
	public int dec() {
		if(this.size > 0)
			this.size--;
		return this.size;
	}
	
	public String name() {
		return this.name;
	}
	
	public int size() {
		return this.size;
	}

	@Override
    public int compareTo(YMarkTag tag) {
		if(this.name.equals(tag.name()))
			return 0;
		if(tag.size() < this.size)
			return -1;
		else if(tag.size() > this.size)
			return 1;
		else
			return this.name.compareTo(tag.name());
	}
}

