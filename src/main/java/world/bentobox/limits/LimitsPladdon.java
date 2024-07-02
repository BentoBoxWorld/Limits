package world.bentobox.limits;


import world.bentobox.bentobox.api.addons.Addon;
import world.bentobox.bentobox.api.addons.Pladdon;


public class LimitsPladdon extends Pladdon {
    
    private Addon addon;

    @Override
    public Addon getAddon() {
        if (addon == null) {
            addon = new Limits();
        }
        return addon;
    }
}
