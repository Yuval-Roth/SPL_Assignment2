package bguspl.set.ex.PlayerStates;

import bguspl.set.ex.Claim;
import bguspl.set.ex.Dealer;
import bguspl.set.ex.Player;
import bguspl.set.ex.Player.State;

public class TurningInClaim extends PlayerState {
    
    private static final int MIN_RETRY_WAIT_TIME = 10;
    private static final int MAX_RETRY_WAIT_TIME = 100;

    private static final int CLICK_TIME_PADDING = 100;

    public TurningInClaim(Player player) {
        super(player);
    }
    
    @Override
    public void run() {

        Integer[] array = placedTokens.stream().toArray(Integer[]::new);
        while(placedTokens.size() == Dealer.SET_SIZE & player.getState() == State.turningInClaim ){
            if(ClaimSet(array) == false) {     
                if(claimQueue.isEmpty() == false){
                    handleNotifiedClaim();
                    if(stillThisState() == false) return;    
                }

                //sleep for a short random time and try again
                try{
                    Thread.sleep((long)(Math.random()*(MAX_RETRY_WAIT_TIME-MIN_RETRY_WAIT_TIME)+MIN_RETRY_WAIT_TIME));
                }catch(InterruptedException ignored){}

            } else if(stillThisState()) changeToState(State.waitingForClaimResult);
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

    /**
     * @pre - The claimQueue is not empty.
     * @post - The claimQueue is empty.
     * @post - The player has removed all cards that were already claimed by another player.
     *  and if so, changed to the waitingForActivity state if it was still in the turningInClaim state.
     */
    private void handleNotifiedClaim() {

        boolean cardsRemoved = false;
        claimQueueAccess.acquireUninterruptibly();
        while(claimQueue.isEmpty() == false){
            Claim claim = claimQueue.remove();

            // in this state the only thing that can be notified is a claim
            // that a different player has made, thus no need to check if it is our claim
            for(Integer card : claim.cards){
                if(placedTokens.contains(card)){
                    clearPlacedToken(card);
                    cardsRemoved = true;
                }
            }
        }
        claimQueueAccess.release();
        
        if(cardsRemoved & stillThisState()){
            changeToState(State.waitingForActivity);
        }
    }

    @Override
    public State stateName() {
        return State.turningInClaim;
    }
}
