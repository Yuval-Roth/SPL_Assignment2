package bguspl.set.ex;
import java.rmi.UnexpectedException;
import java.util.ConcurrentModificationException;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;

import bguspl.set.Env;

/**
 * This class manages the players' threads and data
 *
 * @inv id >= 0
 * @inv score >= 0
 */
public class Player implements Runnable {

    public enum State{
        busy,
        waitingForActivity,
        waitingForClaim,
        frozen,
        executionPaused,
        terminated
    }
        
    
        
    

    /**
     *
     */
    private static final int CLICK_TIME_PADDING = 100;
    private static final int SET_SIZE = 3;
    private static final int CLOCK_UPDATE_INTERVAL = 900;
    private static final int AI_WAIT_BETWEEN_KEY_PRESSES = 1000;

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;

    /*
     * Create a stack of int
     *      
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
     * True if game should be terminated due to an external event.
     */
    private volatile Boolean terminate;
    
    /**
     * The current score of the player.
     */
    private int score;
    
    /**
     * The game's dealer
     */
    private Dealer dealer;
    
    /**
     * Clicks queue
     */
    private volatile ConcurrentLinkedQueue<Integer> clickQueue;

    /**
     * Future timeout time for player freeze timer
     */
    private long timerTimeoutTime;
    
    // /**
    //  * Indicates whether the player should stop executing or not
    //  */
    // private volatile Boolean pauseExecution;

    private volatile LinkedList<Claim> claimNotificationQueue;

    private volatile Boolean claimNotification;

    private volatile State state;

    /**
     * Object for breaking wait() when execution should start
     */
    private volatile Object executionListener;
    private volatile Object activityListener;

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
        claimNotificationQueue = new LinkedList<>();
        terminate = false;
        claimNotification = false;
        executionListener = new Object();
        activityListener = new Object();
        state = State.executionPaused;
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
        while (state != State.terminated) {
            if(state == State.executionPaused){
                clearAllPlacedTokens();
                clearClickQueue();
                try{
                    synchronized(executionListener){
                        executionListener.wait();
                    }
                }catch(InterruptedException ignored){}
            }
            while(clickQueue.isEmpty() == false){
                Integer key = clickQueue.remove();
                placeOrRemoveToken(key);
            } 
            try{
                synchronized(activityListener){
                    activityListener.wait();
                }
            }catch(InterruptedException ignored){}
            if(claimNotification & (state == State.waitingForActivity | state == State.waitingForClaim))
                handleNotifiedClaim();         
        }
        
        if (!human) try { aiThread.join(); } catch (InterruptedException ignored) {}
        System.out.printf("Info: Thread %s terminated.%n", Thread.currentThread().getName());       
    }

    /**
     * Creates an additional thread for an AI (computer) player. 
     * The main loop of this thread repeatedly generates key presses. 
     * If the queue of key presses is full, the thread waits until it is not full.
     */
    private void createArtificialIntelligence() {
        // note: this is a very very basic AI (!)
        aiThread = new Thread(() -> {
            System.out.printf("Info: Thread %s starting.%n", Thread.currentThread().getName());
            aiThread = Thread.currentThread();

            try{synchronized(this){
                wait(AI_WAIT_BETWEEN_KEY_PRESSES);}
            } catch(InterruptedException ignored){}

            while (!terminate) {
                keyPressed_AI(generateKeyPress());
                // limit how fast the AI clicks buttons
                try{synchronized(this){
                    wait(AI_WAIT_BETWEEN_KEY_PRESSES);}
                } catch(InterruptedException ignored){}
            }
            System.out.printf("Info: Thread %s terminated.%n", Thread.currentThread().getName());
        }, "computer-" + id);
        aiThread.start();
    }

    

    //===========================================================
    //                      Main methods
    //===========================================================

    /**
     * If a token is placed in the given slot, remove it.
     * Otherwise, place a token in the given slot.
     * Placing or removing a token sends a message to the table.
     * Claims a set if the player has placed a full set.
     * @post - the token is placed or removed from the given slot.
     */
    private void placeOrRemoveToken(Integer slot){

        if(placedTokens.contains(slot) == false){
            if(table.placeToken(id, slot)){
                placedTokens.addLast(slot);
                while(placedTokens.size() == SET_SIZE){    
                    state = State.waitingForClaim;
                    clearClickQueue();
                    if (ClaimSet()) {    
                        break;
                    }
                    else if(claimNotification){
                        handleNotifiedClaim();
                    }
                    else {/*try again*/}
                        

                        // try{
                        //     synchronized(activityListener){activityListener.wait();}
                        // }catch(InterruptedException ignored){}

                } 
            }
        }
        else {
            clearPlacedToken(slot);
            state = State.waitingForActivity;        
        }
    }

    /**
     * @pre - The player has a placedTokens list of size SET_SIZE.
     * Claims a set if the player has placed a full set.
     * @post - The dealer is notified about the set claim.
     */
    private boolean ClaimSet() {
        if (placedTokens.size()!= SET_SIZE) return false;
        Integer[] array = new Integer[placedTokens.size()];
        int version = dealer.getGameVersion();
        try{Thread.sleep(CLICK_TIME_PADDING);}catch(InterruptedException ignored){}
        return dealer.claimSet(placedTokens.toArray(array), this,version);     
    }

    public void notifyClaim(Claim claim){
        if(state == State.waitingForActivity | state == State.waitingForClaim){
            claimNotificationQueue.add(claim);
            synchronized(claimNotification){claimNotification = true;}
            synchronized(activityListener){activityListener.notifyAll();}
        } 
    }

    private void handleNotifiedClaim() {

        int action = 0;
        boolean cardsRemoved = false;

        while(claimNotificationQueue.isEmpty() == false){
            Claim claim = claimNotificationQueue.remove();

            if(claim.claimer == this){
                action = claim.validSet ? 1:-1;
                clearAllPlacedTokens();
            }
            else if(claim.validSet){             
                for(Integer card : claim.cards){
                    if(placedTokens.contains(card)){
                        clearPlacedToken(card);
                        cardsRemoved = true;
                    }
                }
            }        
        }
        if(cardsRemoved) state = State.waitingForActivity;
        synchronized(claimNotification){claimNotification = false;}
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
     * @param timeToStop - future time to stop in milliseconds
     * @pre - the freeze timer is stopped
     * @post - the freeze timer is started
     * @post - the UI timer is updated
     */
    private void startFreezeTimer() {
        state = State.frozen;
        while(state != State.executionPaused & timerTimeoutTime >= System.currentTimeMillis() ){
            updateTimerDisplay();
            try{
                synchronized(this){wait(CLOCK_UPDATE_INTERVAL);}
            } catch (InterruptedException ignored){}
        }
        env.ui.setFreeze(id,0);
        state = State.waitingForActivity;
    }

    /**
     * Pauses the player's ability to interact with the game
     */
    public void pause(){
        state = State.executionPaused;
    }

    /**
     * Resumes the player's ability to interact with the game
     */
    public void resume(){
        state = State.waitingForActivity;
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
    private void keyPressed_AI(int slot) {
        if(state == State.waitingForActivity)
            clickQueue.add(slot);
        synchronized(activityListener){activityListener.notifyAll();}    
    }

    /**
     * Award a point to a player and perform other related actions.
     * @post - the player's score is increased by 1.
     * @post - the player's score is updated in the ui.
     */
    public void point() {
        env.ui.setScore(id, ++score);
        timerTimeoutTime = System.currentTimeMillis()+ env.config.pointFreezeMillis;
        startFreezeTimer();
    }

    /**
     * Penalize a player and perform other related actions.
     */
    public void penalty() {
        timerTimeoutTime = System.currentTimeMillis() + env.config.penaltyFreezeMillis;
        startFreezeTimer();
    }

    /**
     * Called when the game should be terminated due to an external event.
     * Interrupts the player thread and the AI thread (if any).
     * Clears the queue of tokens placed.
     */
    public void terminate() {
        state = State.terminated;
        synchronized(executionListener){executionListener.notifyAll();}
        try{
            playerThread.join();
        }catch(InterruptedException ignored){};
        clearAllPlacedTokens(); // clear the queue of tokens placed, because the table was also cleared
    }

    //===========================================================
    //                  utility methods
    //===========================================================


    /**
     * Updates the UI timer if the player is frozen
     */
    private void updateTimerDisplay() { 
        env.ui.setFreeze(id,timerTimeoutTime-System.currentTimeMillis());   
    }

    /**
     * Clears the queue of tokens placed.
     * Updates the UI to remove the tokens.
     * @post - the queue of tokens placed is cleared.
     */
    private void clearAllPlacedTokens(){
        while(placedTokens.isEmpty() == false){
            Integer token = placedTokens.peekFirst();
            if(table.removeToken(id, token)){
                placedTokens.removeFirst();
            }
        }    
    }

    private void clearPlacedToken(Integer slot) {
        if(table.removeToken(id, slot)){
           placedTokens.remove(slot);
        }
    }

    /**
     * Clears the pending clicks queue
     */
    private void clearClickQueue() {
        while(clickQueue.isEmpty() == false){
            clickQueue.remove();
        }
    }

    /**
     * @return a random key press in the size of the current table size.
     */
    private int generateKeyPress(){
        Random rand = new Random();
        return rand.nextInt(12/*getCurrentTableSize()*/);
    }


    /**
     * @return the current table size.
     */
    private int getCurrentTableSize(){
        return table.getCurrentSize();
    }

    //===========================================================
    //                  Getters / Setters
    //===========================================================
    
    /**
     * @return the player's score.
     */
    public int getScore() {
        return score;
    }

    public State getState() {
        return state;
    }
}
