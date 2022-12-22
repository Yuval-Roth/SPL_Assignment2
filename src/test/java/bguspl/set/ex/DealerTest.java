package bguspl.set.ex;

import bguspl.set.*;
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
    @Mock
    Config config;

    @BeforeEach
    void setUp() {
        Logger logger = new MockLogger();
        UserInterface ui = new MockUserInterface();
        Config config = new Config(logger, "");
        util = new UtilImpl(config);
        Env env = new Env(logger, config, ui, util);
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
        Thread dealThread = new Thread(dealer,"Dealer");
        dealThread.start();
        try{Thread.sleep(1000);}catch(InterruptedException ignored){}

        dealer.terminate();
        try{Thread.sleep(1000);}catch(InterruptedException ignored){}

        assertEquals(Thread.State.TERMINATED, dealThread.getState());
    }

    @Test
    void isValidSetCorrect() {
        table.placeCard(1, 1);
        table.placeCard(2, 2);
        table.placeCard(3, 3);
        assertEquals(false, dealer.isValidSet(new Integer[]{1, 2, 3}));

        table.placeCard(0, 4);
        table.placeCard(1, 5);
        table.placeCard(2, 6);
        assertEquals(true, dealer.isValidSet(new Integer[]{4, 5, 6}));


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
