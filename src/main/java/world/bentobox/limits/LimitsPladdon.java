package world.bentobox.limits;


import world.bentobox.bentobox.api.addons.Addon;
import world.bentobox.bentobox.api.addons.Pladdon;


public class LimitsPladdon extends Pladdon {
    
    @Override
    public Addon getAddon() {
        return new Limits();
    }
}
