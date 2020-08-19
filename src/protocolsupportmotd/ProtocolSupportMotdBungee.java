package protocolsupportmotd;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.stream.Collectors;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.ServerPing;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.event.ProxyPingEvent;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.chat.ComponentSerializer;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;
import net.md_5.bungee.event.EventHandler;
import protocolsupport.api.ProtocolSupportAPI;
import protocolsupport.api.ProtocolVersion;

public class ProtocolSupportMotdBungee extends Plugin implements Listener {

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
		getDataFolder().mkdirs();
		reloadConfiguration();
		getProxy().getPluginManager().registerCommand(this, new ProtocolSupportMotdCommand());
		getProxy().getPluginManager().registerListener(this, this);
	}

	protected final UUID profileUUID = UUID.randomUUID();
	@EventHandler
	public void onServerPingResponse(ProxyPingEvent event) {
		ResponseConfiugration responseConfiguration = cachedVersionResponeConfiguration.get(ProtocolSupportAPI.getProtocolVersion(event.getConnection().getSocketAddress()));
		ServerPing response = event.getResponse();
		response.setDescriptionComponent(responseConfiguration.getMotd());
		String protocol = responseConfiguration.getProtocol();
		if (!protocol.isEmpty()) {
			response.setVersion(new ServerPing.Protocol(protocol, -1));
		}
		List<String> players = responseConfiguration.getPlayers();
		if (!players.isEmpty()) {
			response.getPlayers().setSample(
				players.stream()
				.map(player -> new ServerPing.PlayerInfo(player, profileUUID))
				.toArray(ServerPing.PlayerInfo[]::new)
			);
		}
	}

	protected class ProtocolSupportMotdCommand extends Command {

		public ProtocolSupportMotdCommand() {
			super("protocolsupportmotd", "protocolsupportmotd.admin", "psm");
		}

		@Override
		public void execute(CommandSender sender, String[] args) {
			if (!hasPermission(sender)) {
				sender.sendMessage(textWithColor("No permission", ChatColor.RED));
				return;
			}
			if (args.length < 1) {
				return;
			}
			switch (args[0].toLowerCase(Locale.ENGLISH)) {
				case "reload": {
					reloadConfiguration();
					sender.sendMessage(textWithColor("Configuration reloaded", ChatColor.GREEN));
					return;
				}
			}
		}

		protected TextComponent textWithColor(String text, ChatColor color) {
			TextComponent component = new TextComponent(text);
			component.setColor(color);
			return component;
		}

	}


	protected static final String default_response_path = "default";

	protected static final String templates_response_path = "templates";
	protected static final String version_response_path = "version";

	protected void reloadConfiguration() {
		File file = new File(getDataFolder(), "config.yml");
		ConfigurationProvider provider = ConfigurationProvider.getProvider(YamlConfiguration.class);

		{
			if (file.exists()) {
				try {
					Configuration config = provider.load(file);

					Configuration defaultResponseSection = config.getSection(default_response_path);
					if (defaultResponseSection != null) {
						defaultResponseConfiguration.load(defaultResponseSection);
					}

					Configuration templatesResponseSection = config.getSection(templates_response_path);
					if (templatesResponseSection != null) {
						templateResponseConfiguration.clear();
						for (String key : templatesResponseSection.getKeys()) {
							templateResponseConfiguration.put(key, new ResponseConfiugration().load(templatesResponseSection.getSection(key)));
						}
					}

					Configuration versionsReponseSection = config.getSection(version_response_path);
					if (versionsReponseSection != null) {
						versionResponseTemplate.clear();
						for (String key : versionsReponseSection.getKeys()) {
							versionResponseTemplate.put(ProtocolVersion.valueOf(key), versionsReponseSection.getString(key));
						}
					}

					calculateResponsesCache();
				} catch (IOException e) {
					getLogger().log(Level.SEVERE, "Unable to load configuration file", e);
				}
			}
		}

		{
			Configuration config = new Configuration();

			config.set(default_response_path, defaultResponseConfiguration.save(new Configuration()));

			Configuration templatesResponseSection = new Configuration();
			for (Map.Entry<String, ResponseConfiugration> entry : templateResponseConfiguration.entrySet()) {
				templatesResponseSection.set(entry.getKey(), entry.getValue().save(new Configuration()));
			}
			config.set(templates_response_path, templatesResponseSection);

			Configuration versionsReponseSection = new Configuration();
			for (Map.Entry<ProtocolVersion, String> entry : versionResponseTemplate.entrySet()) {
				versionsReponseSection.set(entry.getKey().name(), entry.getValue());
			}
			config.set(version_response_path, versionsReponseSection);

			try {
				provider.save(config, file);
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

		protected ResponseConfiugration load(Configuration section) {
			motd = parse(section.getString(motd_path, ComponentSerializer.toString(motd)));
			protocol = ChatColor.translateAlternateColorCodes('&', section.getString(protocol_path, protocol));
			players = section.getStringList(players_path).stream().map(player -> ChatColor.translateAlternateColorCodes('&', player)).collect(Collectors.toList());
			return this;
		}

		protected static BaseComponent parse(String string) {
			BaseComponent[] components = ComponentSerializer.parse(string);
			switch (components.length) {
				case 0: {
					return new TextComponent();
				}
				case 1: {
					return components[0];
				}
				default: {
					return new TextComponent(components);
				}
			}
		}

		protected Configuration save(Configuration section) {
			section.set(motd_path, ComponentSerializer.toString(motd));
			section.set(protocol_path, protocol);
			section.set(players_path, players);
			return section;
		}

	}

}
