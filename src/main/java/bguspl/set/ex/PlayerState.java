package bguspl.set.ex;

public abstract class PlayerState {  

    protected Player player;

    public PlayerState(Player player) {
        this.player = player;
    }

    public abstract void run();
    public abstract Player.State getState();

    protected void changeToState(Player.State state) {
        player.setState(state);
    }
    protected boolean checkState() {
        return player.getState() == getState();
    }
}
