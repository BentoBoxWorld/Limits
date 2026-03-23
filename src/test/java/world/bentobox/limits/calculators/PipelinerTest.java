package world.bentobox.limits.calculators;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import world.bentobox.bentobox.BentoBox;
import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.limits.Limits;
import world.bentobox.limits.calculators.Results.Result;
import org.mockbukkit.mockbukkit.MockBukkit;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class PipelinerTest {

    @Mock
    private Limits addon;
    @Mock
    private BentoBox plugin;
    @Mock
    private Island island;

    private MockedStatic<BentoBox> mockedBentoBox;
    private Pipeliner pipeliner;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();

        mockedBentoBox = Mockito.mockStatic(BentoBox.class);
        mockedBentoBox.when(BentoBox::getInstance).thenReturn(plugin);

        pipeliner = new Pipeliner(addon);
    }

    @AfterEach
    void tearDown() {
        mockedBentoBox.close();
        MockBukkit.unmock();
    }

    @Test
    void testGetIslandsInQueueInitiallyZero() {
        assertEquals(0, pipeliner.getIslandsInQueue());
    }

    @Test
    void testAddIslandReturnsCompletableFuture() {
        when(island.isDeleted()).thenReturn(false);
        when(island.isUnowned()).thenReturn(false);

        try (MockedConstruction<RecountCalculator> mockedCalc = Mockito.mockConstruction(RecountCalculator.class,
                (mock, context) -> {
                    when(mock.getIsland()).thenReturn(island);
                })) {
            CompletableFuture<Results> future = pipeliner.addIsland(island);
            assertNotNull(future);
            assertFalse(future.isDone());
        }
    }

    @Test
    void testAddIslandDuplicateReturnsInProgress() throws ExecutionException, InterruptedException {
        when(island.isDeleted()).thenReturn(false);
        when(island.isUnowned()).thenReturn(false);

        try (MockedConstruction<RecountCalculator> mockedCalc = Mockito.mockConstruction(RecountCalculator.class,
                (mock, context) -> {
                    when(mock.getIsland()).thenReturn(island);
                })) {
            pipeliner.addIsland(island);
            CompletableFuture<Results> second = pipeliner.addIsland(island);
            assertTrue(second.isDone());
            assertEquals(Result.IN_PROGRESS, second.get().getState());
        }
    }

    @Test
    void testGetTimeReturnsStartDurationWhenNoCounts() {
        assertEquals(10, pipeliner.getTime());
    }

    @Test
    void testSetTimeThenGetTimeUpdatesAverage() {
        // Add an island to increment count to 1
        when(island.isDeleted()).thenReturn(false);
        when(island.isUnowned()).thenReturn(false);

        try (MockedConstruction<RecountCalculator> mockedCalc = Mockito.mockConstruction(RecountCalculator.class,
                (mock, context) -> {
                    when(mock.getIsland()).thenReturn(island);
                })) {
            pipeliner.addIsland(island);
        }
        // Now count is 1. Set time to 5000ms -> average = 5000 / 1 / 1000 = 5
        pipeliner.setTime(5000);
        assertEquals(5, pipeliner.getTime());
    }

    @Test
    void testStopClearsQueuesAndCancelsTask() {
        pipeliner.stop();

        assertEquals(0, pipeliner.getIslandsInQueue());
    }
}
