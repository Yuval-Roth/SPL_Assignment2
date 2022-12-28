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
    private static final int TIMER_UPDATE_CRITICAL_TICK_TIME = 25;
    private static final int TIMER_UPDATE_TICK_TIME = 250;
    private static final int TIMER_PADDING = TIMER_UPDATE_TICK_TIME*2;

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
     * @notice this needs to be public to prevent the UI from being updated after the game has been terminated because
     * it causes a deadlock in linux for some reason out of our control 
     */
    public volatile boolean terminate;

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

    /**
     * True iff there are no more sets in the deck.
     */
    private boolean noMoreSets;
    private boolean mHints;

    /**
     * All the possible timer modes.
     */
    private enum TimerMode {
        elapsedTimerMode,
        countdownTimerMode,
        noTimerMode
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
        mHints = env.config.hints; 

        if (env.config.turnTimeoutMillis > 0) {
            timerMode = TimerMode.countdownTimerMode;
        }
        else if (env.config.turnTimeoutMillis == 0) {
            timerMode = TimerMode.elapsedTimerMode;
        }
        else {
            timerMode = TimerMode.noTimerMode;
        }
    }
    
    
    //===========================================================
    //                      Dealer thread
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
        gameVersion = 0;
        while (!shouldFinish()){
            switch (timerMode) {
                case countdownTimerMode: {
                    runCountdownMode();
                    break;
                }
                case elapsedTimerMode: {
                    runElapsedTimeMode();
                    break;
                }
                case noTimerMode: {
                    runNoTimerMode();
                    break;
                }
            }
        }
        terminatePlayers();
        if(env.util.findSets(deck, 1).size() == 0) announceWinners();
        System.out.printf("Info: Thread %s terminated.%n", Thread.currentThread().getName());
    }

    //===========================================================
    //                      Timer modes
    //===========================================================


    /**
     *  no timer mode main method
     */
    private void runNoTimerMode() {
        dealCardsRandomly();
        if (mHints) {
            table.hints();;
        }
        resumePlayerThreads();
        startNoTimer();
        pausePlayerThreads();

        // if the game is terminated, don't remove the cards from the table
        // just return and let the dealer thread terminate
        if(terminate) return;
        removeAllCardsFromTable();
    }

    /**
     *  elapsed timer mode main method
     */
    private void runElapsedTimeMode() {
        dealCardsRandomly();
        if (mHints) {
            table.hints();;
        }
        resumePlayerThreads();
        startElapsedTimer();
        pausePlayerThreads();

        // if the game is terminated, don't remove the cards from the table
        // just return and let the dealer thread terminate
        if(terminate) return;
        removeAllCardsFromTable();
    }

    /**
     *  countdown timer mode main method
     */
    private void runCountdownMode() {
        reshuffleTime = System.currentTimeMillis() + env.config.turnTimeoutMillis;
        while (!terminate && System.currentTimeMillis() < reshuffleTime) {
            dealCardsRandomly();
            if (mHints) {
                table.hints();;
            }
            resumePlayerThreads();
            startCountdownTimer();
            pausePlayerThreads();

            // if the game is terminated, don't remove the cards from the table
            // just return and let the dealer thread terminate
            if(terminate) break;
            removeAllCardsFromTable();
            if (shouldFinish()) terminate();
            shuffleDeck();
        }
    }

    //===========================================================
    //                      Timers
    //===========================================================


    /**
     * in Countdown mode, Dealer spends most of its time in this method.
     * it sleeps until it is woken up by a player thread or needs to updates the timer.
     */
    private void startCountdownTimer() {    
        updateTimerDisplay(true);
        while(terminate == false & noMoreSets == false & reshuffleTime > System.currentTimeMillis()){
            updateNextWakeTime(); 
            while(terminate == false & noMoreSets == false & reshuffleTime > System.currentTimeMillis() & nextWakeTime > System.currentTimeMillis()){
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
     * in Elapsed mode, Dealer spends most of its time in this method.
     * it sleeps until it is woken up by a player thread or needs to updates the timer.
     */
    private void startElapsedTimer() {
        updateElapsedTimeDisplay(true);
        while(terminate == false & noMoreSets == false){
            updateNextWakeTime();
            while(terminate == false & noMoreSets == false & nextWakeTime > System.currentTimeMillis()){
                updateElapsedTimeDisplay(false);
                sleepUntilWokenOrTimeout();
                if(claimQueue.isEmpty() == false){
                    processClaims();
                }
            }
        }
        if(terminate == false) env.ui.setElapsed(0);
    }

    /**
     * in No timer mode, Dealer spends most of its time in this method.
     * it sleeps until it is woken up by a player thread.
     * it does not update the timer display.
     */
    private void startNoTimer() {

        reshuffleTime = Long.MAX_VALUE;
        nextWakeTime = Long.MAX_VALUE;

        while(terminate == false & noMoreSets == false){
            sleepUntilWokenOrTimeout();
            if(claimQueue.isEmpty() == false){
                processClaims();
            }
        }
    }

    //===========================================================
    //                      Main methods
    //===========================================================

    /**
     * Claims a set. adds the claim to the dealer's claimQueue and wakes up the dealer thread
     * @param cards - The cards forming the set
     * @param claimer - The player who claims the set
     * @param claimVersion - The gameVersion according to getGameVersion()
     */
    public boolean  claimSet(Integer[] cards, Player claimer, int claimVersion){

        // this is a critical section that must be synchronized 
        // because this is the solution to multiple players claiming the same cards
        // at virtually the same time. we combat this problem by using versions for claims 
        // and a fair semaphore.
        try{
            gameVersionAccess.acquire();
        }catch(InterruptedException ignored){}
        if(claimVersion == gameVersion) {
            gameVersion++;
            gameVersionAccess.release();
        }
        else {

            // another claimSet was made virtually at the same time as this one but reached the dealer first.
            // To be able to make sure that the player isn't penalized for claiming cards
            // that were just replaced thus claiming a "wrong" set, the player must check for changes 
            // and claim the set again if none of his cards were affected by the claim

            gameVersionAccess.release();
            // the claim was rejected
            return false;
        }

        claimQueueAccess.acquireUninterruptibly(1);
        claimQueue.add(new Claim(cards,claimer,claimVersion));
        claimQueueAccess.release(1);

        // wake up the dealer thread to process the claim
        synchronized(wakeListener){wakeListener.notifyAll();}

        // the claim was accepted
        return true;      
    }

    /**
     * processes all the claims that were made by the players
     */
    private void processClaims() {
        claimQueueAccess.acquireUninterruptibly(players.length);
        while(claimQueue.isEmpty() == false){
            Claim claim = claimQueue.remove();
            handleClaimedSet(claim);
        }
        claimQueueAccess.release(players.length);
    }
    
    /**
     * handles a claim that was verified as a true set
     * and notifies the players accordingly
     * @param claim
     */
    private void handleClaimedSet(Claim claim) {
         if(isValidSet(claim.cards)){

            // remove the cards from the deck and replace them with new cards
            // while making sure that there are sets on the table
             clearSlots(claim.cards);
             if (deck.size() >= SET_SIZE) {
                placeCardsFromClaim();
                if (mHints) {
                    table.hints();;
                }
             }

            updateTimerDisplay(true);
            claim.validSet = true;

            //notify the claimer first and then the other players
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
            // if the claim was not a valid set notify the claimer only.
            // no need to notify the other players
            claim.claimer.notifyClaim(claim);;
        }
       if (shouldFinish()) {
            // if there are no more sets in the game then we want to end the game immediately
            // this is done by setting the noMoreSets flag to true
            // and this breaks the dealer thread out of the while loops
            // and the dealer thread will then terminate the game
           noMoreSets = true;
       }
    }


    /**
     * this method replaces the claimed cards with new cards from the deck
     * and makes sure there is always a set on the table
     */
    private void placeCardsFromClaim() {

        //this is a list of the cards that are currently on the table
        //after removing the cards that were claimed
        LinkedList<Integer> cardsToPlace_U_Table = table.getCardsOnTable();

        boolean done = false;

        if(deck.size() >= SET_SIZE){

            //takes the next 3 cards from the deck and places them in the front of the list
            ListIterator<Integer> iter = deck.listIterator();
            for (int i = 0; i < SET_SIZE; i++) {
                cardsToPlace_U_Table.addFirst(iter.next());
            }
            //==============================================================================

            while(!done){

                // this checks if there is a set in the list and if so it places the last 3 cards 
                // that were added to the list on the table and removes them from the deck
                if(env.util.findSets(cardsToPlace_U_Table, 1).size() != 0){
                    for (int i = 0; i < SET_SIZE; i++) {
                        // place iter.previous() on table and delete it from deck
                        Integer card = iter.previous();
                        table.placeCard(card);
                        iter.remove();
                    }
                    done = true;

                 // if there is no set in the list then it removes the oldest card added to the list
                 // and adds the next card from the deck to the list in a FIFO fashion
                }else if(iter.hasNext()){
                    cardsToPlace_U_Table.remove(SET_SIZE-1);
                    cardsToPlace_U_Table.addFirst(iter.next());


                // if all fails and you can't find a set just place the
                // next cards from the deck on the table
                // this should never realistically happen but it is here just in case.
                // the game would end in this situation.
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


    /**
     * Fills the table with cards from the deck. Note that this method
     * fills the table in random order, as opposed to
     * placeCardsOnTable() which fills the table in order of the deck.
     */
    private void dealCardsRandomly() {

        // this gets the order in which the cards should be placed on the table
        // this is a random order and the slots themselves are determined by where
        // the cards were before they were removed from the table.
        LinkedList<Integer> slots = table.getCardsPlacementSlotsOrder();

        // this randomizes the deck until there is a set in the first 12 cards.
        // this is done to make sure that there is a set on the table after reshuffling
        while (env.util.findSets(deck.subList(0,slots.size()) , 1).size() == 0) {
            shuffleDeck();
        }

        // this places the cards on the table in the order determined by the slots list
        for(Integer slot : slots){
            Integer cardToPlace = deck.get(0);
            deck.remove(0);
            table.placeCard(cardToPlace,slot);
        }

    }

    /**
    * Checks if the given set of cards is a valid set.
    */
    public boolean isValidSet(Integer[] slots) {
        // get an int[] card array from the slots array while filtering out null slots
        slots = Arrays.stream(slots).map(i->table.slotToCard[i]).toArray(Integer[]::new);
        int[] cards = Arrays.stream(slots).filter(Objects::nonNull).mapToInt(i->i).toArray();

        // if the resulting array is not the correct size then it is not a valid set
        // because there was a null slot when converting the slots to cards
        if(cards.length != SET_SIZE)
            return false;

        return env.util.testSet(cards);
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
        return terminate || noMoreSets || allSetsDepleted();
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
                break;
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
        
            if (timerMode == TimerMode.countdownTimerMode) {
                if (reset) reshuffleTime = System.currentTimeMillis() + env.config.turnTimeoutMillis;

                // this if statement is to prevent the UI from being updated after the game has been terminated because
                // it causes a deadlock in linux for some reason out of our control 
                if(terminate == false){
                    env.ui.setCountdown(reshuffleTime - System.currentTimeMillis() + TIMER_PADDING,
                    reshuffleTime - System.currentTimeMillis() + TIMER_PADDING <= env.config.turnTimeoutWarningMillis);
                }
            } else if (timerMode == TimerMode.elapsedTimerMode) {
                updateElapsedTimeDisplay(reset);
            

            // In No Timer mode, the timer display is not updated as it is not needed.
        }
    }

    /**
     * Updates the elapsed time display.
     */
    private void updateElapsedTimeDisplay(boolean reset){
        if (reset) elapsedTime = System.currentTimeMillis();

        // this if statement is to prevent the UI from being updated after the game has been terminated because
        // it causes a deadlock in linux for some reason out of our control 
        if (!terminate) {
            env.ui.setElapsed(System.currentTimeMillis() - elapsedTime + TIMER_PADDING);
        }
    }

    /**
     * clears the cards in these slots from the table .
     * @param slots
     */
    private void clearSlots(Integer[] slots) {
        for(int slot : slots){
            table.removeCard(slot);
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
            System.currentTimeMillis()+TIMER_UPDATE_TICK_TIME : System.currentTimeMillis()+TIMER_UPDATE_CRITICAL_TICK_TIME;
    }

    //========================================================================================================|


    //========used for debugging==============|
    private volatile boolean stop = false;
    private volatile long debuggingTimer = 0;
    //========================================|

    /**
     * A thread that counts time and throws an exception if a certain amount of time has passed
     * without player/dealer activity.
     * The thread dumps the players' and the dealer's data to the console for further examination.
     *
     * @DONT_FORGET to set 'stop = true' when the game should pause
     * AND resetDebuggingTimer() between actions
     */
    private void startDebuggerThread() {
        Thread dealerThread = Thread.currentThread();
        long MAX_INACTIVITY_TIME = env.config.penaltyFreezeMillis+3000;
        Thread debuggingThread = new Thread(()->{
            stop = false;
            resetDebuggingTimer();
            while(stop == false & System.currentTimeMillis() - debuggingTimer < MAX_INACTIVITY_TIME){
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
    
}
