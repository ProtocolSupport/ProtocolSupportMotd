package protocolsupportmotd;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.stream.Collectors;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import protocolsupport.api.ProtocolVersion;
import protocolsupport.api.chat.ChatAPI;
import protocolsupport.api.chat.components.BaseComponent;
import protocolsupport.api.chat.components.TextComponent;
import protocolsupport.api.events.ServerPingResponseEvent;
import protocolsupport.api.events.ServerPingResponseEvent.ProtocolInfo;

public class ProtocolSupportMotdBukkit extends JavaPlugin implements Listener {

	private final ResponseConfiugration defaultResponseConfiguration = new ResponseConfiugration();

	private final Map<String, ResponseConfiugration> templateResponseConfiguration = new HashMap<>();
	private final Map<ProtocolVersion, String> versionResponseTemplate = new EnumMap<>(ProtocolVersion.class);

	private final Map<ProtocolVersion, ResponseConfiugration> cachedVersionResponeConfiguration = new ConcurrentHashMap<>();

	{
		String legacyKey = "legacy";
		templateResponseConfiguration.put(legacyKey, new ResponseConfiugration(new TextComponent("A minecraft server")));
		for (ProtocolVersion version : ProtocolVersion.getAllBeforeI(ProtocolVersion.MINECRAFT_1_6_4)) {
			versionResponseTemplate.put(version, legacyKey);
		}
		calculateResponsesCache();
	}

	protected void calculateResponsesCache() {
		for (ProtocolVersion version : ProtocolVersion.values()) {
			ResponseConfiugration templateConfiguration = templateResponseConfiguration.get(versionResponseTemplate.get(version));
			if (templateConfiguration != null) {
				cachedVersionResponeConfiguration.put(version, templateConfiguration);
			} else {
				cachedVersionResponeConfiguration.put(version, defaultResponseConfiguration);
			}
		}
	}

	@Override
	public void onEnable() {
		reloadConfiguration();
		getServer().getPluginManager().registerEvents(this, this);
	}

	@EventHandler
	protected void onServerPingResponse(ServerPingResponseEvent event) {
		ResponseConfiugration responseConfiguration = cachedVersionResponeConfiguration.get(event.getConnection().getVersion());
		event.setJsonMotd(responseConfiguration.getMotd());
		String protocol = responseConfiguration.getProtocol();
		if (!protocol.isEmpty()) {
			event.setProtocolInfo(new ProtocolInfo(-1, protocol));
		}
		List<String> players = responseConfiguration.getPlayers();
		if (!players.isEmpty()) {
			event.setPlayers(players);
		}
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (!command.testPermission(sender)) {
			return true;
		}
		if (args.length < 1) {
			return false;
		}
		switch (args[0].toLowerCase(Locale.ENGLISH)) {
			case "reload": {
				reloadConfiguration();
				sender.sendMessage(ChatColor.GREEN + "Configuration reloaded");
				return true;
			}
		}
		return false;
	}


	protected static final String default_response_path = "default";

	protected static final String templates_response_path = "templates";
	protected static final String version_response_path = "version";

	protected void reloadConfiguration() {
		File file = new File(getDataFolder(), "config.yml");

		{
			ConfigurationSection config = YamlConfiguration.loadConfiguration(file);

			ConfigurationSection defaultResponseSection = config.getConfigurationSection(default_response_path);
			if (defaultResponseSection != null) {
				defaultResponseConfiguration.load(defaultResponseSection);
			}

			ConfigurationSection templatesResponseSection = config.getConfigurationSection(templates_response_path);
			if (templatesResponseSection != null) {
				templateResponseConfiguration.clear();
				for (String key : templatesResponseSection.getKeys(false)) {
					templateResponseConfiguration.put(key, new ResponseConfiugration().load(templatesResponseSection.getConfigurationSection(key)));
				}
			}

			ConfigurationSection versionsReponseSection = config.getConfigurationSection(version_response_path);
			if (versionsReponseSection != null) {
				versionResponseTemplate.clear();
				for (String key : versionsReponseSection.getKeys(false)) {
					versionResponseTemplate.put(ProtocolVersion.valueOf(key), versionsReponseSection.getString(key));
				}
			}

			calculateResponsesCache();
		}

		{
			FileConfiguration config = new YamlConfiguration();

			defaultResponseConfiguration.save(config.createSection(default_response_path));

			ConfigurationSection templatesResponseSection = config.createSection(templates_response_path);
			for (Map.Entry<String, ResponseConfiugration> entry : templateResponseConfiguration.entrySet()) {
				entry.getValue().save(templatesResponseSection.createSection(entry.getKey()));
			}

			ConfigurationSection versionsReponseSection = config.createSection(version_response_path);
			for (Map.Entry<ProtocolVersion, String> entry : versionResponseTemplate.entrySet()) {
				versionsReponseSection.set(entry.getKey().name(), entry.getValue());
			}

			try {
				config.save(file);
			} catch (IOException e) {
				getLogger().log(Level.SEVERE, "Unable to save configuration file", e);
			}
		}
	}

	protected static class ResponseConfiugration {

		protected BaseComponent motd = new TextComponent("Minecraft Server");
		protected String protocol = "";
		protected List<String> players = new ArrayList<>();

		public ResponseConfiugration() {
		}

		public ResponseConfiugration(BaseComponent motd) {
			this.motd = motd;
		}

		public ResponseConfiugration(BaseComponent motd, String protocol, List<String> players) {
			this.motd = motd;
			this.protocol = protocol;
			this.players = new ArrayList<>(players);
		}

		protected static final String motd_path = "motd";
		protected static final String protocol_path = "protocol";
		protected static final String players_path = "players";

		public BaseComponent getMotd() {
			return motd;
		}

		public String getProtocol() {
			return protocol;
		}

		public List<String> getPlayers() {
			return players;
		}

		protected ResponseConfiugration load(ConfigurationSection section) {
			motd = ChatAPI.fromJSON(section.getString(motd_path, ChatAPI.toJSON(motd)));
			protocol = ChatColor.translateAlternateColorCodes('&', section.getString(protocol_path, protocol));
			players = section.getStringList(players_path).stream().map(player -> ChatColor.translateAlternateColorCodes('&', player)).collect(Collectors.toList());
			return this;
		}

		protected void save(ConfigurationSection section) {
			section.set(motd_path, ChatAPI.toJSON(motd));
			section.set(protocol_path, protocol);
			section.set(players_path, players);
		}

	}

}
