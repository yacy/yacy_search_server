package net.yacy.cora.protocol;

import static org.junit.Assert.assertTrue;

import java.util.Date;

import org.junit.Test;

public class ResponseHeaderTest {

    /**
     * Test of age method, of class ResponseHeader.
     * testing many combination of header.date and header.last_modified
     * test goal: age is suppose to always >= 0
     */
    @Test
    public void testAge() {
        // because ResponseHeader caches date values internally on 1st access,
        // we need to create a new instance for every testcase

        ResponseHeader testhdr1 = new ResponseHeader(200);
        ResponseHeader testhdr2 = new ResponseHeader(200);
        ResponseHeader testhdr3 = new ResponseHeader(200);

        // access-sequence 1 = age() without accessing any date before
        // access-sequence 2 = access date() then age()
        // access-sequence 3 = access lastModified() then age()

        // test case with sorce: date=null lastmodified=null
        long age1 = testhdr1.age();

        testhdr2.date();
        testhdr3.lastModified();

        assertTrue("access-sequence 1 date=null lastmod=null AGE="+age1, age1 >= 0);

        testhdr2.lastModified();

        long age2 = testhdr2.age();
        assertTrue("access-sequence 2 date=null lastmod=null AGE="+age2, age2 >= 0);

        testhdr3.date();

        long age3 = testhdr3.age();
        assertTrue("access-sequence 3 date=null lastmod=null AGE="+age3, age3 >= 0);

        Date past = new Date(System.currentTimeMillis() / 2);
        Date future = new Date(System.currentTimeMillis() * 2);

        Date[] testdate = new Date[3];
        testdate[0] = past;
        testdate[1] = new Date();
        testdate[2] = future;
        String[] testdatename = new String[] {"past","now","future"}; // date names just for output

        for (int id = 0; id < testdate.length; id++) {

            // test case with sorce: date=testdate(x) lastmodified=null
            testhdr1 = new ResponseHeader(200);
            testhdr1.put(HeaderFramework.DATE, HeaderFramework.formatRFC1123(testdate[id]));
            testhdr2 = new ResponseHeader(200);
            testhdr2.put(HeaderFramework.DATE, HeaderFramework.formatRFC1123(testdate[id]));
            testhdr3 = new ResponseHeader(200);
            testhdr3.put(HeaderFramework.DATE, HeaderFramework.formatRFC1123(testdate[id]));

            age1 = testhdr1.age();
            testhdr2.date();
            testhdr3.lastModified();

            assertTrue("access-sequence 1."+id+" date=" + testdatename[id] + " lastmod=null AGE= " + age1, age1 >= 0);

            age2 = testhdr2.age();
            assertTrue("access-sequence 2."+id+" date=" + testdatename[id] + " lastmod=null AGE= " + age2, age2 >= 0);

            age3 = testhdr3.age();
            assertTrue("access-sequence 3."+id+" date=" + testdatename[id] + " lastmod=null AGE= " + age3, age3 >= 0);

            // test case with sorce: date=null lastmodified=testdat(x)
            testhdr1 = new ResponseHeader(200);
            testhdr1.put(HeaderFramework.LAST_MODIFIED, HeaderFramework.formatRFC1123(testdate[id]));
            testhdr2 = new ResponseHeader(200);
            testhdr2.put(HeaderFramework.LAST_MODIFIED, HeaderFramework.formatRFC1123(testdate[id]));
            testhdr3 = new ResponseHeader(200);
            testhdr3.put(HeaderFramework.LAST_MODIFIED, HeaderFramework.formatRFC1123(testdate[id]));

            age1 = testhdr1.age();
            testhdr2.date();
            testhdr3.lastModified();

            assertTrue("access-sequence 1."+id+" date=null lastmod=" + testdatename[id] + " AGE= " + age1, age1 >= 0);

            age2 = testhdr2.age();
            assertTrue("access-sequence 2."+id+" date=null lastmod=" + testdatename[id] + " AGE= " + age2, age2 >= 0);

            age3 = testhdr3.age();
            assertTrue("access-sequence 3."+id+" date=null lastmod=" + testdatename[id] + " AGE= " + age3, age3 >= 0);

            for (int imd = 0; imd < testdate.length; imd++) {
                // test case with sorce: date=testdate(x) lastmodified=testdate(y)
                testhdr1 = new ResponseHeader(200);
                testhdr1.put(HeaderFramework.DATE, HeaderFramework.formatRFC1123(testdate[id]));
                testhdr1.put(HeaderFramework.LAST_MODIFIED, HeaderFramework.formatRFC1123(testdate[imd]));

                testhdr2 = new ResponseHeader(200);
                testhdr2.put(HeaderFramework.DATE, HeaderFramework.formatRFC1123(testdate[id]));
                testhdr2.put(HeaderFramework.LAST_MODIFIED, HeaderFramework.formatRFC1123(testdate[imd]));

                testhdr3 = new ResponseHeader(200);
                testhdr3.put(HeaderFramework.DATE, HeaderFramework.formatRFC1123(testdate[id]));
                testhdr3.put(HeaderFramework.LAST_MODIFIED, HeaderFramework.formatRFC1123(testdate[imd]));

                age1 = testhdr1.age();

                testhdr2.date();
                testhdr3.lastModified();

                assertTrue("case 1."+id+"."+imd+" date=" + testdatename[id] + " lastmod=" + testdatename[imd] + " AGE= " + age1, age1 >= 0);

                // hint to mismatch of date
                Date d1 = testhdr1.date();
                Date m1 = testhdr1.lastModified();
                if (d1.before(m1)) {
                    System.err.println("this faulty combination with lastmod after date (date="+testdatename[id]+" lastmod="+testdatename[imd]+") is accepted without correction");
                }

                age2 = testhdr2.age();
                assertTrue("case 2."+id+"."+imd+" date=" + testdatename[id] + " lastmod=" + testdatename[imd] + " AGE= " + age2, age2 >= 0);

                age3 = testhdr3.age();
                assertTrue("case 3."+id+"."+imd+" date=" + testdatename[id] + " lastmod=" + testdatename[imd] + " AGE= " + age3, age3 >= 0);
            }

        }

    }

}
