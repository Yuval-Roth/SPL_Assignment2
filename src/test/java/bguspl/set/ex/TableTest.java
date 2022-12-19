package bguspl.set.ex;

import bguspl.set.Config;
import bguspl.set.Env;
import bguspl.set.UserInterface;
import bguspl.set.Util;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import java.util.List;
import java.util.Properties;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TableTest {

    Table table;
    @Mock
    Dealer dealer;
    private Integer[] slotToCard;
    private Integer[] cardToSlot;

    @BeforeEach
    void setUp() {

        Properties properties = new Properties();
        properties.put("Rows", "2");
        properties.put("Columns", "2");
        properties.put("FeatureSize", "3");
        properties.put("FeatureCount", "4");
        properties.put("TableDelaySeconds", "0");
        properties.put("PlayerKeys1", "81,87,69,82");
        properties.put("PlayerKeys2", "85,73,79,80");
        MockLogger logger = new MockLogger();
        Config config = new Config(logger, properties);
        slotToCard = new Integer[config.tableSize];
        cardToSlot = new Integer[config.deckSize];
        Env env = new Env(logger, config, new MockUserInterface(), new MockUtil());
        table = new Table(env, slotToCard, cardToSlot);
    }

    private int fillSomeSlots() {
        table.placeCard(3, 1);
        table.placeCard(5, 3);
        return 2;
    }

    private void fillAllSlots() {
        for (int i = 0; i < slotToCard.length; i++) {
            table.placeCard(i, i);
        }
    }

    private void placeSomeCardsAndAssert() throws InterruptedException {
        table.placeCard(8, 2);

        assertEquals(8, (int) slotToCard[2]);
        assertEquals(2, (int) cardToSlot[8]);
    }

    @Test
    void getEmptySlotCountTest() {
        // Test an empty table
        table.clearTable();
        assertEquals(slotToCard.length, table.getEmptySlotCount());

        // Test a table with some slots filled
        int slotsFilled = fillSomeSlots();
        assertEquals(slotToCard.length - slotsFilled, table.getEmptySlotCount());

        table.clearTable();
        // Test a table with all slots filled
        fillAllSlots();
        assertEquals(0, table.getEmptySlotCount());
    }

    @Test
    void getCurrentSizeTest() {
        // Test an empty table
        table.clearTable();
        assertEquals(0, table.getCurrentSize());

        // Test a table with some slots filled
        int slotsFilled = fillSomeSlots();
        assertEquals(slotsFilled, table.getCurrentSize());

        table.clearTable();
        // Test a table with all slots filled
        fillAllSlots();
        assertEquals(slotToCard.length, table.getCurrentSize());
    }

    @Test
    void countCards_AllSlotsAreFilled() {
        fillAllSlots();
        assertEquals(slotToCard.length, table.getCurrentSize());
    }

    @Test
    void placeCard_SomeSlotsAreFilled() throws InterruptedException {
        fillSomeSlots();
        placeSomeCardsAndAssert();
    }

    @Test
    void placeCard_AllSlotsAreFilled() throws InterruptedException {
        fillAllSlots();
        placeSomeCardsAndAssert();
    }

    // Test placeCard()
    @Test
    void placeCard_SlotIsAlreadyFilled() throws InterruptedException {
        fillSomeSlots();
        table.placeCard(9, 2);
        table.placeCard(8, 2);
        assertEquals(8, (int) slotToCard[2]);
        assertEquals(2, (int) cardToSlot[8]);
    }

    // Test table.removeCard()
    @Test
    void removeCardTest() throws InterruptedException {
        table.placeCard(77, 2);
        table.removeCard(2);
        assertEquals(null, slotToCard[2]);
        assertEquals(null, cardToSlot[77]);
    }



    static class MockUserInterface implements UserInterface {
        @Override
        public void dispose() {}
        @Override
        public void placeCard(int card, int slot) {}
        @Override
        public void removeCard(int slot) {}
        @Override
        public void setCountdown(long millies, boolean warn) {}
        @Override
        public void setElapsed(long millies) {}
        @Override
        public void setScore(int player, int score) {}
        @Override
        public void setFreeze(int player, long millies) {}
        @Override
        public void placeToken(int player, int slot) {}
        @Override
        public void removeTokens() {}
        @Override
        public void removeTokens(int slot) {}
        @Override
        public void removeToken(int player, int slot) {}
        @Override
        public void announceWinner(int[] players) {}
    };

    static class MockUtil implements Util {
        @Override
        public int[] cardToFeatures(int card) {
            return new int[0];
        }

        @Override
        public int[][] cardsToFeatures(int[] cards) {
            return new int[0][];
        }

        @Override
        public boolean testSet(int[] cards) {
            return false;
        }

        @Override
        public List<int[]> findSets(List<Integer> deck, int count) {
            return null;
        }

        @Override
        public void spin() {}
    }

    static class MockLogger extends Logger {
        protected MockLogger() {
            super("", null);
        }
    }
}
