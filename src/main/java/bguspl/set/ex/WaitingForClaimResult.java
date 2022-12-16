package bguspl.set.ex;

import bguspl.set.ex.Player.State;

public class WaitingForClaimResult extends PlayerState {

    private volatile Object claimListener;

    public WaitingForClaimResult(Player player) {
        super(player);
        claimListener = player.getClaimListener();
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
