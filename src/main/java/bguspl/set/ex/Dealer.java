package bguspl.set.ex;

import bguspl.set.Env;

import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
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
    private long reshuffleTime;
    private long elapsedTime;
    private Thread[] playerThreads;
    private Thread timer;
    private boolean stopTimer;

    private int gameVersion;
    private LinkedList<Integer[]> claimStack;

    private static final int SET_SIZE = 3;

    private Thread dealerThread;

    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
        playerThreads = new Thread[players.length];
    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        System.out.printf("Info: Thread %s starting.%n", Thread.currentThread().getName());
        dealerThread = Thread.currentThread();
        elapsedTime = System.currentTimeMillis();
        shuffleDeck();
        while (!shouldFinish()) {        
            timerLoop();
        }
        // stopTimer();
        stopPlayerThreads();    
        if(env.util.findSets(deck, 1).size() == 0) announceWinners();
        System.out.printf("Info: Thread %s terminated.%n", Thread.currentThread().getName());
    }

    /**
     * The inner loop of the dealer thread that runs as long as the countdown did not time out.
     */
    private void timerLoop() {
        reshuffleTime = System.currentTimeMillis() + env.config.turnTimeoutMillis;
        gameVersion = 0;
        claimStack = new LinkedList<>();
        while (!terminate && System.currentTimeMillis() < reshuffleTime) {
            placeCardsOnTable();
            startPlayerThreads();
            startTimer();
            sleepUntilWokenOrTimeout();
            stopPlayerThreads();      
            removeAllCardsFromTable();
            shuffleDeck();
        }
    }

    private void startTimer() {
        timer = new Thread(()-> {        
            stopTimer = false;
            while(stopTimer == false & reshuffleTime > System.currentTimeMillis()){
                updateTimerDisplay(false);
                if(reshuffleTime-System.currentTimeMillis() <= env.config.turnTimeoutWarningMillis)
                    try{Thread.sleep(10);} catch (InterruptedException ignored){}
                else  try{Thread.sleep(1000);} catch (InterruptedException ignored){}
            }
        });     
        timer.setPriority(Thread.MAX_PRIORITY);
        timer.start();
    }

    /*
     * Why does this not have a javadoc?
     */
    private void stopTimer() {
        try{
            stopTimer = true;
            timer.join();
        } catch (InterruptedException ignored){}
    }

    /*
     * Why does this not have a javadoc?
     */
    private void startPlayerThreads() {
        for(int i = 0; i< playerThreads.length; i++)
        {
            String name = "Player "+players[i].id +", "+(players[i].human ? "Human":"AI");
            playerThreads[i] = new Thread(players[i],name);
            playerThreads[i].start();
        }
    }

    /*
     * Why does this not have a javadoc?
     */
    private void stopPlayerThreads() {
        for(Player player: players)
        {
            player.terminate();
        }
        for(Thread thread : playerThreads){
            try {
                thread.interrupt();
                thread.join(); 
            } catch (InterruptedException ignored) {}
        }
    }

    /**
     * Called when the game should be terminated due to an external event.
     */
    public void terminate() {
        terminate = true;
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
     * Removes all cards from the table and returns them to the deck.
     */
    private void removeAllCardsFromTable() {
        Integer[] cardsRemoved = table.clearTable();
        for (Integer card: cardsRemoved) {
            deck.add(card);
        }
    }

    /**
     * Checks how many empty slots are on the table and 
     * places cards on the table for each empty slot.
     */
    private void placeCardsOnTable() {
        int countToPlace = getEmptySlotCount();
        for (int i = 0; i < countToPlace; i++) {
            if (deck.size() > 0) {
                placeNextCardOnTable();
            }
            else {
                break; // Think about this
            }
        }
    }

    private int getEmptySlotCount() {
        int countToPlace = env.config.tableSize - table.getCurrentSize();
        return countToPlace;
    }

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some purpose.
     */
    private void sleepUntilWokenOrTimeout() {
        
        if(reshuffleTime-System.currentTimeMillis() > 0){
            try{
                Thread.sleep(reshuffleTime-System.currentTimeMillis());
            }
            catch(InterruptedException e){
                stopTimer();
            }
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

    /*
     * Why does this not have a javadoc?
     */
    private void updateElapsedTimeDisplay(boolean reset){

        if(reset) elapsedTime = System.currentTimeMillis();   
        env.ui.setElapsed(System.currentTimeMillis() - elapsedTime);
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

    /*
     * Why does this not have a javadoc?
     */
    public synchronized void claimSet(List<Integer> cards, Player claimer,int claimVersion){
        

        if (isValidSet(cards)){

            //the claim matches the game version, pretty straight forward procedure from here
            if(gameVersion == claimVersion){
                handleClaimedSet(cards, claimer);
                gameVersion++;
                pushClaimToStack(cards, claimVersion);
            }

            //Decide what to do if received a claim from an older gameVersion
            else{

                Integer[] claim = convertCardsListToClaim(cards, claimVersion);
                ListIterator<Integer[]> iter = claimStack.listIterator();


                while(iter.hasNext()){
                    Integer[] next = iter.next();

                    //find the first claim that has the same version
                    if(next[next.length-1] > claimVersion) continue;
                    
                    else {

                        //found a claim from the same gameVersion
                        if(next[next.length-1] == claimVersion){

                            //check if the claim is identical
                            if(isIdenticalClaim(next, claim)){
                                break; //found an identical claim, continue the game without penalizing the claimer
                            }
                            else continue; // keep looking for identical claims
                        }

                        //at this point, we've went through all the claims with the same claimVersion 
                        // and decided that they are not indentical claims, thus this is a new legit claim
                        // from an older gameVersion
                        else if(next[next.length-1] < claimVersion){
                            iter.add(claim);
                            handleClaimedSet(cards, claimer);
                        };
                    }
                }
            }
        }
        else claimer.penalty();       
    }

    private void handleClaimedSet(List<Integer> cards, Player claimer) {
        removeClaimedCards(cards, claimer);
        claimer.point();
        if(dealerThread.getState() == Thread.State.TIMED_WAITING) dealerThread.interrupt();
    }

    /*
     * Why does this not have a javadoc?
     */
    private void removeClaimedCards(List<Integer> cards, Player claimer) {
        for(int card : cards){ // remove cards from table
            // deck.remove(card); do not remove from deck, card should already be out of the deck
            table.removeCard(card);
        }  
        placeCardsOnTable();
    }

    /*
     * Why does this not have a javadoc?
     */
    private boolean isIdenticalClaim( Integer[] claim1,Integer[] claim2){
        
        if(claim1.length != claim2.length) return false;

        for (int i = 0; i< claim1.length; i ++){
            if(claim1[i] != claim2[i]) return false;
        }

        return true;
    }

    /*
     * Why does this not have a javadoc?
     */
    private void pushClaimToStack(List<Integer> cards, int claimVersion) {
        Integer[] claim = convertCardsListToClaim(cards, claimVersion);
        claimStack.push(claim);
    }

    /*
     * Why does this not have a javadoc?
     */
    private Integer[] convertCardsListToClaim(List<Integer> cards, int claimVersion) {
        Integer[] claim = new Integer[SET_SIZE+1];
        Collections.sort(cards);
        int i = 0;
        for(Integer card : cards){
            claim[i++] = card;
        }
        claim[claim.length-1] = claimVersion;
        return claim;
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
    private boolean isValidSet(List<Integer> cards) {
        synchronized(cards){
            int[] _cards = cards.stream().mapToInt(i -> i).toArray();
            return env.util.testSet(_cards);
        }
    }

    /*
     * Removes one card from the deck and places it on the table.
     */
    private void placeNextCardOnTable(){
        Integer cardToPlace = deck.get(0);
        deck.remove(0);
        table.placeCard(cardToPlace);
    }

    public int getGameVersion() {
        return gameVersion;
    }

    
}
