package de.anomic.index;

import de.anomic.kelondro.kelondroOrder;

public class indexContainerOrder implements kelondroOrder {

    private kelondroOrder embeddedOrder;

    public indexContainerOrder(kelondroOrder embedOrder) {
        this.embeddedOrder = embedOrder;
    }

    public boolean wellformed(byte[] a) {
        return embeddedOrder.wellformed(a);
    }
    
    public boolean wellformed(byte[] a, int astart, int alength) {
        return embeddedOrder.wellformed(a, astart, alength);
    }
    
    public void direction(boolean ascending) {
        this.embeddedOrder.direction(ascending);
    }

    public long partition(byte[] key, int forks) {
        return this.embeddedOrder.partition(key, forks);
    }

    public int compare(Object a, Object b) {
        if ((a instanceof indexContainer) && (b instanceof indexContainer)) {
            return this.embeddedOrder.compare(((indexContainer) a).getWordHash(), ((indexContainer) b).getWordHash());
        }
        return this.embeddedOrder.compare(a, b);
    }

    public byte[] zero() {
        return this.embeddedOrder.zero();
    }

    public void rotate(byte[] zero) {
        this.embeddedOrder.rotate(zero);
    }

    public Object clone() {
        return new indexContainerOrder((kelondroOrder) this.embeddedOrder.clone());
    }

    public String signature() {
        return this.embeddedOrder.signature();
    }

    public long cardinal(byte[] key) {
        return this.embeddedOrder.cardinal(key);
    }

    public int compare(byte[] a, byte[] b) {
        return this.embeddedOrder.compare(a, b);
    }

    public int compare(byte[] a, int aoffset, int alength, byte[] b, int boffset, int blength) {
        return this.embeddedOrder.compare(a, aoffset, alength, b, boffset, blength);
    }

    public boolean equals(kelondroOrder otherOrder) {
        if (!(otherOrder instanceof indexContainerOrder)) return false;
        return this.embeddedOrder.equals(((indexContainerOrder) otherOrder).embeddedOrder);
    }
}
