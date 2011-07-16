package net.yacy.kelondro.order;

import java.util.Comparator;

public interface Sortable<A> extends Comparator<A> {

    public int size();

    public A get(final int index, final boolean clone);

    public void delete(int i);

    public A buffer();

    public void swap(int i, int j, A buffer);

}
