package world.bentobox.limits.calculators;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.World.Environment;
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
    void testGetBlockCountReturnsEmptyMultiset() {
        Results results = new Results();
        assertNotNull(results.getBlockCount(Environment.NORMAL));
        assertTrue(results.getBlockCount(Environment.NORMAL).isEmpty());
    }

    @Test
    public void testGetEntityCountReturnsEmptyMultiset() {
        Results results = new Results();
        assertNotNull(results.getEntityCount(Environment.NORMAL));
        assertTrue(results.getEntityCount(Environment.NORMAL).isEmpty());
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
