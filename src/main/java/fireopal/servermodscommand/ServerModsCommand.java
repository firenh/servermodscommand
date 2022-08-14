package fireopal.servermodscommand;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServerModsCommand implements ModInitializer {
	public static final String MODID = "servermodscommand";
	public static final Logger LOGGER = LoggerFactory.getLogger(MODID);

	@Override
	public void onInitialize() {
		ModsCommand.register();
	}

	public static String getCommandLiteral() {
		return "mods";
	}
}
