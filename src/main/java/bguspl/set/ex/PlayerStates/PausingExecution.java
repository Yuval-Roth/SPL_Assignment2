package bguspl.set.ex.PlayerStates;

import bguspl.set.ex.Player;
import bguspl.set.ex.Player.State;

public class PausingExecution extends PlayerState {
    
    public PausingExecution(Player player) {
        super(player);
    }

    @Override
    public void run() {
            clearAllPlacedTokens();
            clearClickQueue();
            changeToState(State.paused);
        }      

    @Override
    public State stateName() {
        return State.pausingExecution;
    }  
}
