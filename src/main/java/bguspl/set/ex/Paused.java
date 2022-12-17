package bguspl.set.ex;

import bguspl.set.ex.Player.State;

public class Paused extends PlayerState{

    /**
    * Object for breaking wait() when waiting for general activity
    */
    private volatile Object executionListener;

    public Paused(Player player) {
        super(player);
        executionListener = player.getExecutionListener();
    }

    @Override
    public void run() {

        try{
            // Wait for the game to be resumed / terminated
            synchronized(executionListener){executionListener.wait();}
        }catch(InterruptedException ignored){}
        
        // If the game is not terminated, check if the player is frozen or not
        if(getState() != State.terminated){
            if (player.getFreezeRemainder() > 0){
                changeToState(State.frozen);
            }
            else{
                changeToState(State.waitingForActivity);
            } 
        }
    }

    @Override
    public State stateName() {
        return State.paused;
    }
     
}
