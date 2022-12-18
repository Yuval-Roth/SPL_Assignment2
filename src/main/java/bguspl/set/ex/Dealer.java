package bguspl.set.ex;
import bguspl.set.Env;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * This class manages the dealer's threads and data
 */
public class Dealer implements Runnable {

    public static final int SET_SIZE = 3;
    private static final int timerUpdateCriticalTickTime = 25;
    private static final int timerUpdateTickTime = 250;

    /**
     * The game environment object.
     */
    private final Env env;

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
    private final LinkedList<Integer> deck;

    /**
     * The current timer mode.
     */
    private final TimerMode timerMode;

    /**
     * True if game should be terminated due to an external event.
     */
    private volatile boolean terminate;

    /**
     * The time when the dealer needs to reshuffle the deck due to turn timeout.
     */
    private volatile long reshuffleTime;

    /**
     * The time when the dealer needs to reshuffle the deck due to turn timeout.
     */
    private volatile long nextWakeTime;

    /**
     * The amount of time passed since the last set claimed.
     */
    private volatile long elapsedTime;

    /**
     * Holds all of the player threads
     */
    private Thread[] playerThreads;
    
    /**
     * a semaphore to control access to the gameVersion variable
     */
    private volatile Semaphore gameVersionAccess;

    /**
     * a version indicator for claimSet() actions
     * resets each shuffle
     * 
     * @inv gameVersion >= 0
     */
    private volatile Integer gameVersion;
    
    /**
     * a queue for claims made by the players
     */
    private volatile ConcurrentLinkedQueue<Claim> claimQueue;
    
    /**
     * a semaphore to control access to the claimQueue variable
     */
    private volatile Semaphore claimQueueAccess;

    /**
     * a listener for the dealer thread to wake up
     */
    private volatile Object wakeListener;

    // create an enum of time modes
    private enum TimerMode {
        elapsedTimerMode,
        CountdownTimerMode,
        NoTimerMode
    }

    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toCollection(LinkedList::new));
        playerThreads = new Thread[players.length];
        wakeListener = new Object();
        claimQueue = new ConcurrentLinkedQueue<>();
        gameVersionAccess = new Semaphore(1,true);
        claimQueueAccess = new Semaphore(players.length,true);

        if (env.config.turnTimeoutMillis > 0) {
            timerMode = TimerMode.CountdownTimerMode;
        }
        else if (env.config.turnTimeoutMillis == 0) {
            timerMode = TimerMode.elapsedTimerMode;
        }
        else {
            timerMode = TimerMode.NoTimerMode;
        }
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
            updateNextWakeTime(); 
            while(terminate == false & reshuffleTime > System.currentTimeMillis() & nextWakeTime > System.currentTimeMillis()){
                updateTimerDisplay(false);
                sleepUntilWokenOrTimeout();

                if(claimQueue.isEmpty() == false){
                    processClaims();
                }
            } 
                
        }
        if(terminate == false) env.ui.setCountdown(0,true);   
    }

    /**
     * processes all the claims that were made by the players
     */
    private void processClaims() {
        claimQueueAccess.acquireUninterruptibly(players.length);
        while(claimQueue.isEmpty() == false){
            Claim claim = claimQueue.remove();
            handleClaimedSet(claim);
            updateTimerDisplay(false);
            resetDebuggingTimer();
        }
        claimQueueAccess.release(players.length);
    }

    /**
     * The inner loop of the dealer thread that runs as long as the countdown did not time out.
     */
    private void timerLoop() {

        //TODO - break this into sub-classes / game-modes

        switch (timerMode) {
            case CountdownTimerMode: {
            reshuffleTime = System.currentTimeMillis() + env.config.turnTimeoutMillis;
            gameVersion = 0;
            while (!terminate && System.currentTimeMillis() < reshuffleTime) {
                fillDeck();
                resumePlayerThreads();
                startTimer();
                pausePlayerThreads();
                if(terminate) break;
                removeAllCardsFromTable();
                shuffleDeck();
                }
                break;
            }
            case elapsedTimerMode: {
            gameVersion = 0;
            while (!shouldFinish()) {
                fillDeck();
                resumePlayerThreads();
                startElapsedTimer();
                }
                break;
            }
            case NoTimerMode: {
                gameVersion = 0;
                while (!shouldFinish()) {
                    fillDeck();
                    resumePlayerThreads();
                    startNoTimer();
                }
                break;
            }
        }
    }


    private void startElapsedTimer() {

        updateElapsedTimeDisplay(true);
        while(terminate == false){
            updateNextWakeTime();
            while(terminate == false & nextWakeTime > System.currentTimeMillis()){
                updateElapsedTimeDisplay(false);
                sleepUntilWokenOrTimeout();
                if(claimQueue.isEmpty() == false){
                    processClaims();
                }
            }
        }
        if(terminate == false) env.ui.setElapsed(0);
    }


    private void startNoTimer() {
        reshuffleTime = Long.MAX_VALUE;
        while(terminate == false){
            updateNextWakeTime();
            while(terminate == false & nextWakeTime > System.currentTimeMillis()){
                sleepUntilWokenOrTimeout();
                if(claimQueue.isEmpty() == false){
                    processClaims();
                }
            }
        }
    }



    /**
     * Claims a set. adds the claim to the dealer's claimQueue and wakes up the dealer thread
     * @param cards - The cards forming the set
     * @param claimer - The player who claims the set
     * @param claimVersion - The gameVersion according to getGameVersion()
     */
    public boolean  claimSet(Integer[] cards, Player claimer, int claimVersion){

            resetDebuggingTimer();
            try{
                gameVersionAccess.acquire();
            }catch(InterruptedException ignored){}
            if(claimVersion == gameVersion) {
                gameVersion++;
                gameVersionAccess.release();
            }
            else {
                gameVersionAccess.release();
                return false;
            }

            claimQueueAccess.acquireUninterruptibly(1);
            claimQueue.add(new Claim(cards,claimer,claimVersion));
            claimQueueAccess.release(1);
            synchronized(wakeListener){wakeListener.notifyAll();}
            return true;      
    }

    
    /**
     * handles a claim that was verified as a true set
     * @param claim
     */
    private void handleClaimedSet(Claim claim) {
         if(isValidSet(claim.cards)){
             removeClaimedCards(claim.cards);
             if (deck.size() >= SET_SIZE /*&& !shouldFinish() */ ) { //TODO: Test the shouldFinish() condition
                placeCardsFromClaim();
             }
            updateTimerDisplay(true);
            claim.validSet = true;
            claim.claimer.notifyClaim(claim);
            for(Player player : players){
                if(player!=claim.claimer && (
                        player.getState() == Player.State.waitingForActivity |
                        player.getState() == Player.State.waitingForClaimResult|
                        player.getState() == Player.State.turningInClaim)){
                    player.notifyClaim(claim); 
                }
            }
        }else {
            claim.claimer.notifyClaim(claim);;
        }
        if (shouldFinish()) {
            terminate = true;
        }
    }

    private void placeCardsFromClaim() {
        LinkedList<Integer> cardsToPlace_U_Table = table.getCardsOnTable();
        boolean done = false;

        if(deck.size() >= SET_SIZE){
            //takes the next 3 cards from the deck and places them in the front of the list
            ListIterator<Integer> iter = deck.listIterator();
            for (int i = 0; i < SET_SIZE; i++) {
                cardsToPlace_U_Table.addFirst(iter.next());
            }
            while(!done){
                if(env.util.findSets(cardsToPlace_U_Table, 1).size() != 0){
                    for (int i = 0; i < SET_SIZE; i++) {
                        // place iter.previous() on table and delete it from deck
                        Integer card = iter.previous();
                        table.placeCard(card);
                        iter.remove();
                    }
                    done = true;
                }else if(iter.hasNext()){
                    cardsToPlace_U_Table.remove(SET_SIZE-1);
                    cardsToPlace_U_Table.addFirst(iter.next());
                }else {
                    placeCardsOnTable();
                    done = true;
                }
            }
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


    private void fillDeck() {
        LinkedList<Integer> slots = table.getCardsPlacementSlotsOrder();
        for(Integer slot : slots){
            Integer cardToPlace = deck.get(0);
            deck.remove(0);
            table.placeCard(cardToPlace,slot);
        }
    }

    /**
    * Checks if the given set of cards is a valid set.
    */
    public boolean isValidSet(Integer[] cards) {
            cards = Arrays.stream(cards).map(i->table.slotToCard[i]).toArray(Integer[]::new);
            int[] _cards = Arrays.stream(cards).filter(Objects::nonNull).mapToInt(i->i).toArray();
            if(_cards.length != SET_SIZE)
                return false;
            return env.util.testSet(_cards);
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
     * resumes the player threads 
     */
    private void resumePlayerThreads() {
        if(env.config.computerPlayers > 0) 
            Player.secretService = new AISuperSecretIntelligenceService(env, this,table);
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
            String name = "Player "+ players[i].id +", "+(players[i].human ? "Human":"AI");
            playerThreads[i] = new Thread(players[i],name);
            playerThreads[i].start();
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
        for(int i=players.length-1; i>=0; i--){
            players[i].terminate();
        }  
    }
 
    /**
     * Removes all cards from the table and returns them to the deck.
     */
    private void removeAllCardsFromTable() {
        Integer[] cardsRemoved = table.clearTable();
        for (Integer card: cardsRemoved) {
            if (card !=null) {
                deck.add(card);
            }
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
                break; // TODO: Think about this
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
        if (timerMode == TimerMode.CountdownTimerMode) {
            if (reset) reshuffleTime = System.currentTimeMillis() + env.config.turnTimeoutMillis;
            env.ui.setCountdown(reshuffleTime - System.currentTimeMillis(),
                    reshuffleTime - System.currentTimeMillis() <= env.config.turnTimeoutWarningMillis);
        }
        else if (timerMode == TimerMode.elapsedTimerMode) { // TODO: Implement noTimerMode here too
            if (reset) elapsedTime = System.currentTimeMillis();
            env.ui.setCountdown(System.currentTimeMillis() - elapsedTime, false);
        }
    }

    /**
     * Updates the elapsed time display.
     */
    private void updateElapsedTimeDisplay(boolean reset){
        if(reset) elapsedTime = System.currentTimeMillis();   
        env.ui.setElapsed(System.currentTimeMillis() - elapsedTime);
    }

    /**
     * Removes the claimed cards from the table .
     * @param cards
     */
    private void removeClaimedCards(Integer[] cards) { //TODO: rename to removeClaimedSlots to prevent confusion
        // These are slots and not cards
        for(int card : cards){ // remove cards from table
            // deck.remove(card); do not remove from deck, card should already be out of the deck
            table.removeCard(card);
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
    private void placeNextCardOnTable() {
        int cardToPlace = deck.get(0);
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
        
        LinkedList<Integer> allCards = new LinkedList<>();
        allCards.addAll(deck);
        allCards.addAll(table.getCardsOnTable());

        return env.util.findSets(allCards, 1).size() == 0;
    }

    /**
     * Updates the next wake time of the dealer thread.
     */
    private void updateNextWakeTime() {
        nextWakeTime =  reshuffleTime-System.currentTimeMillis() > env.config.turnTimeoutWarningMillis ?
            System.currentTimeMillis()+timerUpdateTickTime : System.currentTimeMillis()+timerUpdateCriticalTickTime;
    }

    /**
     * dumps the dealer's data to the console
     * for debugging purposes
     */
    private void dumpData() {
        System.out.println("dumping Dealer data:");
        System.out.println("reshuffleTime: " + (reshuffleTime-System.currentTimeMillis())/1000.0);
        System.out.println("claimQueue.isEmpty():"+claimQueue.isEmpty());
        System.out.println("claimQueue.size:"+claimQueue.size());
        System.out.println("claimQueue.size:");
        for(Claim claim : claimQueue){
            System.out.println(claim);
        }
        System.out.println("====================================");
    }


    //========used for debugging==============|
    private volatile boolean stop = false;
    private volatile long debuggingTimer = 0;
    //========================================|

    /**
     * @DONT_FORGET: to set 'stop = true' when the game should pause
     * AND resetDebuggingTimer() between actions
     */
    private void startDebuggerThread() {
        Thread dealerThread = Thread.currentThread();
        Thread debuggingThread = new Thread(()->{
            stop = false;
            resetDebuggingTimer();
            while(stop == false & System.currentTimeMillis() - debuggingTimer < env.config.penaltyFreezeMillis+3000){
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {}
            }
            if(stop == false){
                for(Player player : players){
                    player.dumpData();
                }
                dumpData();
                dealerThread.interrupt();   
                reshuffleTime = Long.MAX_VALUE;
                throw new RuntimeException("Player threads unresponsive");
            }
            
        });
        debuggingThread.start();
    }
    /**
     * for debugging purposes
     */
    private void resetDebuggingTimer() {
        debuggingTimer = System.currentTimeMillis();
    }
    
}
