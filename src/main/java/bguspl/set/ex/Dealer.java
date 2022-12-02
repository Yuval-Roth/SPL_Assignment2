package bguspl.set.ex;

import bguspl.set.Env;

import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * This class manages the dealer's threads and data
 */
public class Dealer implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;
    private final Player[] players;

    /**
     * The list of card ids that are left in the dealer's deck.
     */
    private final List<Integer> deck;

    /**
     * True iff game should be terminated due to an external event.
     */
    private volatile boolean terminate;

    /**
     * The time when the dealer needs to reshuffle the deck due to turn timeout.
     */
    private long reshuffleTime = Long.MAX_VALUE;
    private long elapsedTime;
    private Thread[] playerThreads;

    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
        playerThreads = new Thread[players.length];
        for(int i = 0; i< playerThreads.length; i++)
        {
            playerThreads[i] = new Thread(players[i],
                                "Player "+players[i].id +", "+(players[i].human ? "Human":"AI"));
        }
    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        System.out.printf("Info: Thread %s starting.%n", Thread.currentThread().getName());
        elapsedTime = System.currentTimeMillis();
        while (!shouldFinish()) {
            placeCardsOnTable();
            updateTimerDisplay(true);
            timerLoop();
            removeAllCardsFromTable();
        }
        
        
        announceWinners();
        System.out.printf("Info: Thread %s terminated.%n", Thread.currentThread().getName());
    }

    /**
     * The inner loop of the dealer thread that runs as long as the countdown did not time out.
     */
    private void timerLoop() {
        
        while (!terminate && System.currentTimeMillis() < reshuffleTime) {
            startPlayerThreads();
            sleepUntilWokenOrTimeout();
            // this will need to be turned into a thread that continously changes the timer in the future
            updateTimerDisplay(true); 
            // -------------------------------
            stopPlayerThreads();
            removeCardsFromTable();
            shuffleDeck();
            placeCardsOnTable();
        }
    }

    private void startPlayerThreads() {
        for(Thread thread : playerThreads)
        {
            thread.start();
        }
    }

    private void stopPlayerThreads() {
        for(Player player: players)
        {
            player.terminate();
        }
        for(Thread thread : playerThreads){
            try { thread.join(); } catch (InterruptedException ignored) {}
        }
    }

    /**
     * Called when the game should be terminated due to an external event.
     */
    public void terminate() {
        // TODO implement
    }

    /**
     * Check if the game should be terminated or the game end conditions are met.
     *
     * @return true iff the game should be finished.
     */
    private boolean shouldFinish() {
        return terminate || env.util.findSets(deck, 1).size() == 0;
    }

    /**
     * Checks if any cards should be removed from the table and returns them to the deck.
     */
    private void removeCardsFromTable() {
        table.clearTable();
    }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable() {
        // for card in deck table.placeCard()
        int countToPlace = env.config.tableSize;
        Iterator<Integer> iter = deck.iterator();
        int index = 0;
        while(index < countToPlace && iter.hasNext())
        {
            table.placeCard(iter.next(), index++);
        }
    }

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some purpose.
     */
    private void sleepUntilWokenOrTimeout() {
        
        if(reshuffleTime-System.currentTimeMillis() > 0){
            try{
                Thread.sleep(reshuffleTime-System.currentTimeMillis());
            }
            catch(InterruptedException ignored){}
        }
    }

    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset) {
        if(reset) reshuffleTime = System.currentTimeMillis() + env.config.turnTimeoutMillis;   
        env.ui.setCountdown(reshuffleTime-System.currentTimeMillis(),
        reshuffleTime-System.currentTimeMillis() <= env.config.turnTimeoutWarningMillis);   
    }

    private void updateElapsedTimeDisplay(boolean reset){

        if(reset) elapsedTime = System.currentTimeMillis();   
        env.ui.setElapsed(System.currentTimeMillis() - elapsedTime);
    }

    /**
     * Returns all the cards from the table to the deck.
     */
    private void removeAllCardsFromTable() {
        table.clearTable();
    }

    /**
     * Check who is/are the winner/s and send them to the UI.
     */
    private void announceWinners() {
        int maxScore = 0;
        LinkedList<Player> winners = new LinkedList<>();
        for(Player player : players)
        {
            if (player.getScore() >= maxScore)
            {
                maxScore = player.getScore();
            }
        }
        for(Player player : players)
        {
            if (player.getScore() == maxScore)
            {
                winners.add(player);
            }
        }
        // convert winners to int[], send to UI
        int[] winnerIds = new int[winners.size()];
        for(int i = 0; i < winnerIds.length; i++)
        {
            winnerIds[i] = winners.get(i).id;
        }
        env.ui.announceWinner(winnerIds);
    }

    public synchronized void claimSet(Deque<Integer> cards, Player claimer){
        if (isValidSet(cards)){
            claimer.point();
            for(int card : cards){ // remove cards from table
                deck.remove(card);
                table.removeCard(card);
            }
        }
        else claimer.penalty();
    }

    /*
     * Shuffles the deck
     */
    private void shuffleDeck() {
        Collections.shuffle(deck);
    }
     
    /*
     * Checks if the given set of cards is a valid set.
     */
    private boolean isValidSet(Deque<Integer> cards) {
        int[] _cards = cards.stream().mapToInt(i -> i).toArray();
        return env.util.testSet(_cards);
    }
}
