package bguspl.set.ex;
import java.util.LinkedList;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;

import bguspl.set.Env;

/**
 * This class manages the players' threads and data
 *
 * @inv id >= 0
 * @inv score >= 0
 */
public class Player implements Runnable {

    /**
     * The player's possible states
     */
    public enum State{
        waitingForActivity,
        turningInClaim,
        waitingForClaimResult,
        frozen,
        pausingExecution,
        paused,
        terminated
    }
    
    private static final int CLICK_TIME_PADDING = 100;
    private static final int CLOCK_UPDATE_INTERVAL = 250;

    /**
     * The AI service
     */
    public static AISuperSecretIntelligenceService secretService;
    
    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;

    /**
     * The player's currently placed tokens.
     */
    private LinkedList<Integer> placedTokens;

    /**
     * The id of the player (starting from 0).
     */
    public final int id;

    /**
     * The thread representing the current player.
     */
    private Thread playerThread;

    /**
     * The thread of the AI (computer) player (an additional thread used to generate key presses).
     */
    private Thread aiThread;

    /**
     * True iff the player is human (not a computer player).
     */
    public final boolean human;
    
    /**
     * The current score of the player.
     */
    private int score;
    
    /**
     * The game's dealer
     */
    private Dealer dealer;
    
    /**
     * The clicks queue.
     */
    private volatile ConcurrentLinkedQueue<Integer> clickQueue;

    /**
     * Future timeout time for player freeze timer
     */
    private long freezeUntil;

    /**
     * The claim queue.
     * @important needs to accessed using claimQueueAccess semaphore
     */
    private volatile ConcurrentLinkedQueue<Claim> claimQueue;

    // private volatile Boolean claimNotification;

    private volatile State state;

    /**
     * Object for breaking wait() when game execution should resume
     */
    private volatile Object executionListener;

    /**
     * Object for breaking wait() when waiting for general activity
     */
    private volatile Object activityListener;

    /**
     * Object for breaking wait() when waiting for claim result
     */
    private volatile Object claimListener;
    /**
     * Object for breaking wait() when the AI is waiting
     */
    private volatile Object AIListener;

    /**
     * True if the AI thread is running
     */
    private volatile boolean AIRunning;

    /**
     * The remaining time to freeze the player
     */
    private long freezeRemainder;

    /**
     * The semaphore used to control access to the click queue.
     */
    private Semaphore claimQueueAccess;

    
    /**
     * The class constructor.
     *
     * @param env    - the environment object.
     * @param dealer - the dealer object.
     * @param table  - the table object.
     * @param id     - the id of the player.
     * @param human  - true iff the player is a human player (i.e. input is provided manually, via the keyboard).
     */
    public Player(Env env, Dealer dealer, Table table, int id, boolean human) {
        this.env = env;
        this.table = table;
        this.id = id;
        this.human = human;
        this.dealer = dealer;
        placedTokens = new LinkedList<>();
        clickQueue = new ConcurrentLinkedQueue<>();
        claimQueue = new ConcurrentLinkedQueue<>();
        AIRunning = false;
        executionListener = new Object();
        activityListener = new Object();
        claimListener = new Object();
        AIListener = new Object();
        state = State.pausingExecution;
        freezeRemainder = 0;
        claimQueueAccess = new Semaphore(1,true);
    }

    //===========================================================
    //                      Threads
    //===========================================================

    /**
     * The main player thread of each player starts here (main loop for the player thread).
     */
    @Override
    public void run() {
            System.out.printf("Info: Thread %s starting.%n", Thread.currentThread().getName());
            playerThread = Thread.currentThread();
            if (!human) createArtificialIntelligence();

            //main loop
            while (state != State.terminated) {

                //while the game is still running
                if(state == State.pausingExecution){
                    enterPausedState();

                    //do this after being released from paused state
                    if(state == State.terminated) break;
                    else if (freezeRemainder > 0) startFreezeTimer(freezeRemainder);
                }

                //check if game needs to be paused
                if(state != State.pausingExecution & state != State.terminated){
                    
                    //if there is a click to be processed
                    while(clickQueue.isEmpty() == false & state == State.waitingForActivity){
                        Integer key = clickQueue.remove();
                        placeOrRemoveToken(key);
                    }

                    //if there is a claim to be turned in
                    if(state == State.turningInClaim){
                        turnInClaim();
                    } 

                    //number of tries to wait for claim result
                    int tries = 0;
                    while(state == State.waitingForClaimResult & tries < 10){  
                        try{
                            //wait for claim result
                            synchronized(claimListener){claimListener.wait(generateWaitingTime());}
                        }catch(InterruptedException ignored){} 

                        //if a claim was notified, handle it
                        if(claimQueue.isEmpty() == false & state == State.waitingForClaimResult){
                            handleNotifiedClaim();
                        }else  tries++; //if no claim was notified, increment tries
                    }

                    //disaster recovery if claim result was not notified
                    if(tries >= 10 & state == State.waitingForClaimResult){
                        state = State.turningInClaim;
                        continue;
                    }

                    try{
                        synchronized(activityListener){
                            //wait for activity
                            activityListener.wait();
                        }
                    }catch(InterruptedException ignored){}

                    //if a claim was notified, handle it
                    if(claimQueue.isEmpty() == false & state == State.waitingForActivity){
                        handleNotifiedClaim();         
                    }
                }
            }
        
        if (!human) try { aiThread.join(); } catch (InterruptedException ignored) {}
        System.out.printf("Info: Thread %s terminated.%n", Thread.currentThread().getName());       
    }

    public void notifyClaim(Claim claim){
        if(state == State.waitingForActivity | state == State.waitingForClaimResult){
            claimQueueAccess.acquireUninterruptibly();
            claimQueue.add(claim);
            claimQueueAccess.release();
            // synchronized(claimNotification){claimNotification = true;}
            synchronized(claimListener){claimListener.notifyAll();}
        }
    }

    /**
     * Pauses the player's ability to interact with the game
     */
    public void pause(){
        int tries = 0;
        do {
            if(tries++ % 10 == 0) state = State.pausingExecution;
            synchronized(AIListener){AIListener.notifyAll();}
            synchronized(activityListener){activityListener.notifyAll();}
            synchronized(claimListener){claimListener.notifyAll();}
            try{Thread.sleep(10);}catch(InterruptedException ignored){}
        }while(state != State.paused | AIRunning);
    }

    

    //===========================================================
    //                      Main methods
    //===========================================================

    /**
     * Resumes the player's ability to interact with the game
     */
    public void resume(){
        state = State.waitingForActivity;
        if(human == false) AIRunning = true;
        synchronized(executionListener){executionListener.notifyAll();}
    }

    /**
     * This method is called when a key is pressed.
     *
     * @param slot - the slot corresponding to the key pressed.
     */
    public void keyPressed(int slot) {

        if(human){
            if(state == State.waitingForActivity)
                clickQueue.add(slot);
            synchronized(activityListener){activityListener.notifyAll();}
        }       
    }

    /**
     * Award a point to a player and perform other related actions.
     * @post - the player's score is increased by 1.
     * @post - the player's score is updated in the ui.
     */
    public void point() {
        env.ui.setScore(id, ++score);
        if(env.config.pointFreezeMillis > 0) startFreezeTimer(env.config.pointFreezeMillis);
        else if(state != State.pausingExecution) state = State.waitingForActivity;
    }

    /**
     * Penalize a player and perform other related actions.
     */
    public void penalty() {
        if(env.config.penaltyFreezeMillis > 0) startFreezeTimer(env.config.penaltyFreezeMillis);
        else if(state != State.pausingExecution) state = State.waitingForActivity;
    }

    /**
     * Called when the game should be terminated due to an external event.
     * Interrupts the player thread and the AI thread (if any).
     * Clears the queue of tokens placed.
     */
    public void terminate() {
        state = State.terminated;
        synchronized(activityListener){activityListener.notifyAll();}
        synchronized(executionListener){executionListener.notifyAll();}
        try{
            playerThread.join();
        }catch(InterruptedException ignored){};
    }

    /**
     * @return the player's score.
     */
    public int getScore() {
        return score;
    }

    public State getState() {
        return state;
    }

    /**
     * dumps the player's data to the console.
     * Used for debugging.
     */
    public void dumpData(){
        System.out.println("dumping player "+id+" data:");
            System.out.println("State: "+state);
            System.out.println("placedTokens: "+placedTokens);
            System.out.println("claimQueue.isEmpty(): "+claimQueue.isEmpty());
            System.out.println("claimQueue.size: "+claimQueue.size());
            System.out.println("claimQueue: ");
            for(Claim c : claimQueue)
                System.out.println(c);
            System.out.println("remainingFreezeTime: "+freezeRemainder);
            System.out.println("================================");
    }

    /**
     * Pauses the player thread and waits for it to be released.
     */
    private void enterPausedState() {
        clearAllPlacedTokens();
        clearClickQueue();
        try{
            state = State.paused;
            synchronized(executionListener){executionListener.wait();}
        }catch(InterruptedException ignored){}
    }
    /**
     * Creates an additional thread for an AI (computer) player. 
     * The main loop of this thread repeatedly generates key presses. 
     * If the queue of key presses is full, the thread waits until it is not full.
     */
    private void createArtificialIntelligence() {
        // note: this is a very secretive AI.... SHHH!
        aiThread = new Thread(() -> {
            System.out.printf("Info: Thread %s starting.%n", Thread.currentThread().getName());
            aiThread = Thread.currentThread();

            //wait until the game starts
            try{
                synchronized(executionListener){
                    executionListener.wait();
                }
            }catch(InterruptedException ignored){}

            AIRunning = true; //AI is now running
            
            while (state!=State.terminated) {
                Integer[] keys = secretService.getIntel(); //get the keys to press

                int currentScore = score; //score before the AI makes a move

                for(int i = 0; i < keys.length & state != State.pausingExecution & state != State.paused ; i++){
                    // limit how fast the AI clicks buttons
                    try{synchronized(AIListener){AIListener.wait(generateAIWaitTime());}
                    } catch(InterruptedException ignored){}
                    keyPressed_AI(keys[i]);
                }

                //if the player is waiting, gather intel
                while(state == State.waitingForClaimResult | state == State.turningInClaim){
                    try{synchronized(AIListener){AIListener.wait(secretService.WAIT_BETWEEN_INTELLIGENCE_GATHERING);}
                    } catch(InterruptedException ignored){}
                    secretService.gatherIntel();
                }

                //if the game does not need to be paused, report the claim
                if(state != State.pausingExecution & state!=State.paused){
                    if (currentScore < score)
                        secretService.reportSetClaimed(keys);
                    else secretService.sendIntel(keys,false); 
                }

                //if the player is frozen, gather intel
                while(state == State.frozen){
                    try{synchronized(AIListener){AIListener.wait(secretService.WAIT_BETWEEN_INTELLIGENCE_GATHERING);}
                    }catch(InterruptedException ignored){}
                    secretService.gatherIntel();
                }

                //if the game needs to be paused, wait until it is unpaused
                if(state == State.pausingExecution | state == State.paused){
                    try{
                        AIRunning = false; //AI is not running while game is paused
                        synchronized(executionListener){
                            executionListener.wait();
                        }
                    }catch(InterruptedException ignored){}
                }        
            }
            System.out.printf("Info: Thread %s terminated.%n", Thread.currentThread().getName());
        }, "computer-" + id);
        aiThread.start();
    }

    /**
     * If a token is placed in the given slot, remove it.
     * Otherwise, place a token in the given slot.
     * Placing or removing a token sends a message to the table.
     * Claims a set if the player has placed a full set.
     * @post - the token is placed or removed from the given slot.
     */
    private void placeOrRemoveToken(Integer slot){

        if(placedTokens.contains(slot) == false){
            boolean insertState = false;
            int tries = 0;
            while(insertState == false & tries <=5 & state != State.pausingExecution){
                insertState = table.placeToken(id, slot);
                tries++;
                try{Thread.sleep(10);}catch(InterruptedException ignored){}
            }
            if(insertState){
                placedTokens.addLast(slot);
                if(placedTokens.size() == Dealer.SET_SIZE) {
                    state = State.turningInClaim;
                    clearClickQueue();
                } 
            }
        }
        else {
            clearPlacedToken(slot);       
        }
    }

    private void turnInClaim(){
        Integer[] array = placedTokens.stream().toArray(Integer[]::new);
        while(placedTokens.size() == Dealer.SET_SIZE & state == State.turningInClaim){
            if(ClaimSet(array) == false) {     
                if(claimQueue.isEmpty() == false){
                    handleNotifiedClaim();
                    if(state != State.turningInClaim) return;    
                }

                //sleep for a short random time and try again
                try{
                    Thread.sleep((long)(Math.random()*(25-10)+10));
                }catch(InterruptedException ignored){}

            } else if(state != State.pausingExecution) state = State.waitingForClaimResult;
        }
    }

    /**
     * @pre - The player has a placedTokens list of size SET_SIZE.
     * Claims a set if the player has placed a full set.
     * @post - The dealer is notified about the set claim.
     */
    private boolean ClaimSet(Integer[] array) {
        int version = dealer.getGameVersion();
        try{Thread.sleep(CLICK_TIME_PADDING);}catch(InterruptedException ignored){}
        return dealer.claimSet(array, this,version);     
    }

    //===========================================================
    //                  utility methods
    //===========================================================

    private void handleNotifiedClaim() {

        int action = 0;
        boolean cardsRemoved = false;
        claimQueueAccess.acquireUninterruptibly();
        while(claimQueue.isEmpty() == false){
            Claim claim = claimQueue.remove();
            if(claim.claimer == this){
                action = claim.validSet ? 1:-1;
                clearAllPlacedTokens();
                break;
            }
            else{ 
                if(claim.validSet){
                    for(Integer card : claim.cards){
                        if(placedTokens.contains(card)){
                            clearPlacedToken(card);
                            cardsRemoved = true;
                        }
                    }
                }            
            }        
        }
        claimQueueAccess.release();
        if(cardsRemoved & state != State.pausingExecution) state = State.waitingForActivity;
        switch(action){
            case 0 : break;
            case 1:{
                point();
                break;
            } 
            case -1: {
                penalty();
                break;
            }
        }
    }

    /**
     * Starts a freeze time thread and updates the UI timer
     * @pre - the freeze timer is stopped
     * @post - the freeze timer is started
     * @post - the UI timer is updated
     */
    private void startFreezeTimer(long freezeTime) {
        if(state != State.pausingExecution) state = State.frozen;
        freezeUntil = System.currentTimeMillis() +  freezeTime;
        updateTimerDisplay(freezeUntil-System.currentTimeMillis());
        while(state == State.frozen & freezeUntil >= System.currentTimeMillis() ){
            try{
                synchronized(this){wait(CLOCK_UPDATE_INTERVAL);}
            } catch (InterruptedException ignored){}
            updateTimerDisplay(freezeUntil-System.currentTimeMillis()); 
        }
        freezeRemainder = freezeUntil - System.currentTimeMillis();
        if(state == State.frozen){
            env.ui.setFreeze(id,0);
            state = State.waitingForActivity;
        }
    }

    private void keyPressed_AI(int slot) {
        if(state == State.waitingForActivity)
            clickQueue.add(slot);
        synchronized(activityListener){activityListener.notifyAll();}    
    }

    private long generateWaitingTime() {  
        if(claimQueue.isEmpty() == false) return 1;
        else return 50;
    }

    private void clearClaimNotificationQueue() {
        while(claimQueue.isEmpty() == false){
            claimQueue.remove();
        }
    }

    /**
     * Updates the UI timer if the player is frozen
     */
    private void updateTimerDisplay(long time) { 
        env.ui.setFreeze(id,time);   
    }

    /**
     * Clears the queue of tokens placed.
     * Updates the UI to remove the tokens.
     * @post - the queue of tokens placed is cleared.
     */
    private void clearAllPlacedTokens(){
        while(placedTokens.isEmpty() == false){
            Integer token = placedTokens.peekFirst();
            table.removeToken(id, token);
            placedTokens.removeFirst();
        }    
    }

    //===========================================================
    //                  Getters / Setters
    //===========================================================
    
    private void clearPlacedToken(Integer slot) {
        table.removeToken(id, slot);
        placedTokens.remove(slot);
    }

    /**
     * Clears the pending clicks queue
     */
    private void clearClickQueue() {
        while(clickQueue.isEmpty() == false){
            clickQueue.remove();
        }
    }

    private int generateAIWaitTime() {
        return (int)(Math.random()*
        (secretService.AI_WAIT_BETWEEN_KEY_PRESSES*(3.0/2.0) - secretService.AI_WAIT_BETWEEN_KEY_PRESSES/2.0)+
         secretService.AI_WAIT_BETWEEN_KEY_PRESSES/2.0);
    }
}
