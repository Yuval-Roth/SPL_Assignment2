package bguspl.set.ex.PlayerStates;

import bguspl.set.ex.Claim;
import bguspl.set.ex.Dealer;
import bguspl.set.ex.Player;
import bguspl.set.ex.Player.State;

public class WaitingForActivity extends PlayerState {

    /**
     * Object for breaking wait() when waiting for general activity
     */
    private volatile Object activityListener;

    public WaitingForActivity(Player player) {
        super(player);
        activityListener = player.getActivityListener();
    }

    @Override
    public void run() {

        while(stillThisState()){

            try{
                //wait for a click
                synchronized(activityListener){
                    activityListener.wait();
                }
            }catch(InterruptedException ignored){}
            
            //if there is a click to be processed
            while(clickQueue.isEmpty() == false & stillThisState()){
                Integer key = clickQueue.remove();
                placeOrRemoveToken(key);
            }

            //if a claim was notified, handle it
            if(claimQueue.isEmpty() == false & stillThisState()){
                handleNotifiedClaim();         
            }
        }
    }

    /**
     * places or removes a token from the table.
     * 
     * @post - if the token was placed, it is added to the list of placed tokens.
     * @post - if the token was removed, it is removed from the list of placed tokens.
     * @post - if the player has placed enough tokens, the set is claimed.
     * @param slot - the slot to place or remove a token from.
     */
    private void placeOrRemoveToken(Integer slot){

        if(placedTokens.contains(slot) == false){

            // this limits the number of tokens that can be placed to the number of cards in a set
            if(placedTokens.size() == Dealer.SET_SIZE){
                return;
            }

            // if the token was placed, add it to the list of placed tokens.
            // if the player has placed enough tokens, claim the set.
            if(table.placeToken(player.id, slot)){
                placedTokens.addLast(slot);
                if(placedTokens.size() == Dealer.SET_SIZE) {
                    changeToState(State.turningInClaim);
                    clearClickQueue();
                } 
            }
        }
        else {
            // here we remove the token from the slot
            clearPlacedToken(slot);       
        }
    }
    
    /**
     * @pre - The claimQueue is not empty.
     * @post - The claimQueue is empty.
     * @post - The player has removed all cards that were already claimed by another player.
     *  and if so, changed to the waitingForActivity state if it was still in the turningInClaim state.
     */
    private void handleNotifiedClaim() {

        claimQueueAccess.acquireUninterruptibly();
        while(claimQueue.isEmpty() == false){
            Claim claim = claimQueue.remove();

            // in this state the only thing that can be notified is a claim
            // that a different player has made, thus no need to check if it is our claim
            for(Integer card : claim.cards){
                if(placedTokens.contains(card)){
                    clearPlacedToken(card);
                }
            }            
        }
        claimQueueAccess.release();
    }

    @Override
    public State stateName() {
        return State.waitingForActivity;
    }
}
