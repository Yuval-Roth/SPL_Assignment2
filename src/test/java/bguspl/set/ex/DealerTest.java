package bguspl.set.ex;

import bguspl.set.Config;
import bguspl.set.Env;
import bguspl.set.UserInterface;
import bguspl.set.Util;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import java.util.List;
import java.util.Properties;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DealerTest {


    Dealer dealer;
    @Mock
    Table table;
    @Mock
    Env env;
    @Mock
    Player player;
    @Mock Logger logger;

    @Mock
    UserInterface ui;

    @Mock
    Util util;

    @BeforeEach
    void setUp() {
        Logger logger = new MockLogger();
        ui = new UserInterfaceImpl(logger);
        Env env = new Env(logger, new Config(logger, "config.properties"), ui, util);
        table = new Table(env);
        player = new Player(env, dealer, table, 0, true);
        Player[] players = {player};
        dealer = new Dealer(env, table, players);

    }


    @AfterEach
    void tearDown() {
    }

    @Test
    void terminateTest() {
        dealer.terminate();
        assertEquals(true, dealer.terminate);
    }

    @Test
    void isValidSetCorrect() {
        table.placeCard(1, 1);
        table.placeCard(2, 2);
        table.placeCard(3, 3);
        assertEquals(true, dealer.isValidSet(new Integer[]{1, 2, 3}));

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
