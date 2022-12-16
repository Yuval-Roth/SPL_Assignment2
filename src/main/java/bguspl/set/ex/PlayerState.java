package bguspl.set.ex;

import bguspl.set.ex.Player.State;

public abstract class PlayerState {  

    protected Player player;

    public PlayerState(Player player) {
        this.player = player;
    }

    public abstract void run();
    public abstract Player.State stateName();

    protected void changeToState(Player.State state) {
        player.setState(state);
    }
    protected boolean stillThisState() {
        return getState() == stateName();
    }
    protected State getState() {
        return player.getState();
    }
}
