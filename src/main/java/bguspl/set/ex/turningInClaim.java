package bguspl.set.ex;

import java.util.LinkedList;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;

import bguspl.set.Env;
import bguspl.set.ex.Player.State;

public class turningInClaim implements PlayerState{
    
    private static final int CLICK_TIME_PADDING = 100;

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
     * The game's dealer
     */
    private Dealer dealer;

    /**
     * The claim queue.
     * @important needs to accessed using claimQueueAccess semaphore
     */
    private volatile ConcurrentLinkedQueue<Claim> claimQueue;

    /**
     * The semaphore used to control access to the click queue.
     */
    private Semaphore claimQueueAccess;

    private Player player;

    public turningInClaim(Env env, Table table, LinkedList<Integer> placedTokens, Dealer dealer,
            ConcurrentLinkedQueue<Claim> claimQueue, Semaphore claimQueueAccess, Player player) {
        this.env = env;
        this.table = table;
        this.placedTokens = placedTokens;
        this.dealer = dealer;
        this.claimQueue = claimQueue;
        this.claimQueueAccess = claimQueueAccess;
        this.player = player;
    }

    @Override
    public void run() {

        Integer[] array = placedTokens.stream().toArray(Integer[]::new);
        while(placedTokens.size() == Dealer.SET_SIZE & player.getState() == State.turningInClaim ){
            if(ClaimSet(array) == false) {     
                if(claimQueue.isEmpty() == false){
                    handleNotifiedClaim();
                    if(checkState() == false) return;    
                }

                //sleep for a short random time and try again
                try{
                    Thread.sleep((long)(Math.random()*(25-10)+10));
                }catch(InterruptedException ignored){}

            } else if(checkState()) changeToState(State.waitingForClaimResult);
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
        return dealer.claimSet(array, player,version);     
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
        if(cardsRemoved & checkState()){
            changeToState(State.waitingForActivity);
        }

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
        if(env.config.pointFreezeMillis > 0 & checkState()) changeToState(State.frozen);
        else if(checkState()) changeToState(State.waitingForActivity);
    }

    /**
     * Penalize a player and perform other related actions.
     */
    public void penalty() {
        if(env.config.penaltyFreezeMillis > 0 & checkState()) changeToState(State.frozen);
        else if(checkState()) changeToState(State.waitingForActivity);
    }

    void clearPlacedToken(Integer slot) {
        table.removeToken(player.id, slot);
        placedTokens.remove(slot);
    }
    /**
     * Clears the queue of tokens placed.
     * Updates the UI to remove the tokens.
     * @post - the queue of tokens placed is cleared.
     */
    void clearAllPlacedTokens(){
        while(placedTokens.isEmpty() == false){
            Integer token = placedTokens.peekFirst();
            table.removeToken(player.id, token);
            placedTokens.removeFirst();
        }    
    }

    @Override
    public State getState() {
        return State.turningInClaim;
    }
    
    private void changeToState(State state) {
        player.setState(state);
    }
    private boolean checkState() {
        return player.getState() == State.turningInClaim;
    }
}
