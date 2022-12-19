package bguspl.set.ex.PlayerStates;

import bguspl.set.ex.Player;
import bguspl.set.ex.Player.State;

public class Frozen extends PlayerState {

    private static final int CLOCK_UPDATE_INTERVAL = 250;

    public Frozen(Player player) {
        super(player);
    }

    @Override
    public void run() {

        //set the timer to the remainder of the freeze time
        long freezeUntil = System.currentTimeMillis()+player.getFreezeRemainder();
        updateTimerDisplay(freezeUntil-System.currentTimeMillis());

        //main freeze timer loop
        while(stillThisState() & freezeUntil >= System.currentTimeMillis() ){
            try{
                synchronized(player){player.wait(CLOCK_UPDATE_INTERVAL);}
            } catch (InterruptedException ignored){}
            updateTimerDisplay(freezeUntil-System.currentTimeMillis()); 
        }

        //update the remaining freeze time
        player.setFreezeRemainder(freezeUntil - System.currentTimeMillis());

        //if the player state was not changed by another thread, change it to waiting for activity
        if(stillThisState()){
            env.ui.setFreeze(player.id,0);
            changeToState(State.waitingForActivity);
        }  
    }

     /**
     * Updates the UI timer for the player freeze
     */
    private void updateTimerDisplay(long time) { 

        // this if statement is to prevent the UI from being updated after the game has been terminated because
        // it causes a deadlock in linux for some reason out of our control 
        if(dealer.terminate == false){
            env.ui.setFreeze(player.id,time);   
        }
    }
    
    @Override
    public State stateName() {
        return State.frozen;
    }  
}
