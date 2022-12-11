package bguspl.set.ex;
import bguspl.set.Env;
import bguspl.set.Main;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * This class manages the dealer's threads and data
 */
public class Dealer implements Runnable {

    /**
     *
     */
    private static final int timerUpdateCriticalTickTime = 25;

    /**
     *
     */
    private static final int timerUpdateTickTime = 250;

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
     * True if game should be terminated due to an external event.
     */
    private volatile boolean terminate;

    /**
     * The time when the dealer needs to reshuffle the deck due to turn timeout.
     */
    volatile private long reshuffleTime;

    /**
     * The time when the dealer needs to reshuffle the deck due to turn timeout.
     */
    volatile private long nextWakeTime;

    /**
     * TODO fill this doc
     */
    volatile private long elapsedTime;

    /**
     * Holds all of the player threads
     */
    private Thread[] playerThreads;

    /**
     * a version indicator for claimSet() actions
     * resets each shuffle
     * 
     * @inv gameVersion >= 0
     */
    private volatile Integer gameVersion;

    private volatile Object wakeListener;

    private volatile ConcurrentLinkedQueue<Claim> claimQueue;
       
    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
        playerThreads = new Thread[players.length];
        wakeListener = new Object();
        claimQueue = new ConcurrentLinkedQueue<>();
    }
    
    
    //===========================================================
    //                      Main methods
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
        terminatePlayers();
        if(env.util.findSets(deck, 1).size() == 0) announceWinners();
        System.out.printf("Info: Thread %s terminated.%n", Thread.currentThread().getName());
    }

    private void startTimer() {     
        updateTimerDisplay(true);
        while(terminate == false & reshuffleTime > System.currentTimeMillis()){
            nextWakeTime =  reshuffleTime-System.currentTimeMillis() > env.config.turnTimeoutWarningMillis ?
                                System.currentTimeMillis()+timerUpdateTickTime : System.currentTimeMillis()+timerUpdateCriticalTickTime; 
            while(terminate == false & reshuffleTime > System.currentTimeMillis() & nextWakeTime > System.currentTimeMillis()){
                updateTimerDisplay(false);
                sleepUntilWokenOrTimeout();
                while(claimQueue.isEmpty() == false){
                    Claim claim = claimQueue.remove();
                    handleClaimedSet(claim);
                    updateTimerDisplay(false);
                }
            } 
                
        }
        if(terminate == false) env.ui.setCountdown(0,true);   
    }

    /**
     * The inner loop of the dealer thread that runs as long as the countdown did not time out.
     */
    private void timerLoop() {
        reshuffleTime = System.currentTimeMillis() + env.config.turnTimeoutMillis;
        gameVersion = 0;
        while (!terminate && System.currentTimeMillis() < reshuffleTime) {
            placeCardsOnTable();
            resumePlayerThreads();
            startTimer();
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
    public boolean  claimSet(Integer[] cards, Player claimer,int claimVersion){

        synchronized(gameVersion){
            if(claimVersion == gameVersion) 
                gameVersion++;
            else return false;
        }
            claimQueue.add(new Claim(cards,claimer,claimVersion));
            synchronized(wakeListener){wakeListener.notifyAll();}
            return true;      
    }

    
    /**
     * handles a claim that was verified as a true set
     * 
     * @param cards
     * @param claimer
     */
    private void handleClaimedSet(Claim claim) {

         if(isValidSet(claim.cards)){
            removeClaimedCards(claim.cards, claim.claimer);
            placeCardsOnTable();
            updateTimerDisplay(true);
            claim.validSet = true;
            for(Player player : players){
                if(player.getState() == Player.State.waitingForActivity | player.getState() == Player.State.waitingForClaim)
                    player.notifyClaim(claim); 
            }
        } else{
            claim.claimer.notifyClaim(claim);
        }   
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
    private boolean isValidSet(Integer[] cards) {
        synchronized(cards){
            int[] _cards = Arrays.stream(cards).mapToInt(i->table.slotToCard[i]).toArray();
            return env.util.testSet(_cards);
        }
    }

    /**
     * Starts the player threads 
     * @note This instantiates new player threads and calls start()
     * on the threads
     */
    private void resumePlayerThreads() {
        if(env.config.computerPlayers > 0) 
            Player.secretService = new AISuperSecretIntelligenceService(env);
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
        if(env.config.computerPlayers > 0) Player.secretService.continueExecution = false;
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

    private void terminatePlayers() {
        for(Player player : players){
            player.terminate();
        }  
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
                break; //TODO Think about this
            }
        }
    }
    
    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some purpose.
     */
    private void sleepUntilWokenOrTimeout() {
        
        if(reshuffleTime-System.currentTimeMillis() > 0){
            if(nextWakeTime-System.currentTimeMillis() > 0)
                try{
                    synchronized(wakeListener){wakeListener.wait(nextWakeTime-System.currentTimeMillis());
                    }
            }catch(InterruptedException ignored){}
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

    /*
    * Why does this not have a javadoc?
    */
    private void removeClaimedCards(Integer[] cards, Player claimer) {
        for(int card : cards){ // remove cards from table
            // deck.remove(card); do not remove from deck, card should already be out of the deck
            table.removeCard(card);
            // env.ui.removeToken(claimer.id, card);
        } 
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
        //TODO 
        return env.util.findSets(deck, 1).size() == 0 && table.getSetCount()==0;
    }
    
}
