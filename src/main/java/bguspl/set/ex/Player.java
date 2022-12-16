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

    private volatile PlayerState state;

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

    private PlayerState[] playerStates;

    
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
        freezeRemainder = 0;
        claimQueueAccess = new Semaphore(1,true);
        playerStates = new PlayerState[7];

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

            //while the game is still running
            while (getState() != State.terminated) {
                state.run();
            }
            
        
        if (!human) try { aiThread.join(); } catch (InterruptedException ignored) {}
        System.out.printf("Info: Thread %s terminated.%n", Thread.currentThread().getName());       
    }

    public void notifyClaim(Claim claim){
        if(state.getState() == State.waitingForActivity | state.getState() == State.waitingForClaimResult){
            claimQueueAccess.acquireUninterruptibly();
            claimQueue.add(claim);
            claimQueueAccess.release();
            synchronized(claimListener){claimListener.notifyAll();}
        }
    }

    /**
     * Pauses the player's ability to interact with the game
     */
    public void pause(){
        int tries = 0;
        do {
            if(tries++ % 10 == 0) setState(State.pausingExecution);
            synchronized(this){this.notifyAll();}
            synchronized(AIListener){AIListener.notifyAll();}
            synchronized(activityListener){activityListener.notifyAll();}
            synchronized(claimListener){claimListener.notifyAll();}
            try{Thread.sleep(10);}catch(InterruptedException ignored){}
        }while(getState() != State.paused | AIRunning);
    }

    

    //===========================================================
    //                      Main methods
    //===========================================================

    /**
     * Resumes the player's ability to interact with the game
     */
    public void resume(){
        setState(State.waitingForActivity);
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
            if(state.getState() == State.waitingForActivity)
                clickQueue.add(slot);
            synchronized(activityListener){activityListener.notifyAll();}
        }       
    }

    /**
     * Called when the game should be terminated due to an external event.
     * Interrupts the player thread and the AI thread (if any).
     * Clears the queue of tokens placed.
     */
    public void terminate() {
        setState(State.terminated);
        synchronized(activityListener){activityListener.notifyAll();}
        synchronized(executionListener){executionListener.notifyAll();}
        try{
            playerThread.join();
        }catch(InterruptedException ignored){};
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
            
            while (state.getState() !=State.terminated) {
                Integer[] keys = secretService.getIntel(); //get the keys to press

                int currentScore = score; //score before the AI makes a move

                for(int i = 0; i < keys.length & state.getState() == State.waitingForActivity ; i++){
                    // limit how fast the AI clicks buttons
                    try{synchronized(AIListener){AIListener.wait(generateAIWaitTime());}
                    } catch(InterruptedException ignored){}
                    keyPressed_AI(keys[i]);
                }

                //if the player is waiting, gather intel
                while(state.getState() == State.waitingForClaimResult | state.getState() == State.turningInClaim){
                    try{synchronized(AIListener){AIListener.wait(secretService.WAIT_BETWEEN_INTELLIGENCE_GATHERING);}
                    } catch(InterruptedException ignored){}
                    secretService.gatherIntel();
                }

                //if the game does not need to be paused, report the claim
                if(state.getState() != State.pausingExecution & state.getState() !=State.paused){
                    if (currentScore < score)
                        secretService.reportSetClaimed(keys);
                    else secretService.sendIntel(keys,false); 
                }

                //if the player is frozen, gather intel
                while(state.getState() == State.frozen){
                    try{synchronized(AIListener){AIListener.wait(secretService.WAIT_BETWEEN_INTELLIGENCE_GATHERING);}
                    }catch(InterruptedException ignored){}
                    secretService.gatherIntel();
                }

                //if the game needs to be paused, wait until it is unpaused
                if(state.getState() == State.pausingExecution | state.getState() == State.paused){
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

    //===========================================================
    //                  utility methods
    //===========================================================

    private void keyPressed_AI(int slot) {
        if(state.getState() == State.waitingForActivity)
            clickQueue.add(slot);
        synchronized(activityListener){activityListener.notifyAll();}    
    }

    

    private void clearClaimNotificationQueue() {
        while(claimQueue.isEmpty() == false){
            claimQueue.remove();
        }
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

     /**
     * Clears the pending clicks queue
     */
    private void clearClickQueue() {
        while(clickQueue.isEmpty() == false){
            clickQueue.remove();
        }
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
            System.out.println("AIRunning: "+AIRunning);
            System.out.println("aiThread.getState(): "+aiThread.getState());
            System.out.println("================================");
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

    public int incrementAndGetScore() {
        return ++score;
    }

    public State getState() {
        return state.stateName();
    }
    public void setState(State state) {
        this.state = playerStates[state.ordinal()];
    }

    private int generateAIWaitTime() {
        return (int)(Math.random()*
        (secretService.AI_WAIT_BETWEEN_KEY_PRESSES*(3.0/2.0) - secretService.AI_WAIT_BETWEEN_KEY_PRESSES/2.0)+
         secretService.AI_WAIT_BETWEEN_KEY_PRESSES/2.0);
    }

    public long getFreezeUntil() {
        return freezeUntil;
    }

    public void setFreezeRemainder(long remainder) {
        this.freezeRemainder = remainder;
    }

    public long getFreezeRemainder() {
        return freezeRemainder;
    }

    public Env getEnv() {
        return null;
    }

    public Table getTable() {
        return null;
    }

    public LinkedList<Integer> getPlacedTokens() {
        return null;
    }

    public Dealer getDealer() {
        return null;
    }

    public ConcurrentLinkedQueue<Claim> getClaimQueue() {
        return null;
    }

    public Semaphore getClaimQueueAccess() {
        return null;
    }

    public Object getActivityListener() {
        return null;
    }
   
}
