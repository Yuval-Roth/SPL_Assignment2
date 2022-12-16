package bguspl.set.ex;

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
            while(insertState == false & tries <=5 & stillThisState()){
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
    public State stateName() {
        return State.waitingForActivity;
    }
}
