package eu.phiwa.dt;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.milkbowl.vault.Vault;
import net.milkbowl.vault.economy.Economy;
import net.minecraft.server.v1_7_R1.EntityTypes;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import eu.phiwa.dt.anticheatplugins.AntiCheatHandler;
import eu.phiwa.dt.anticheatplugins.NoCheatPlusHandler;
import eu.phiwa.dt.commands.CommandHandler;
import eu.phiwa.dt.filehandlers.Config;
import eu.phiwa.dt.filehandlers.FlightsDB;
import eu.phiwa.dt.filehandlers.HomesDB;
import eu.phiwa.dt.filehandlers.Messages;
import eu.phiwa.dt.filehandlers.StationsDB;
import eu.phiwa.dt.flights.FlightEditor;
import eu.phiwa.dt.listeners.BlockListener;
import eu.phiwa.dt.listeners.EntityListener;
import eu.phiwa.dt.listeners.PlayerListener;
import eu.phiwa.dt.modules.MountingScheduler;
import eu.phiwa.dt.payment.PaymentHandler;


public class DragonTravelMain extends JavaPlugin {

	public static PluginManager pm;
	public static DragonTravelMain plugin;
	public static final Logger logger = Logger.getLogger("Minecraft");
		
	// General
	public static double speed = 0.5;
	public static boolean ignoreAntiMobspawnAreas;
	public static boolean onlysigns = false;
	public static boolean ptoggleDefault = false;
	
	// Listeners
	private static EntityListener entityListener;
	private static PlayerListener playerListener;
	private static BlockListener blocklistener;
	private static FlightEditor flighteditor;
	
	// Config
	public static double configVersion = 0.2;
	public static File configFile;
	public static FileConfiguration config;
	public static Config configHandler;
	
	// Messages
	public static double messagesVersion = 0.2;
	public static File messagesFile;
	public static FileConfiguration messages;
	public static Messages messagesHandler;
	
	// StationsDB
	public static File dbStationsFile;
	public static FileConfiguration dbStationsConfig;
	public static StationsDB dbStationsHandler;
	
	// HomesDB
	public static File dbHomesFile;
	public static FileConfiguration dbHomesConfig;
	public static HomesDB dbHomesHandler;
	
	// FlightsDB
	public static File dbFlightsFile;
	public static FileConfiguration dbFlightsConfig;
	public static FlightsDB dbFlightsHandler;

	// Hashmaps
	public static HashMap<Player, RyeDragon> listofDragonriders = new HashMap<Player, RyeDragon>();
	public static HashMap<Player, Location> listofDragonsridersStartingpoints = new HashMap<Player, Location>();
	public static HashMap<Block, Block> globalwaypointmarkers = new HashMap<Block, Block>();
	public static HashMap<String, Boolean> ptogglers = new HashMap<String, Boolean>();
	
	// CheatProtection-Pluginstatus
	public static boolean anticheat;
	public static boolean nocheatplus;
	
	// Required Item
	public static boolean requireItemTravelStation = false;
	public static boolean requireItemTravelRandom = false;
	public static boolean requireItemTravelCoordinates = false;
	public static boolean requireItemTravelPlayer = false;
	public static boolean requireItemTravelHome = false;	
	public static boolean requireItemTravelFactionhome = false;	
	public static boolean requireItemFlight = false;
	public static int requiredItem = 122;
	
	// Payment-Types
	public static final int TRAVEL_TOSTATION = 1;
	public static final int TRAVEL_TORANDOM = 2;
	public static final int TRAVEL_TOPLAYER = 3;
	public static final int TRAVEL_TOCOORDINATES = 4;
	public static final int TRAVEL_TOHOME = 5;
	public static final int TRAVEL_TOFACTIONHOME = 6;
	public static final int FLIGHT = 7;
	public static final int SETHOME = 8;
	
	// Dragon Antigrief-Options
	public static boolean onlydragontraveldragons;
	public static boolean alldragons;
	
	// DragonLimit
	public static int dragonLimit = 99999;
	
	// Payment (Costs are directly read from the config/sign on-the-fly)
	public static boolean usePayment = false;
	public static boolean byEconomy = false;
	public static boolean byResources = false;
	public static Economy economyProvider;	
	public static int paymentItem = 371;
	
    private final Class<?> dragonClass;
    
    
    public DragonTravelMain() {
        this.dragonClass = RyeDragon.class;
    }
    
	@Override
	public void onEnable() {

		pm = getServer().getPluginManager();
		PluginDescriptionFile description = getDescription();
		plugin = this;

		// Add the new entity to Minecraft's (Craftbukkit's) entities
		// Returns false if plugin disabled
		if (!registerEntity()) return;

		// Register EventListener
		entityListener = new EntityListener(this);
		playerListener = new PlayerListener(this);
		flighteditor = new FlightEditor();
		blocklistener = new BlockListener(this);
		
		pm.registerEvents(playerListener, this);
		pm.registerEvents(entityListener, this);
		pm.registerEvents(flighteditor, this);
		pm.registerEvents(blocklistener, this);
		
		if(!(new File(plugin.getDataFolder(), "databases").exists()))
			new File(plugin.getDataFolder(), "databases").mkdirs();
		
		// Config
		configHandler = new Config(this);
		configHandler.loadConfig();
		if(config.getString("File.Version") == null)
		{
			logger.log(Level.SEVERE, "Could not initialize config! Disabling the plugin!");
			this.getPluginLoader().disablePlugin(this);
			return;
		}
		else
			logger.info("Config loaded successfully.");
		
		// Messages-file
		messagesHandler = new Messages(this);
		messagesHandler.loadMessages();
		if(messages == null)
			return;
		
		// StationsDB
		dbStationsHandler = new StationsDB(this);
		dbStationsHandler.init();
		
		// HomesDB
		dbHomesHandler = new HomesDB(this);
		dbHomesHandler.init();
		
		// StationsDB
		dbFlightsHandler = new FlightsDB(this);
		dbFlightsHandler.init();

		
		// Commands
		getCommand("dt").setExecutor(new CommandHandler(this));
		
		// AntiCheat
		if (AntiCheatHandler.getAntiCheat())
			logger.info("[DragonTravel] AntiCheat-support enabled");
		
		// NoCheatPlus
		if (NoCheatPlusHandler.getNoCheatPlus())
			logger.info("[DragonTravel] NoCheatPlus-support enabled");	
				
		// Load some variables from config
		onlydragontraveldragons = config.getBoolean("AntiGriefDragons.ofDragonTravel");
		alldragons = config.getBoolean("AntiGriefDragons.all");
		ignoreAntiMobspawnAreas = config.getBoolean("AntiGriefDragons.bypassWorldGuardAntiSpawn");
		
		requiredItem = config.getInt("RequiredItem.Item");;
		requireItemTravelStation = config.getBoolean("RequiredItem.For.toStation");
		requireItemTravelRandom = config.getBoolean("RequiredItem.For.toRandom");
		requireItemTravelCoordinates = config.getBoolean("RequiredItem.For.toCoordinates");
		requireItemTravelPlayer = config.getBoolean("RequiredItem.For.toPlayer");
		requireItemTravelHome = config.getBoolean("RequiredItem.For.toHome");	
		requireItemTravelFactionhome = config.getBoolean("RequiredItem.For.toFactionhome");
		requireItemFlight = config.getBoolean("RequiredItem.For.Flight");	
	
		speed = config.getDouble("DragonSpeed");
		
		usePayment = config.getBoolean("Payment.usePayment");
		byEconomy = config.getBoolean("Payment.byEconomy");
		byResources = config.getBoolean("Payment.byResources");
		
		paymentItem = config.getInt("Payment.Resources.Item");
		
		dragonLimit = config.getInt("DragonLimit");
		
		onlysigns = config.getBoolean("OnlySigns");
		
		ptoggleDefault = config.getBoolean("PToggleDefault");
		
		if(usePayment) {
			
			if(!byEconomy && !byResources) {
				logger.log(Level.SEVERE, "[DragonTravel] Payment has been enabled, but both payment-types are disabled, how should a player be able to pay?! Disabling payment...");
				usePayment = false;
			}
				
			if(byEconomy && byResources) {
				logger.log(Level.SEVERE, "[DragonTravel] Payment has been set to Economy AND Resources, but you can only use one type of payment! Disabling payment...");
				usePayment = false;
			}
					
			// Set up Economy (if config-option is set to true)
			if(byEconomy) {
				Plugin x = pm.getPlugin("Vault");
				if (x != null & x instanceof Vault) {
					logger.info(String.format("[DragonTravel] Hooked into Vault, using it for economy-support"));
					logger.info(String.format("[DragonTravel] Enabled %s", description.getVersion()));		
					new PaymentHandler(this.getServer()).setupEconomy();
				}
				else {
					logger.log(Level.SEVERE, "[DragonTravel] \"Vault\" was not found,");
					logger.log(Level.SEVERE, "[DragonTravel] disabling economy-support!");
					logger.log(Level.SEVERE, "[DragonTravel] Turn off \"Payment.byEconomy\"");
					logger.log(Level.SEVERE, "[DragonTravel] in the config.yml or install Vault!");			
				}
			}	
		}
		
		//MoutingScheduler
		Bukkit.getScheduler().scheduleSyncRepeatingTask(this, new MountingScheduler(), 60L, 30L);
			
	}
	
	@Override
	public void onDisable() {
		logger.log(Level.SEVERE, String.format("[DragonTravel] -----------------------------------------------"));
		logger.log(Level.SEVERE, String.format("[DragonTravel] Successfully disabled %s %s", getDescription().getName(), getDescription().getVersion()));
		logger.log(Level.SEVERE, String.format("[DragonTravel] -----------------------------------------------"));
	}

	private boolean registerEntity() {
    
        try {        
            Class entityTypeClass = EntityTypes.class;
            
            Field c = entityTypeClass.getDeclaredField("c");
            c.setAccessible(true);
            HashMap c_map = (HashMap)c.get(null);
            c_map.put("RyeDragon", this.dragonClass);

            Field d = entityTypeClass.getDeclaredField("d");
            d.setAccessible(true);
            HashMap d_map = (HashMap)d.get(null);
            d_map.put(this.dragonClass, "RyeDragon");

            Field e = entityTypeClass.getDeclaredField("e");
            e.setAccessible(true);
            HashMap e_map = (HashMap)e.get(null);
            e_map.put(Integer.valueOf(63), this.dragonClass);

            Field f = entityTypeClass.getDeclaredField("f");
            f.setAccessible(true);
            HashMap f_map = (HashMap)f.get(null);
            f_map.put(this.dragonClass, Integer.valueOf(63));

            Field g = entityTypeClass.getDeclaredField("g");
            g.setAccessible(true);
            HashMap g_map = (HashMap)g.get(null);
            g_map.put("RyeDragon", Integer.valueOf(63));
            
            return true;
        }    
		catch (Exception e) {

            Class<?>[] paramTypes = new Class[] { Class.class, String.class, int.class };
            
			// MCPC+ compatibility
			// Forge Dev environment; names are not translated into func_foo
			try { 
				Method method = EntityTypes.class.getDeclaredMethod("addMapping", paramTypes);
				method.setAccessible(true);
				method.invoke(null, RyeDragon.class, "RyeDragon", 63);
				return true;
			}
            catch (Exception ex) {
                e.addSuppressed(ex);
            }
			// Production environment: search for the method
			// This is required because the seargenames could change
			// LAST CHECKED FOR VERSION 1.6.4
			try {
				for (Method method : EntityTypes.class.getDeclaredMethods()) {
					if (Arrays.equals(paramTypes, method.getParameterTypes())) {
						method.invoke(null, RyeDragon.class, "RyeDragon", 63);
						return true;
					}
				}
			}
            catch (Exception ex) {
                e.addSuppressed(ex);
            }

			logger.info("[DragonTravel] [Error] Could not register the RyeDragon-entity!");
			e.printStackTrace();
			pm.disablePlugin(this);

		}
		return false;
	}


	public void reload() {
		
		logger.log(Level.INFO, "Reloading all files.");
		logger.log(Level.INFO, "WE RECOMMEND NOT TO DO THIS BECAUSE IT MIGHT CAUSE SERIUOS PROBLEMS!");
		logger.log(Level.INFO, "SIMPLY RESTART YOUR SERVER INSTEAD; THAT'S MUCH SAFER!");
		
		// Config
		configHandler.loadConfig();
		if(config.getString("File.Version") == null)
		{
			logger.log(Level.SEVERE, "Could not initialize config! Disabling the plugin!");
			this.getPluginLoader().disablePlugin(this);
			return;
		}
		else
			logger.info("Config loaded successfully.");
		
		
		// Messages-file
		messagesHandler.loadMessages();
		if(messages == null)
			return;
		
		
		// StationsDB
		dbStationsHandler.init();
		
		
		// HomesDB
		dbHomesHandler.init();
		
		
		// StationsDB
		dbFlightsHandler.init();
		
		
		logger.log(Level.INFO, "Successfully reloaded all files.");
	}
}
