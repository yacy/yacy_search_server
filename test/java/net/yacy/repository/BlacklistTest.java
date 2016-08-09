package net.yacy.repository;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import net.yacy.cora.document.id.Punycode;
import org.junit.Test;
import static org.junit.Assert.*;

public class BlacklistTest {

    /**
     * Simulates contains method, of class Blacklist as proof for pattern.toString
     * needed and works
     */
    @Test
    public void testContains() throws Punycode.PunycodeException {
        String path = ".*"; // simplest test pattern

        Pattern pattern = Pattern.compile(path, Pattern.CASE_INSENSITIVE);

        // pattern list as in Blacklist class
        // ConcurrentMap<BlacklistType, Map<String, Set<Pattern>>> hostpaths_matchable;
        // simulate last part, path pattern set
        Set<Pattern> hostList = new HashSet<Pattern>();
        hostList.add(pattern);

        // proof assumption pattern(path) != path
        boolean ret = hostList.contains(path);
        assertFalse("match blacklist pattern " + path, ret);

        // proof pattern.toString match works
        for (Pattern hp : hostList) {
            String hpxs = hp.pattern();
            if (hpxs.equals(path)) {
                ret = true;
                break;
            }
        }
        assertTrue("match blacklist pattern " + path, ret);
    }

}
