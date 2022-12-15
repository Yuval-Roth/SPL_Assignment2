package bguspl.set.ex;

import java.util.LinkedList;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;

import bguspl.set.Env;
import bguspl.set.ex.Player.State;

public class WaitingForClaimResult implements PlayerState {

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
    private Player player;



    @Override
    public void run() {
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
        if(tries >= 10 & checkState()){
            changeToState(State.turningInClaim);
        }
        
    }

    @Override
    public State getState() {
        return State.waitingForClaimResult;
    }

    private void changeToState(State state) {
        player.setState(state);
    }

    private boolean checkState() {
        return player.getState() == State.waitingForClaimResult;
    }

    
    
}
