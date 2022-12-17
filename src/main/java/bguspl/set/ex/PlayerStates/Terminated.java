package bguspl.set.ex.PlayerStates;

import bguspl.set.ex.Player.State;

public class Terminated extends PlayerState {

    public Terminated() {
        super();
    }

    @Override
    public void run() {
        //do nothing
    }

    @Override
    public State stateName() {
        return State.terminated;
    } 
}
