package net.yacy.repository;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.Assert;
import org.junit.Test;
import static org.junit.Assert.*;

public class BlacklistTest {

    /**
     * Simulates contains method, of class Blacklist as proof for pattern.toString
     * needed and works
     */
    @Test
    public void testContains() {
        String path = ".*"; // simplest test pattern

        Pattern pattern = Pattern.compile(path, Pattern.CASE_INSENSITIVE);

        // pattern list as in Blacklist class
        // ConcurrentMap<BlacklistType, Map<String, Set<Pattern>>> hostpaths_matchable;
        // simulate last part, path pattern set
        Set<Pattern> hostList = new HashSet<Pattern>();
        hostList.add(pattern);

        // proof assumption pattern(path) != path
		@SuppressWarnings("unlikely-arg-type")
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
    
    /**
     * Tests static Blacklist.isListed() function with some sample patterns.
     */
    @Test
    public void testIsListed() {
    	final Map<String, Set<Pattern>> blacklistMapMatched = new HashMap<>();
    	Set<Pattern> patterns = new HashSet<>();
    	patterns.add(Pattern.compile(".*"));
    	blacklistMapMatched.put("stats.example.com", patterns);
    	
    	patterns = new HashSet<>();
    	patterns.add(Pattern.compile(".*"));
    	blacklistMapMatched.put("site.blacklisted.net", patterns);
    	
    	patterns = new HashSet<>();
    	patterns.add(Pattern.compile("data/js/\\d*\\.js"));
    	blacklistMapMatched.put("js.blacklisted.org", patterns);
    	
    	
    	patterns = new HashSet<>();
    	patterns.add(Pattern.compile(".*"));
    	blacklistMapMatched.put("ftp.*", patterns);
    	
    	patterns = new HashSet<>();
    	patterns.add(Pattern.compile("bestenlisten/.*"));
    	patterns.add(Pattern.compile("produkte/.*"));
    	blacklistMapMatched.put("esample.de", patterns);
    	
    	final Map<String, Set<Pattern>> blacklistMapNotMatched = new HashMap<>();
    	patterns = new HashSet<>();
    	patterns.add(Pattern.compile(".*"));
    	blacklistMapNotMatched.put("mobil\\..*", patterns);
    	
    	patterns = new HashSet<>();
    	patterns.add(Pattern.compile("counter\\?.*"));
    	blacklistMapNotMatched.put(".*samples.fr", patterns);
    	
    	patterns = new HashSet<>();
    	patterns.add(Pattern.compile(".*\\.js"));
    	patterns.add(Pattern.compile(".*\\.jpg"));
    	patterns.add(Pattern.compile(".*BannerAd.*"));
    	
    	// Form "(.*/|)term.*" should be preferred over "(.*/)*term.*" which is consuming far too much CPU on JDK 7 and URLs with many path segments
    	
    	patterns.add(Pattern.compile("(.*/|)search.*"));
    	patterns.add(Pattern.compile("(.*/|)bizad.*"));
    	patterns.add(Pattern.compile("(.*/|)member/.*"));
    	blacklistMapNotMatched.put(".*.*", patterns);
    	
    	Assert.assertTrue(Blacklist.isListed("site.blacklisted.net", "", blacklistMapMatched, blacklistMapNotMatched));
    	Assert.assertTrue(Blacklist.isListed("site.blacklisted.net", "/index.html", blacklistMapMatched, blacklistMapNotMatched));
    	Assert.assertTrue(Blacklist.isListed("mobil.news.fr", "/index.htm", blacklistMapMatched, blacklistMapNotMatched));
    	Assert.assertTrue(Blacklist.isListed("mobil.news.fr", "/news/latest.html", blacklistMapMatched, blacklistMapNotMatched));
    	Assert.assertTrue(Blacklist.isListed("fr.notblacklisted.org", "/script.js", blacklistMapMatched, blacklistMapNotMatched));
    	Assert.assertTrue(Blacklist.isListed("fr.notblacklisted.org", "/js/script.js", blacklistMapMatched, blacklistMapNotMatched));
    	

    	Assert.assertFalse(Blacklist.isListed("fr.notblacklisted.org", "/index.html", blacklistMapMatched, blacklistMapNotMatched));
    	Assert.assertFalse(Blacklist.isListed("js.blacklisted.org", "/index.html", blacklistMapMatched, blacklistMapNotMatched));
    	
    	Assert.assertTrue(Blacklist.isListed("fr.notblacklisted.org", "/search.html", blacklistMapMatched, blacklistMapNotMatched));
    	Assert.assertTrue(Blacklist.isListed("fr.notblacklisted.org", "/aa/search.html", blacklistMapMatched, blacklistMapNotMatched));
    	Assert.assertTrue(Blacklist.isListed("fr.notblacklisted.org", "/aa/bb/search.html", blacklistMapMatched, blacklistMapNotMatched));
    	Assert.assertTrue(Blacklist.isListed("fr.notblacklisted.org", "/aa/bb/search/index.html", blacklistMapMatched, blacklistMapNotMatched));
    	Assert.assertTrue(Blacklist.isListed("fr.notblacklisted.org", "/search/index.html", blacklistMapMatched, blacklistMapNotMatched));
    	Assert.assertTrue(Blacklist.isListed("fr.notblacklisted.org", "/searchengine/index.html", blacklistMapMatched, blacklistMapNotMatched));
    	Assert.assertTrue(Blacklist.isListed("fr.notblacklisted.org", "/searchengine", blacklistMapMatched, blacklistMapNotMatched));
    	Assert.assertTrue(Blacklist.isListed("fr.notblacklisted.org", "/aaa/searchengine", blacklistMapMatched, blacklistMapNotMatched));
    	
    	Assert.assertFalse(Blacklist.isListed("fr.notblacklisted.org", "/thesearch.html", blacklistMapMatched, blacklistMapNotMatched));
    	Assert.assertFalse(Blacklist.isListed("fr.notblacklisted.org", "/aa/thesearch.html", blacklistMapMatched, blacklistMapNotMatched));
    	    	
    	Assert.assertFalse(Blacklist.isListed("fr.notblacklisted.org", "/path/with/many/segments/e/f/g/h/i/j/k/l/m/n/o/p/q/r/s/t/u/v/w/x/y/z/file.html", blacklistMapMatched, blacklistMapNotMatched));
    }

}
