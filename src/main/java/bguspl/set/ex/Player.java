package bguspl.set.ex;
import java.util.LinkedList;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;

import bguspl.set.ex.PlayerStates.*;
import bguspl.set.Env;

/**
 * This class manages the players' threads and data
 *
 * @inv id >= 0
 * @inv score >= 0
 */
public class Player implements Runnable {

    /**
     *
     */
    private static final int AI_WAIT_FOR_PLAYER_TO_FINISH_CLAIMSET = 1;

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
     * The claim queue.
     * @important needs to accessed using claimQueueAccess semaphore
     */
    private volatile ConcurrentLinkedQueue<Claim> claimQueue;

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

    /**
     * All possible player states
     */
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

        if(!human)AIRunning = true;

        placedTokens = new LinkedList<>();
        clickQueue = new ConcurrentLinkedQueue<>();
        claimQueue = new ConcurrentLinkedQueue<>();
        executionListener = new Object();
        activityListener = new Object();
        claimListener = new Object();
        AIListener = new Object();
        claimQueueAccess = new Semaphore(1,true);

        playerStates = new PlayerState[7];
        playerStates[0] = new WaitingForActivity(this);
        playerStates[1] = new TurningInClaim(this);
        playerStates[2] = new WaitingForClaimResult(this);
        playerStates[3] = new Frozen(this);
        playerStates[4] = new PausingExecution(this);
        playerStates[5] = new Paused(this);
        playerStates[6] = new Terminated();

        setState(State.paused);
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

            // This runs the current state's main method.
            // When the state is changed, the state's main method will return here
            // and the new state's main method will be called.
            // we use the states design pattern in order to control the flow of the player's thread
            state.run();
        }

        if (!human) try { aiThread.join(); } catch (InterruptedException ignored) {}
        System.out.printf("Info: Thread %s terminated.%n", Thread.currentThread().getName());       
    }

    /**
     * Creates an additional thread for an AI (computer) player. 
     * The main loop of this thread repeatedly generates key presses. 
     * and does other things while the player thread is busy.
     */
    private void createArtificialIntelligence() {
        // note: this is a very secretive AI.... SHHH!
        aiThread = new Thread(()->{
            System.out.printf("Info: Thread %s starting.%n", Thread.currentThread().getName());
            aiThread = Thread.currentThread();

            //wait until the game starts
            try{
                AIRunning = false;
                synchronized(executionListener){
                    executionListener.wait();
                }
            }catch(InterruptedException ignored){}

            while(getState() != State.waitingForActivity){
                try{Thread.sleep(10);}catch(InterruptedException ignored){}
            }

            AIRunning = true; //AI is now running
            while (getState() != State.terminated) {

                
                //wait until the player thread is waiting for activity and ready to accept key presses
                while(getState() != State.waitingForActivity & getState() != State.pausingExecution & getState() != State.paused){
                    try{
                        synchronized(AIListener){
                            AIListener.wait(AI_WAIT_FOR_PLAYER_TO_FINISH_CLAIMSET);
                        }
                    }catch(InterruptedException ignored){}
                }
            
                //press the keys
                while(getState() != State.pausingExecution & getState() != State.paused){
                    keyPressed_AI(generateKeyPress());
                }

                //if the game needs to be paused, wait until it is unpaused
                if(getState() == State.pausingExecution | getState() == State.paused){
                    try{
                        AIRunning = false; //AI is not running while game is paused
                        synchronized(executionListener){
                            executionListener.wait();
                        }
                    }catch(InterruptedException ignored){}
                    if(getState() != State.paused) AIRunning = true;
                }        
            }
            System.out.printf("Info: Thread %s terminated.%n", Thread.currentThread().getName());


            
        }, "computer-" + id);
        aiThread.start();
    }

    //===========================================================
    //                      Main methods
    //===========================================================

    /**
     * Called when the dealer wants to notify the player of a new claim.
     * If the player is waiting for a claim or waiting for key presses,
     * the claim is added to the claim queue.
     * @param claim
     */
    public void notifyClaim(Claim claim){

        State state = getState();


        // we allow the player to receive a claim only if he is waiting for a claim or waiting for key presses
        if(state == State.waitingForActivity | state == State.waitingForClaimResult | state == State.turningInClaim){
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

        // here we want to push the player thread into a waiting state
        // so we are gonna wake him up from every possible waiting state

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

    /**
     * Resumes the player's ability to interact with the game
     */
    public void resume(){
        while(AIRunning != false | getState() != State.paused){
            try{Thread.sleep(10);}catch(InterruptedException ignored){}
        }
        synchronized(executionListener){executionListener.notifyAll();}
    }

    /**
     * This method is called when a key is pressed.
     *
     * @param slot - the slot corresponding to the key pressed.
     */
    public void keyPressed(int slot) {
        if(human){
            if(getState() == State.waitingForActivity){
                clickQueue.add(slot);
                synchronized(activityListener){activityListener.notifyAll();}
            }
        }       
    }

    /**
     * This method is called when a key is pressed.
     * This method is called by the AI thread.
     *
     * @param slot - the slot corresponding to the key pressed.
     */
    private void keyPressed_AI(int slot) {
        if(getState() == State.waitingForActivity){
            clickQueue.add(slot);
            synchronized(activityListener){activityListener.notifyAll();}    
        }
    }

    private int generateKeyPress(){
        Random rand = new Random();
        return rand.nextInt(env.config.tableSize);
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

    //===========================================================
    //                  Getters / Setters
    //===========================================================
    
    public int getScore() {return score;}
    public int incrementAndGetScore() {return ++score;}
    public State getState() {return state.stateName();}
    public void setState(State state) {this.state = playerStates[state.ordinal()];}
    public void setFreezeRemainder(long remainder) {this.freezeRemainder = remainder;}
    public long getFreezeRemainder() {return freezeRemainder;}
    public Env getEnv() {return env;}
    public Table getTable() {return table;}
    public LinkedList<Integer> getPlacedTokens() {return placedTokens;}
    public Dealer getDealer() {return dealer;}
    public ConcurrentLinkedQueue<Claim> getClaimQueue() {return claimQueue;}
    public Semaphore getClaimQueueAccess() {return claimQueueAccess;}
    public Object getActivityListener() {return activityListener;}
    public Object getExecutionListener() {return executionListener;}
    public Object getClaimListener() {return claimListener;}
    public ConcurrentLinkedQueue<Integer> getClickQueue() {return clickQueue;}

     //===========================================================
    //                  debugging methods
    //===========================================================

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
            if(aiThread != null) System.out.println("aiThread.getState(): "+aiThread.getState());
            System.out.println("================================");
    }

    /**
     * used to nudge the player thread out of a wait state.
     * Used for debugging.
     */
    public void nudge(){
        synchronized(executionListener){executionListener.notifyAll();}
        synchronized(activityListener){activityListener.notifyAll();}
        synchronized(claimListener){claimListener.notifyAll();}
    }
}
