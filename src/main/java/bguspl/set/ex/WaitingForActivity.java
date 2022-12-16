package bguspl.set.ex;

import java.util.LinkedList;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;

import bguspl.set.Env;
import bguspl.set.ex.Player.State;

public class WaitingForActivity extends PlayerState {
    
    /**
     * Game entities.
     */
    private final Table table;

    /**
     * The player's currently placed tokens.
     */
    private LinkedList<Integer> placedTokens;
    
    /**
     * The clicks queue.
     */
    private volatile ConcurrentLinkedQueue<Integer> clickQueue;

    /**
     * Object for breaking wait() when waiting for general activity
     */
    private volatile Object activityListener;

        /**
     * The semaphore used to control access to the click queue.
     */
    private Semaphore claimQueueAccess;

        /**
     * The claim queue.
     * @important needs to accessed using claimQueueAccess semaphore
     */
    private volatile ConcurrentLinkedQueue<Claim> claimQueue;

    

    public WaitingForActivity(Player player, Table table, LinkedList<Integer> placedTokens,
                ConcurrentLinkedQueue<Integer> clickQueue, Object activityListener, Semaphore claimQueueAccess,
                ConcurrentLinkedQueue<Claim> claimQueue) {
            super(player);
            this.table = table;
            this.placedTokens = placedTokens;
            this.clickQueue = clickQueue;
            this.activityListener = activityListener;
            this.claimQueueAccess = claimQueueAccess;
            this.claimQueue = claimQueue;
        }

    @Override
    public void run() {

        while(checkState()){

            try{
                //wait for a click
                synchronized(activityListener){
                    activityListener.wait();
                }
            }catch(InterruptedException ignored){}

            //if a claim was notified, handle it
            if(claimQueue.isEmpty() == false & checkState()){
                handleNotifiedClaim();         
            }

            //if there is a click to be processed
            while(clickQueue.isEmpty() == false & checkState()){
                Integer key = clickQueue.remove();
                placeOrRemoveToken(key);
            }
        }
    }

    /**
     * If a token is placed in the given slot, remove it.
     * Otherwise, place a token in the given slot.
     * Placing or removing a token sends a message to the table.
     * Claims a set if the player has placed a full set.
     * @post - the token is placed or removed from the given slot.
     */
    private void placeOrRemoveToken(Integer slot){

        if(placedTokens.contains(slot) == false){
            boolean insertState = false;
            int tries = 0;
            while(insertState == false & tries <=5 & checkState()){
                insertState = table.placeToken(player.id, slot);
                tries++;
                try{Thread.sleep(10);}catch(InterruptedException ignored){}
            }
            if(insertState){
                placedTokens.addLast(slot);
                if(placedTokens.size() == Dealer.SET_SIZE) {
                    changeToState(State.turningInClaim);
                    clearClickQueue();
                } 
            }
        }
        else {
            clearPlacedToken(slot);       
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
    
    private void clearPlacedToken(Integer slot) {
        table.removeToken(player.id, slot);
        placedTokens.remove(slot);
    }

    private void handleNotifiedClaim() {

        claimQueueAccess.acquireUninterruptibly();
        while(claimQueue.isEmpty() == false){
            Claim claim = claimQueue.remove();
            for(Integer card : claim.cards){
                if(placedTokens.contains(card)){
                    clearPlacedToken(card);
                }
            }            
        }
        claimQueueAccess.release();
    }

    @Override
    public State getState() {
        return State.waitingForActivity;
    }
}
