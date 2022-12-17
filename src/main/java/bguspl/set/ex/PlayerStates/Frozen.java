package bguspl.set.ex.PlayerStates;

import bguspl.set.ex.Player;
import bguspl.set.ex.PlayerStates.PlayerState;
import bguspl.set.ex.Player.State;

public class Frozen extends PlayerState {

    private static final int CLOCK_UPDATE_INTERVAL = 250;

    public Frozen(Player player) {
        super(player);
    }

    @Override
    public void run() {
        long freezeUntil = System.currentTimeMillis()+player.getFreezeRemainder();
        updateTimerDisplay(freezeUntil-System.currentTimeMillis());
        while(stillThisState() & freezeUntil >= System.currentTimeMillis() ){
            try{
                synchronized(player){player.wait(CLOCK_UPDATE_INTERVAL);}
            } catch (InterruptedException ignored){}
            updateTimerDisplay(freezeUntil-System.currentTimeMillis()); 
        }

        player.setFreezeRemainder(freezeUntil - System.currentTimeMillis());

        if(stillThisState()){
            env.ui.setFreeze(player.id,0);
            changeToState(State.waitingForActivity);
        }  
    }

     /**
     * Updates the UI timer if the player is frozen
     */
    private void updateTimerDisplay(long time) { 
        env.ui.setFreeze(player.id,time);   
    }
    
    @Override
    public State stateName() {
        return State.frozen;
    }  
}
