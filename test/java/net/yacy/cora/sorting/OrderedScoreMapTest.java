package net.yacy.cora.sorting;

import net.yacy.cora.util.StringBuilderComparator;
import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.*;

/**
 * copied from the main() in OrderedScoreMap; nearly a unit test TODO
 */
public class OrderedScoreMapTest {

    @Test
    public void test1() {


        OrderedScoreMap<StringBuilder> w = new OrderedScoreMap<StringBuilder>(StringBuilderComparator.CASE_INSENSITIVE_ORDER);
        Random r = new Random();
        for (int i = 0; i < 10000; i++) {
            w.inc(new StringBuilder("a" + ((char) (('a') + r.nextInt(26)))));
        }
        for (StringBuilder s : w) System.out.println(s + ":" + w.get(s));
        System.out.println("--");
        w.shrinkToMaxSize(10);
        for (StringBuilder s : w) System.out.println(s + ":" + w.get(s));

        assertEquals(10, w.size() );
    }
}