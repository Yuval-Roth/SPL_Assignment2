package bguspl.set.ex;

import java.util.LinkedList;
import java.util.Random;

import bguspl.set.Env;
import jdk.nashorn.internal.runtime.regexp.joni.Config;

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
    private static final int SET_SIZE = 3;

    /**
     *
     */
    private static final int CLOCK_UPDATE_INTERVAL = 900;

    /**
     *
     */
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
     * True iff game should be terminated due to an external event.
     */
    private volatile Boolean terminatePlayer;

    /**
     * True iff game should be terminated due to an external event.
     */
    private volatile Boolean terminateAI;

    /**
     * The current score of the player.
     */
    private int score;

    /**
     * The game's dealer
     */
    private Dealer dealer;

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
    }

    private Thread freezeTimer;
    private volatile boolean stopTimer;
    private volatile long timerStopTime;
    /**
     * The main player thread of each player starts here (main loop for the player thread).
     */
    @Override
    public void run() {
            terminatePlayer = false;
            playerThread = Thread.currentThread();
            System.out.printf("Info: Thread %s starting.%n", Thread.currentThread().getName());
            if (!human) createArtificialIntelligence();
            while (!terminatePlayer) {
                
            }
            if (!human) try { aiThread.join(); } catch (InterruptedException ignored) {}
            System.out.printf("Info: Thread %s terminated.%n", Thread.currentThread().getName());       
    }

    private void updateTimerDisplay() { 
        env.ui.setFreeze(id,timerStopTime-System.currentTimeMillis());   
    }
    private void stopTimer() {     
        stopTimer = true;
    }

    /**
     * Creates an additional thread for an AI (computer) player. The main loop of this thread repeatedly generates
     * key presses. If the queue of key presses is full, the thread waits until it is not full.
     */
    private void createArtificialIntelligence() {
        // note: this is a very very smart AI (!)
        while(aiThread != null && aiThread.getState() != Thread.State.TERMINATED){}
        aiThread = new Thread(() -> {
            aiThread = Thread.currentThread();
            terminateAI = false;
            System.out.printf("Info: Thread %s starting.%n", Thread.currentThread().getName());
            while (!terminateAI) {
                while(placedTokens.size() < 3 & !terminateAI){
                    keyPressed(generateKeyPress());


                    // limit how fast the AI clicks buttons
                    try{synchronized(this){wait(AI_WAIT_BETWEEN_KEY_PRESSES);}}catch(InterruptedException ignored){}
                }
                // if(terminateAI) break;
                // waitForClaimSet();
            }
            System.out.printf("Info: Thread %s terminated.%n", Thread.currentThread().getName());
        }, "computer-" + id);
        aiThread.start();
    }

    private void waitForClaimSet() {
        //This is a freeze that unlocks after claimSet() is complete
        try {
            // env.ui.setFreeze(id,Long.MAX_VALUE);
            synchronized (this){
                wait();
            }
            // env.ui.setFreeze(id,0);
        } catch (InterruptedException ignored) {
            System.out.println("AI interrupted");
        }
    }

    /**
     * Called when the game should be terminated due to an external event.
     */
    public void terminate() {
        stopTimer();
        terminatePlayer = true;
        terminateAI = true;
        if(human == false){
            aiThread.interrupt();
            try{
                aiThread.join();
            }catch(InterruptedException ignored){};
        } 
        try{
            playerThread.interrupt();
            playerThread.join();
        }catch(InterruptedException ignored){};
        clearPlacedTokens();
    }

    /**
     * This method is called when a key is pressed.
     *
     * @param slot - the slot corresponding to the key pressed.
     */
    public void keyPressed(int slot) {
        placeOrRemoveToken(slot);
    }

    /**
     * Award a point to a player and perform other related actions.
     *
     * @post - the player's score is increased by 1.
     * @post - the player's score is updated in the ui.
     */
    public void point() {

        // int ignored = table.countCards(); // this part is just for demonstration in the unit tests
        env.ui.setScore(id, ++score);
        clearPlacedTokens();
        timerStopTime = System.currentTimeMillis()+ env.config.pointFreezeMillis;
        startTimer();
        // try{
        //     if(human) synchronized(this){wait();}
        // } catch(InterruptedException ignored){}
        // stopTimer();

        //at this point, aiThread is in wait() and needs to be interrupted to keep running
        // if(human == false) aiThread.interrupt();
    }

    /**
     * Penalize a player and perform other related actions.
     */
    public void penalty() {
        clearPlacedTokens();
        timerStopTime = System.currentTimeMillis()+ env.config.penaltyFreezeMillis;
        startTimer();
        try{
            Thread.sleep(env.config.penaltyFreezeMillis);
        }catch(InterruptedException ignored){}
        //at this point, aiThread is in wait() and needs to be interrupted to keep running
    }

    private void startTimer() {
        freezeTimer = new Thread(()->{
            stopTimer = false;
            while(stopTimer == false & timerStopTime >= System.currentTimeMillis() ){
                updateTimerDisplay();
                try{
                    Thread.sleep(CLOCK_UPDATE_INTERVAL);
                } catch (InterruptedException ignored){}
            }
            env.ui.setFreeze(id,0);
            // if(human == false) aiThread.interrupt();
            // else playerThread.interrupt();
        });
        // freezeTimer.setPriority(Thread.MAX_PRIORITY); 
        freezeTimer.start();
    }

    public int getScore() {
        return score;
    }
    private void placeOrRemoveToken(Integer tokenValue){
        
        if(placedTokens.contains(tokenValue) == false){
            table.placeToken(id, tokenValue);
            placedTokens.addLast(tokenValue);
            if(placedTokens.size() == SET_SIZE) ClaimSet();
        }
        else{
            table.removeToken(id, tokenValue);
            placedTokens.remove(tokenValue);
        } 
    }

    private void ClaimSet() {
        int version = dealer.getGameVersion();
        dealer.claimSet(placedTokens, this,version);
    }

    private void clearPlacedTokens(){
        while (placedTokens.isEmpty() == false){
            int token = placedTokens.pop();
            env.ui.removeToken(id, token);
        }
    }
    private int generateKeyPress(){
        Random rand = new Random();
        return rand.nextInt(env.config.tableSize);
    }

    
}
