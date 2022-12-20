package bguspl.set.ex.PlayerStates;

import bguspl.set.ex.Claim;
import bguspl.set.ex.Player;
import bguspl.set.ex.Player.State;

public class WaitingForClaimResult extends PlayerState {

    private static final int WAIT_FOR_CLAIM_MAX_TRIES = 10;
    private volatile Object claimListener;

    public WaitingForClaimResult(Player player) {
        super(player);
        claimListener = player.getClaimListener();
    }

    /**
     * Waits for a claim result to be notified to the player.
     */
    @Override
    public void run() {
        //number of tries to wait for claim result
        int tries = 0;
        while(stillThisState() & tries < WAIT_FOR_CLAIM_MAX_TRIES){  
            try{
                //wait for claim result
                synchronized(claimListener){claimListener.wait(generateWaitingTime());}
            }catch(InterruptedException ignored){} 

            //if a claim was notified, handle it
            if(claimQueue.isEmpty() == false & stillThisState()){
                handleNotifiedClaim();
            }else  tries++; //if no claim was notified, increment tries
        }

        // disaster recovery if claim result got lost due to a limitation in java's concurrent data structures.
        // this is a very rare case, but it can happen.
        // this just makes sure that the player does not get stuck in this state.
        // realistically, it just sends the player back to the previous state to try again.
        if(tries >= WAIT_FOR_CLAIM_MAX_TRIES & stillThisState()){
            changeToState(State.turningInClaim);
        }   
    }

    /**
     * Handle a claim that was notified to the player.
     * @post - the player's score is increased by 1 if the claim was valid.
     * @post - the player's state is changed to frozen if the claim was the player's.
     * @post - the player's state is changed to waitingForActivity if some of the player's placed tokens were cleared.
     */
    private void handleNotifiedClaim() {

        // this variable is used to store the action that should be performed after the claim is handled
        // the base value is 0, which means no action should be performed
        int action = 0;

        boolean cardsRemoved = false;
        claimQueueAccess.acquireUninterruptibly();
        while(claimQueue.isEmpty() == false){
            Claim claim = claimQueue.remove();

            // this part is for the case when the player is the claimer
            if(claim.claimer == player){
                action = claim.validSet ? 1:-1;
                break;
            }
            else{ 
                // this part is for the case when the player is not the claimer
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

        //if the player's placed tokens were cleared, change state to waitingForActivity
        if(cardsRemoved & stillThisState()) changeToState(State.waitingForActivity);

        // here we handle the action that was decided in the previous part
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
    private void point() {
        env.ui.setScore(player.id, player.incrementAndGetScore());

        // if the player claimed a valid set we want to clear all of his placed tokens
        clearAllPlacedTokens();

        // this is an optimization to skip the frozen state if the freeze time is 0
        if(env.config.pointFreezeMillis > 0 & stillThisState()){
            player.setFreezeRemainder(env.config.pointFreezeMillis);
            changeToState(State.frozen);
        } 
        else if(stillThisState()) changeToState(State.waitingForActivity);
    }

    /**
     * Penalize a player and perform other related actions.
     */
    private void penalty() {

        //this is an optimization to skip the frozen state if the freeze time is 0
        if(env.config.penaltyFreezeMillis > 0 & stillThisState()){
            player.setFreezeRemainder(env.config.penaltyFreezeMillis);
            changeToState(State.frozen);
        }
        else if(stillThisState()) changeToState(State.waitingForActivity);
    }

    /**
     * Generates a waiting time for the player to wait for a claim result.
     * @return  1 if the player is still in this state and the claim queue is not empty.
     *   100 if the player is still in this state and the claim queue is empty.
     *   1 if the player is no longer in this state.
     */
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
