package world.bentobox.limits.calculators;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import world.bentobox.limits.calculators.Results.Result;

public class ResultsTest {

    @Test
    public void testDefaultConstructorStateIsAvailable() {
        Results results = new Results();
        assertEquals(Result.AVAILABLE, results.getState());
    }

    @Test
    public void testConstructorWithState() {
        Results results = new Results(Result.IN_PROGRESS);
        assertEquals(Result.IN_PROGRESS, results.getState());
    }

    @Test
    public void testGetMdCountReturnsEmptyMultiset() {
        Results results = new Results();
        assertNotNull(results.getMdCount());
        assertTrue(results.getMdCount().isEmpty());
    }

    @Test
    public void testGetEntityCountReturnsEmptyMultiset() {
        Results results = new Results();
        assertNotNull(results.getEntityCount());
        assertTrue(results.getEntityCount().isEmpty());
    }

    @Test
    public void testResultEnumValues() {
        Result[] values = Result.values();
        assertEquals(3, values.length);
        assertNotNull(Result.valueOf("AVAILABLE"));
        assertNotNull(Result.valueOf("IN_PROGRESS"));
        assertNotNull(Result.valueOf("TIMEOUT"));
    }
}
