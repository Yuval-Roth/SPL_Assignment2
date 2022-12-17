package bguspl.set.ex.PlayerStates;

import bguspl.set.ex.Claim;
import bguspl.set.ex.Dealer;
import bguspl.set.ex.Player;
import bguspl.set.ex.PlayerStates.PlayerState;
import bguspl.set.ex.Player.State;

public class TurningInClaim extends PlayerState {
    
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
                    Thread.sleep((long)(Math.random()*(25-10)+10));
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

    private void handleNotifiedClaim() {
        
        boolean cardsRemoved = false;
        claimQueueAccess.acquireUninterruptibly();
        while(claimQueue.isEmpty() == false){
            Claim claim = claimQueue.remove();
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
