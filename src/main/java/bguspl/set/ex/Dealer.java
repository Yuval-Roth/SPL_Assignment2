package bguspl.set.ex;
import bguspl.set.Env;
import java.util.Collections;
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
    final Env env;

    /**
     * Game entities.
     */
    private final Table table;

    /**
     * Game Players
     */
    private final Player[] players;

    /**
     * The list of card ids that are left in the dealer's deck.
     */
    private final List<Integer> deck;

    /**
     * a linked list that holds a history of claimSet() actions
     * like a stack. FIFO order on the objects inside.
     * 
     * @Note The cards inside each Integer[] are sorted with Collections.sort()
     */
    private LinkedList<Integer[]> claimStack;

    /**
     * True if game should be terminated due to an external event.
     */
    private volatile boolean terminate;

    /**
     * The time when the dealer needs to reshuffle the deck due to turn timeout.
     */
    volatile private long reshuffleTime;

    /**
     * TODO fill this doc
     */
    volatile private long elapsedTime;

    /**
     * Holds all of the player threads
     */
    private Thread[] playerThreads;

    /**
     * Reshuffle timer thread
     */
    private Thread timer;

    /**
     * Object for breaking wait() when execution should resume
     */
    private volatile Object executionListener;

    /**
     * Indicates whether the reshuffle timer should stop running
     */
    private volatile Boolean stopTimer = false;

    /**
     * a version indicator for claimSet() actions
     * resets each shuffle
     * 
     * @inv gameVersion >= 0
     */
    private int gameVersion;
    
    /**
     * indicates how many cards in a valid set
     */
    private static final int SET_SIZE = 3;
    
    
    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
        playerThreads = new Thread[players.length];
        executionListener = new Object();
    }
    
    //===========================================================
    //                      Threads
    //===========================================================

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        System.out.printf("Info: Thread %s starting.%n", Thread.currentThread().getName());
        createPlayerThreads();
        elapsedTime = System.currentTimeMillis();
        shuffleDeck();
        while (!shouldFinish()) {        
            timerLoop();
        }   
        if(env.util.findSets(deck, 1).size() == 0) announceWinners();
        System.out.printf("Info: Thread %s terminated.%n", Thread.currentThread().getName());
    }

    private void startTimer() {
        timer = new Thread(()-> {        
            stopTimer = false;
            updateTimerDisplay(true);
            while(stopTimer == false & reshuffleTime > System.currentTimeMillis()){
                updateTimerDisplay(false);
                if(reshuffleTime-System.currentTimeMillis() <= env.config.turnTimeoutWarningMillis)
                    try{Thread.sleep(10);} catch (InterruptedException ignored){}
                else  try{Thread.sleep(1000);} catch (InterruptedException ignored){}
            }
            env.ui.setCountdown(0,true);   
            synchronized(executionListener){
                executionListener.notifyAll();
            }
        },"Reshuffle timer");     
        timer.start();
    }


    //===========================================================
    //                      Main methods
    //===========================================================

    /**
     * The inner loop of the dealer thread that runs as long as the countdown did not time out.
     */
    private void timerLoop() {
        reshuffleTime = System.currentTimeMillis() + env.config.turnTimeoutMillis;
        gameVersion = 0;
        claimStack = new LinkedList<>();
        while (!terminate && System.currentTimeMillis() < reshuffleTime) {
            placeCardsOnTable();
            resumePlayerThreads();
            sleepUntilWokenOrTimeout();
            pausePlayerThreads();      
            removeAllCardsFromTable();
            shuffleDeck();
        }
    }

    /**
     * Claims a set. checks if the set is a legal set and awards or penalizes a player
     * @param cards - The cards forming the set
     * @param claimer - The player who claims the set
     * @param claimVersion - The gameVersion according to getGameVersion()
     */
    public boolean claimSet(List<Integer> cards, Player claimer,int claimVersion){

        boolean correct = false;
        if(claimVersion == gameVersion){
            if (isValidSet(cards)){
                synchronized(this){   
                    //the claim matches the game version, pretty straight forward procedure from here
                    if(gameVersion == claimVersion){
                        handleClaimedSet(cards, claimer);
                        gameVersion++;
                        pushClaimToStack(cards, claimVersion);
                        correct = true;
                    }
                }
            }    
        }
        else return false;       

        clearClaimFromUI(cards, claimer);
        if(correct) claimer.point();
        else claimer.penalty();
        return true;



        // if (isValidSet(cards)){
        //     synchronized(this){
            
        //         //the claim matches the game version, pretty straight forward procedure from here
        //         if(gameVersion == claimVersion){
        //             handleClaimedSet(cards, claimer);
        //             gameVersion++;
        //             pushClaimToStack(cards, claimVersion);
        //             correct = true;
        //         }
    
        //         //Decide what to do if received a claim from an older gameVersion
        //         else{
    
        //             Integer[] claim = convertCardsListToClaim(cards, claimVersion);
        //             ListIterator<Integer[]> iter = claimStack.listIterator();
    
    
        //             while(iter.hasNext()){
        //                 Integer[] next = iter.next();
    
        //                 //find the first claim that has the same version
        //                 if(next[next.length-1] > claimVersion) continue;
                        
        //                 else {
    
        //                     //found a claim from the same gameVersion
        //                     if(next[next.length-1] == claimVersion){
    
        //                         //check if the claim is identical
        //                         if(isIdenticalClaim(next, claim)){
        //                             break; //found an identical claim, continue the game without penalizing the claimer
        //                         }
        //                         else continue; // keep looking for identical claims
        //                     }
    
        //                     //at this point, we've went through all the claims with the same claimVersion 
        //                     // and decided that they are not indentical claims, thus this is a new legit claim
        //                     // from an older gameVersion
        //                     else if(next[next.length-1] < claimVersion){
        //                         iter.add(claim);
        //                         handleClaimedSet(cards, claimer);
        //                         correct = true;
        //                     };
        //                 }
        //             }
        //         }
        //     }
        // }       
        // clearClaimFromUI(cards, claimer);
        // if(correct) claimer.point();
        // else claimer.penalty();
    }

    /**
     * Stops the reshuffle timer
     */
    private void stopTimer() {
        try{
            stopTimer = true;
            timer.join();
        } catch (InterruptedException ignored){}
    }

    /**
     * current reshuffle game version
     * @return
     */
    public int getGameVersion() {
        return gameVersion;
    }

    /**
     * Called when the game should be terminated due to an external event.
     */
    public void terminate() {
        terminate = true;
    }

    //===========================================================
    //                  utility methods
    //===========================================================

    /**
    * Checks if the given set of cards is a valid set.
    */
    private boolean isValidSet(List<Integer> cards) {
        synchronized(cards){
            int[] _cards = cards.stream().mapToInt(i -> i).toArray();
            return env.util.testSet(_cards);
        }
    }

    /**
     * clears the claim from the UI
     * @param cards - the cards in the claim
     * @param claimer - the player who claimed the set
     */
    private void clearClaimFromUI(List<Integer> cards, Player claimer) {
        for (int token : cards){
            env.ui.removeToken(claimer.id, token);
        }
    }

    /**
     * Starts the player threads 
     * @note This instantiates new player threads and calls start()
     * on the threads
     */
    private void resumePlayerThreads() {
        for(Player player : players){
            player.resume();
        }
    }

    /**
     * Instantiates and starts all the player threads
     */
    private void createPlayerThreads() {
        for(int i = 0; i< playerThreads.length; i++)
        {
            String name = "Player "+players[i].id +", "+(players[i].human ? "Human":"AI");
            playerThreads[i] = new Thread(players[i],name);
            playerThreads[i].start();
        }
    }
    
    /**
     * Terminates all the player threads
     */
    private void pausePlayerThreads() {
        for(Player player : players){
            player.pause();
        }
    }

    /**
     * Check if the game should be terminated or the game end conditions are met.
     *
     * @return true iff the game should be finished.
     */
    private boolean shouldFinish() {
        return terminate || allSetsDepleted();
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
        int countToPlace = table.getEmptySlotCount();
        for (int i = 0; i < countToPlace; i++) {
            if (deck.size() > 0) {
                placeNextCardOnTable();
            }
            else {
                break; // Think about this
            }
        }
    }
    
    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some purpose.
     */
    private void sleepUntilWokenOrTimeout() {
        
        if(reshuffleTime-System.currentTimeMillis() > 0){
            try{
                startTimer();
                synchronized(executionListener){executionListener.wait();}
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
     * handles a claim that was verified as a true set
     * 
     * @param cards
     * @param claimer
     */
    private void handleClaimedSet(List<Integer> cards, Player claimer) {
        removeClaimedCards(cards, claimer);
        placeCardsOnTable();
        updateTimerDisplay(true);
    }

    /*
    * Why does this not have a javadoc?
    */
    private void removeClaimedCards(List<Integer> cards, Player claimer) {
        for(int card : cards){ // remove cards from table
            // deck.remove(card); do not remove from deck, card should already be out of the deck
            table.removeCard(card);
        }  
        
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
    * Removes one card from the deck and places it on the table.
    */
    private void placeNextCardOnTable(){
        Integer cardToPlace = deck.get(0);
        deck.remove(0);
        table.placeCard(cardToPlace);
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
    
    /**
     * Checks if there are any set combinations left in the deck or on the table.
     * @return true iff there are no possible sets.
     */
    private boolean allSetsDepleted() {

        return env.util.findSets(deck, 1).size() == 0 && table.getSetCount()==0;
    }
    
}
