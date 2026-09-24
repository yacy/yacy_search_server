package net.yacy.search.query;

import static org.junit.Assert.assertNotNull;

import org.junit.Test;

public class QueryGoalTest {

    @Test
    public void parsesConsecutiveSeparators() {
        assertNotNull(new QueryGoal("fe80::1"));
        assertNotNull(new QueryGoal("::1"));
        assertNotNull(new QueryGoal("2001:db8::1"));
    }
}
