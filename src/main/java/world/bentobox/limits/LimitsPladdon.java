package world.bentobox.limits;

import org.bukkit.plugin.java.annotation.plugin.ApiVersion;
import org.bukkit.plugin.java.annotation.plugin.Plugin;

import world.bentobox.bentobox.api.addons.Addon;
import world.bentobox.bentobox.api.addons.Pladdon;

/**
 *
 * @author YellowZaki
 */

@Plugin(name="Limits", version="1.0")
@ApiVersion(ApiVersion.Target.v1_16)
public class LimitsPladdon extends Pladdon {
    
    @Override
    public Addon getAddon() {
        return new Limits();
    }
}
