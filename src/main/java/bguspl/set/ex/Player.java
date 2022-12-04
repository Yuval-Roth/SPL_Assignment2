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
    private static final int AI_WAIT_BETWEEN_KEY_PRESSES = 100;

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
            while (!terminatePlayer) {} // wait for interrupt from the dealer
            if (!human) try { aiThread.join(); } catch (InterruptedException ignored) {}
            System.out.printf("Info: Thread %s terminated.%n", Thread.currentThread().getName());       
    }

    /*
     * Updates the UI timer if the player is frozen
     */
    private void updateTimerDisplay() { 
        env.ui.setFreeze(id,timerStopTime-System.currentTimeMillis());   
    }
    private void stopTimer() {     
        stopTimer = true;
    }

    /**
     * Creates an additional thread for an AI (computer) player. 
     * The main loop of this thread repeatedly generates key presses. 
     * If the queue of key presses is full, the thread waits until it is not full.
     */
    private void createArtificialIntelligence() {
        // note: this is a very very basic AI (!)
        while(aiThread != null && aiThread.getState() != Thread.State.TERMINATED){}
        aiThread = new Thread(() -> {
            aiThread = Thread.currentThread();
            terminateAI = false;
            System.out.printf("Info: Thread %s starting.%n", Thread.currentThread().getName());
            while (!terminateAI) {
                while(placedTokens.size() < SET_SIZE & !terminateAI){
                    keyPressed(generateKeyPress());
                    // limit how fast the AI clicks buttons
                    try{synchronized(this){
                        wait(AI_WAIT_BETWEEN_KEY_PRESSES);}
                    } catch(InterruptedException ignored){}
                }
                // if(terminateAI) break;
                // waitForClaimSet();
            }
            System.out.printf("Info: Thread %s terminated.%n", Thread.currentThread().getName());
        }, "computer-" + id);
        aiThread.start();
    }

    /**
     * Called when the game should be terminated due to an external event.
     * Interrupts the player thread and the AI thread (if any).
     * Clears the queue of tokens placed.
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
        clearPlacedTokens(); // clear the queue of tokens placed, because the table was also cleared
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
     * @post - the player's score is increased by 1.
     * @post - the player's score is updated in the ui.
     */
    public void point() {
        env.ui.setScore(id, ++score);
        startTimer(env.config.pointFreezeMillis);
        try{
            Thread.sleep(env.config.pointFreezeMillis);
        } catch(InterruptedException ignored){}
    }

    /**
     * Penalize a player and perform other related actions.
     */
    public void penalty() {
        startTimer(env.config.penaltyFreezeMillis);
        try{
            Thread.sleep(env.config.penaltyFreezeMillis);
        }catch(InterruptedException ignored){}
    }


    /**
     * Starts a freeze time thread and updates the UI timer
     * @param timeToStop - future time to stop in milliseconds
     * @pre - the freeze timer is stopped
     * @post - the freeze timer is started
     * @post - the UI timer is updated
     */
    private void startTimer(long timeToStop) {
        freezeTimer = new Thread(()->{
            timerStopTime = System.currentTimeMillis()+ timeToStop;
            stopTimer = false;
            while(stopTimer == false & timerStopTime >= System.currentTimeMillis() ){
                updateTimerDisplay();
                try{
                    Thread.sleep(CLOCK_UPDATE_INTERVAL);
                } catch (InterruptedException ignored){}
            }
            env.ui.setFreeze(id,0);
            if(human == false) aiThread.interrupt();
            else playerThread.interrupt();
        },"Freeze timer for player "+id);
        freezeTimer.start();
    }

    /**
     * @return the player's score.
     */
    public int getScore() {
        return score;
    }

    /*
     * If a token is placed in the given slot, remove it.
     * Otherwise, place a token in the given slot.
     * Placing or removing a token sends a message to the table.
     * Claims a set if the player has placed a full set.
     * @post - the token is placed or removed from the given slot.
     */
    private void placeOrRemoveToken(Integer slot){
        
        if(placedTokens.contains(slot) == false){
            table.placeToken(id, slot);
            placedTokens.addLast(slot);
            if(placedTokens.size() == SET_SIZE) ClaimSet();
        }
        else{
            table.removeToken(id, slot);
            placedTokens.remove(slot);
        } 
    }

    /*
     
     * @pre - The player has a placedTokens list of size SET_SIZE.
     * Claims a set if the player has placed a full set.
     * @post - The dealer is notified about the set claim.
     */
    private void ClaimSet() {
        int version = dealer.getGameVersion();
        dealer.claimSet(placedTokens, this,version);
        clearPlacedTokens();
    }

    /*
     * Clears the queue of tokens placed.
     * Updates the UI to remove the tokens.
     * @post - the queue of tokens placed is cleared.
     */
    private void clearPlacedTokens(){
        while (placedTokens.isEmpty() == false){
            int token = placedTokens.pop();
        }
    }

    /**
     * @return a random key press of size tableSize.
     */
    private int generateKeyPress(){
        Random rand = new Random();
        return rand.nextInt(env.config.tableSize);
    }

    
}
