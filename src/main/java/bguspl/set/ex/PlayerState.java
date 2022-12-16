package bguspl.set.ex;

import java.util.LinkedList;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;

import bguspl.set.Env;
import bguspl.set.ex.Player.State;

public abstract class PlayerState {  

    /**
    * The game environment object.
    */
    protected final Env env;

    /**
     * Game entities.
     */
    protected final Table table;

    /**
     * The player object
     */
    protected Player player;

        /**
     * The player's currently placed tokens.
     */
    protected LinkedList<Integer> placedTokens;
    
    /**
     * The game's dealer
     */
    protected Dealer dealer;

    /**
     * The claim queue.
     * @important needs to accessed using claimQueueAccess semaphore
     */
    protected volatile ConcurrentLinkedQueue<Claim> claimQueue;
    
    /**
     * The semaphore used to control access to the click queue.
     */
    protected Semaphore claimQueueAccess;

        /**
     * The clicks queue.
     */
    protected volatile ConcurrentLinkedQueue<Integer> clickQueue;

    /**
     *  initializes everything to null
     */
    protected PlayerState(){
        this.player = null;
        this.env = null;
        this.table = null;
        this.placedTokens = null;
        this.dealer = null;
        this.claimQueue = null;
        this.claimQueueAccess = null;
    }

    /**
     * main constructor for PlayerState objects
     */
    protected PlayerState(Player player) {
        this.player = player;
        this.env = player.getEnv();
        this.table = player.getTable();
        this.placedTokens = player.getPlacedTokens();
        this.dealer = player.getDealer();
        this.claimQueue = player.getClaimQueue();
        this.claimQueueAccess = player.getClaimQueueAccess();
    }

    public abstract void run();
    public abstract Player.State stateName();

    /**
     * Changes the player's state.
     * @param state
     */
    protected void changeToState(Player.State state) {
        player.setState(state);
    }

    /**
     * @return - true if the player is still in the current state.
     */
    protected boolean stillThisState() {
        return getState() == stateName();
    }

    /**
     * @return - the current state of the player.
     */
    protected State getState() {
        return player.getState();
    }

    /**
     * Clears a placed token.
     * Updates the UI to remove the token.
     * @param slot - the slot of the token to be removed.
     * @post - the token is removed from the queue of tokens placed.
     */
    protected void clearPlacedToken(Integer slot) {
        table.removeToken(player.id, slot);
        placedTokens.remove(slot);
    }

    /**
     * Clears the queue of tokens placed.
     * Updates the UI to remove the tokens.
     * @post - the queue of tokens placed is cleared.
     */
    protected void clearAllPlacedTokens(){
        while(placedTokens.isEmpty() == false){
            Integer token = placedTokens.peekFirst();
            table.removeToken(player.id, token);
            placedTokens.removeFirst();
        }    
    }

    /**
     * Clears the click queue.
     * @post - the click queue is cleared.
     */
    protected void clearClickQueue() {
        while(clickQueue.isEmpty() == false){
            clickQueue.remove();
        }
    }
}
