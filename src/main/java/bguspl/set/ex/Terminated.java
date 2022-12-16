package bguspl.set.ex;

import bguspl.set.ex.Player.State;

public class Terminated extends PlayerState {

    @Override
    public void run() {
        //do nothing
    }

    @Override
    public State stateName() {
        return State.terminated;
    } 
}
