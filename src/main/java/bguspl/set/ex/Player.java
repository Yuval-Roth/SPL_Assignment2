package bguspl.set.ex;

import java.util.Deque;
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
    private Deque<Integer> placedTokens;

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
    private volatile boolean terminate;

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
        placedTokens = new LinkedList<Integer>();
    }

    /**
     * The main player thread of each player starts here (main loop for the player thread).
     */
    @Override
    public void run() {
            terminate = false;
            playerThread = Thread.currentThread();
            System.out.printf("Info: Thread %s starting.%n", Thread.currentThread().getName());
            if (!human) createArtificialIntelligence();
            while (!terminate) {
                // maybe code will go in here in the future
            }
            if (!human) try { aiThread.join(); } catch (InterruptedException ignored) {}
            System.out.printf("Info: Thread %s terminated.%n", Thread.currentThread().getName());
        
        
        
    }

    /**
     * Creates an additional thread for an AI (computer) player. The main loop of this thread repeatedly generates
     * key presses. If the queue of key presses is full, the thread waits until it is not full.
     */
    private void createArtificialIntelligence() {
        // note: this is a very very smart AI (!)
        aiThread = new Thread(() -> {
            System.out.printf("Info: Thread %s starting.%n", Thread.currentThread().getName());
            while (!terminate) {
                while(placedTokens.size() < 3){
                    keyPressed(generateKeyPress());
                }
                try {
                    synchronized (this) { wait(); }
                } catch (InterruptedException ignored) {}
            }
            System.out.printf("Info: Thread %s terminated.%n", Thread.currentThread().getName());
        }, "computer-" + id);
        aiThread.start();
    }

    /**
     * Called when the game should be terminated due to an external event.
     */
    public void terminate() {
        terminate = true;
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
        try{
            synchronized(this){Thread.sleep(env.config.pointFreezeMillis);}
        } catch(InterruptedException ignored){}

        //at this point, aiThread is in wait() and needs to be interrupted to keep running
        if(human == false) aiThread.interrupt();
    }

    /**
     * Penalize a player and perform other related actions.
     */
    public void penalty() {
        clearPlacedTokens();
        try{
            synchronized(this){Thread.sleep(env.config.pointFreezeMillis);}
        } catch(InterruptedException ignored){}

        //at this point, aiThread is in wait() and needs to be interrupted to keep running
        if(human == false) aiThread.interrupt();
    }

    public int getScore() {
        return score;
    }
    private void placeOrRemoveToken(int tokenValue){
        
        if(placedTokens.contains(tokenValue) == false){
            placedTokens.addLast(tokenValue);
            table.placeToken(id, tokenValue);
            if(placedTokens.size() == 3) ClaimSet();
        }
        else{
            placedTokens.remove(tokenValue);
            table.removeToken(id, tokenValue);
        } 
    }

    private void ClaimSet() {
        dealer.claimSet(placedTokens, this);
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
