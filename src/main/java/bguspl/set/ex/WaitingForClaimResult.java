package bguspl.set.ex;

import java.util.LinkedList;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;

import bguspl.set.Env;
import bguspl.set.ex.Player.State;

public class WaitingForClaimResult extends PlayerState {

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
     * The claim queue.
     * @important needs to accessed using claimQueueAccess semaphore
     */
    private volatile ConcurrentLinkedQueue<Claim> claimQueue;

    /**
     * Object for breaking wait() when waiting for claim result
     */
    private volatile Object claimListener;

    /**
     * The semaphore used to control access to the click queue.
     */
    private Semaphore claimQueueAccess;

    private Player player;

    public WaitingForClaimResult(Env env, Table table, LinkedList<Integer> placedTokens,
            ConcurrentLinkedQueue<Claim> claimQueue, Object claimListener, Semaphore claimQueueAccess, Player player) {
        super(player);
        this.env = env;
        this.table = table;
        this.placedTokens = placedTokens;
        this.claimQueue = claimQueue;
        this.claimListener = claimListener;
        this.claimQueueAccess = claimQueueAccess;
    }

    @Override
    public void run() {
        //number of tries to wait for claim result
        int tries = 0;
        while(stillThisState() & tries < 10){  
            try{
                //wait for claim result
                synchronized(claimListener){claimListener.wait(generateWaitingTime());}
            }catch(InterruptedException ignored){} 

            //if a claim was notified, handle it
            if(claimQueue.isEmpty() == false & stillThisState()){
                handleNotifiedClaim();
            }else  tries++; //if no claim was notified, increment tries
        }

        //disaster recovery if claim result was not notified
        if(tries >= 10 & stillThisState()){
            changeToState(State.turningInClaim);
        }
        
    }

    private void handleNotifiedClaim() {

        int action = 0;
        boolean cardsRemoved = false;
        claimQueueAccess.acquireUninterruptibly();
        while(claimQueue.isEmpty() == false){
            Claim claim = claimQueue.remove();
            if(claim.claimer == player){
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
        if(cardsRemoved & stillThisState()) changeToState(State.waitingForActivity);
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
     * Award a point to a player and perform other related actions.
     * @post - the player's score is increased by 1.
     * @post - the player's score is updated in the ui.
     */
    public void point() {
        env.ui.setScore(player.id, player.incrementAndGetScore());
        if(env.config.pointFreezeMillis > 0 & stillThisState()) changeToState(State.frozen);
        else if(stillThisState()) changeToState(State.waitingForActivity);
    }

    /**
     * Penalize a player and perform other related actions.
     */
    public void penalty() {
        if(env.config.penaltyFreezeMillis > 0 & stillThisState()) changeToState(State.frozen);
        else if(stillThisState()) changeToState(State.waitingForActivity);
    }

    /**
     * Clears the queue of tokens placed.
     * Updates the UI to remove the tokens.
     * @post - the queue of tokens placed is cleared.
     */
    private void clearAllPlacedTokens(){
        while(placedTokens.isEmpty() == false){
            Integer token = placedTokens.peekFirst();
            table.removeToken(player.id, token);
            placedTokens.removeFirst();
        }    
    }

    private void clearPlacedToken(Integer slot) {
        table.removeToken(player.id, slot);
        placedTokens.remove(slot);
    }

    private long generateWaitingTime() {  
        if(stillThisState()){
            if(claimQueue.isEmpty() == false) return 1;
            else return 100;
        }else return 1;
    }

    @Override
    public State stateName() {
        return State.waitingForClaimResult;
    }
}
