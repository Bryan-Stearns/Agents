package soar2d;

import java.awt.Point;
import java.io.*;
import java.util.*;
import java.util.logging.*;

import sml.*;
import soar2d.config.Config;
import soar2d.config.SimConfig;
import soar2d.config.Soar2DKeys;
import soar2d.player.*;
import soar2d.player.book.Dog;
import soar2d.player.book.Mouse;
import soar2d.player.book.Robot;
import soar2d.player.book.SoarRobot;
import soar2d.player.eaters.Eater;
import soar2d.player.eaters.SoarEater;
import soar2d.player.eaters.ToscaEater;
import soar2d.player.kitchen.Cook;
import soar2d.player.kitchen.SoarCook;
import soar2d.player.kitchen.ToscaCook;
import soar2d.player.tanksoar.SoarTank;
import soar2d.player.tanksoar.Tank;
import soar2d.player.taxi.SoarTaxi;
import soar2d.player.taxi.Taxi;
import soar2d.world.World;

/**
 * @author voigtjr
 *
 * Keeps track of the meta simulation state. The world keeps track of more state and
 * is the major member of this class. Creates the soar kernel and registers events.
 */
public class Simulation {

	/**
	 * True if we want to use the run-til-output feature
	 */
	boolean runTilOutput = false;
	/**
	 * The soar kernel
	 */
	Kernel kernel = null;
	/**
	 * The random number generator used throughout the program
	 */
	public static Random random = null;
	
	/**
	 * The world and everything associated with it
	 */
	public World world = new World();
	/**
	 * Legal colors (see PlayerConfig)
	 */
	public final String kColors[] = { "red", "blue", "yellow", "purple", "orange", "green", "black",  };
	/**
	 * A list of colors not currently taken by a player
	 */
	private ArrayList<String> unusedColors = new ArrayList<String>(kColors.length);
	/**
	 * String agent name to agent mapping
	 */
	private HashMap<String, Agent> agents = new HashMap<String, Agent>();

	private static final String kDog = "dog";
	private static final String kMouse = "mouse";
	
	/**
	 * @return true if there were no errors during initialization
	 * 
	 * sets everything up in preparation of execution. only called once per
	 * program run (not once per soar run)
	 */
	public boolean initialize() {
		// keep track of colors
		for (int i = 0; i < kColors.length; ++i) {
			unusedColors.add(kColors[i]);
		}
		
		runTilOutput = Soar2D.simConfig.runTilOutput();
		
		// Initialize Soar
		if (Soar2D.config.getBoolean(Soar2DKeys.general.soar.remote, false)) {
			kernel = Kernel.CreateRemoteConnection(true);
		} else {
			// Create kernel
			kernel = Kernel.CreateKernelInNewThread("SoarKernelSML", Soar2D.config.getInt(Soar2DKeys.general.soar.port, 12121));
			//kernel = Kernel.CreateKernelInCurrentThread("SoarKernelSML", true);
		}

		if (kernel.HadError()) {
			Soar2D.control.severeError("Error creating kernel: " + kernel.GetLastErrorDescription());
			return false;
		}
		
		// We want the most performance
		if (Soar2D.logger.isLoggable(Level.FINEST)) Soar2D.logger.finest("Setting auto commit false.");
		kernel.SetAutoCommit(false);

		// Make all runs non-random if asked
		// For debugging, set this to make all random calls follow the same sequence
		if (Soar2D.config.hasKey(Soar2DKeys.general.seed)) {
			// seed the generators
			int seed = Soar2D.config.getInt(Soar2DKeys.general.seed, 0);
			if (Soar2D.logger.isLoggable(Level.FINEST)) Soar2D.logger.finest("Seeding generators with " + seed);
			kernel.ExecuteCommandLine("srand " + seed, null) ;
			random = new Random(seed);
		} else {
			if (Soar2D.logger.isLoggable(Level.FINEST)) Soar2D.logger.finest("Not seeding generators.");
			random = new Random();
		}
		
		// Register for events
		kernel.RegisterForSystemEvent(smlSystemEventId.smlEVENT_SYSTEM_START, Soar2D.control, null);
		kernel.RegisterForSystemEvent(smlSystemEventId.smlEVENT_SYSTEM_STOP, Soar2D.control, null);
		if (runTilOutput) {
			if (Soar2D.logger.isLoggable(Level.FINEST)) Soar2D.logger.finest("Registering for: smlEVENT_AFTER_ALL_GENERATED_OUTPUT");
			kernel.RegisterForUpdateEvent(smlUpdateEventId.smlEVENT_AFTER_ALL_GENERATED_OUTPUT, Soar2D.control, null);
		} else {
			if (Soar2D.logger.isLoggable(Level.FINEST)) Soar2D.logger.finest("Registering for: smlEVENT_AFTER_ALL_OUTPUT_PHASES");
			kernel.RegisterForUpdateEvent(smlUpdateEventId.smlEVENT_AFTER_ALL_OUTPUT_PHASES, Soar2D.control, null);
		}
		
		// Load the world
		if(!world.load()) {
			return false;
		}
		
		// Add default debugger client to configuration, overwriting any existing java-debugger config:
		String prefix = Soar2DKeys.clientKey(Names.kDebuggerClient) + ".";
		Soar2D.config.setBoolean(prefix + Soar2DKeys.clients.after, true);
		Soar2D.config.setInt(prefix + Soar2DKeys.clients.after, 15);
		
		// Start or wait for clients (false: before agent creation)
		if (!doClients(false)) {
			return false;
		}
		
		// add initial players
		if (Soar2D.config.hasKey(Soar2DKeys.players.active_players)) {
			for ( String playerId : Soar2D.config.getStrings(Soar2DKeys.players.active_players)) {
				createPlayer(playerId);
			}
		}
		
		// Start or wait for clients (true: after agent creation)
		if (!doClients(true)) {
			return false;
		}
		
		// success
		return true;
	}
	
	public ArrayList<String> getUnusedColors() {
		return unusedColors;
	}
	
	/**
	 * @param color the color to use, or null if any will work
	 * @return null if the color is not available for whatever reason
	 * 
	 * removes a color from the unused colors list (by random if necessary)
	 * a return of null indicates failure, the color is taken or no more
	 * are available
	 */
	public String useAColor(String color) {
		if (unusedColors.size() < 1) {
			return null;
		}
		if (color == null) {
			int pick = random.nextInt(unusedColors.size());
			color = unusedColors.get(pick);
			unusedColors.remove(pick);
			return color;
		}
		Iterator<String> iter = unusedColors.iterator();
		while (iter.hasNext()) {
			if (color.equalsIgnoreCase(iter.next())) {
				iter.remove();
				return color;
			}
		}
		return null;
	}
	
	/**
	 * @param color the color to free up, must be not null
	 * @return false if the color wasn't freed up
	 * 
	 * The opposite of useAColor
	 * a color wouldn't be freed up if it wasn't being used in the first place
	 * or if it wasn't legal
	 */
	public boolean freeAColor(String color) {
		assert color != null;
		boolean legal = false;
		for (int i = 0; i < kColors.length; ++i) {
			if (color.equals(kColors[i])) {
				legal = true;
			}
		}
		if (!legal) {
			return false;
		}
		if (unusedColors.contains(color)) {
			return false;
		}
		unusedColors.add(color);
		return true;
	}

	/**
	 * @author voigtjr
	 *
	 * exception class to keep things sane during player creation
	 */
	class CreationException extends Throwable {
		static final long serialVersionUID = 1;
		private String message;
		
		public CreationException() {
		}
		
		public CreationException(String message) {
			this.message = message;
		}
		
		public String getMessage() {
			return message;
		}
	}

	/**
	 * @param playerConfig configuration data for the future player
	 * 
	 * create a player and add it to the simulation and world
	 */
	public void createPlayer(String playerId) {
		Config playerConfig = Soar2D.config.getChild(Soar2DKeys.playerKey(playerId));
		
		if ((Soar2D.simConfig.is(SimConfig.Game.TAXI)) && (world.getPlayers().size() > 1)) {
			// if this is removed, revisit white color below!
			Soar2D.control.severeError("Taxi game type only supports 1 player.");
			return;
		}
		
		// if a color was specified
		if (playerConfig.hasKey(Soar2DKeys.players.color)) {
			//make sure it is unused
			if (!unusedColors.contains(playerConfig.requireString(Soar2DKeys.players.color))) {
				Soar2D.control.severeError("Color used or not available: " + playerConfig.requireString(Soar2DKeys.players.color));
				return;
			}
			// it is unused, so use it
			useAColor(playerConfig.requireString(Soar2DKeys.players.color));
		} else {
			
			// no color specified, pick on at random
			String color = useAColor(null);
			
			// make sure we got one
			if (color == null) {
				
				// if we didn't then they are all gone
				Soar2D.control.severeError("There are no more player slots available.");
				return;
			}
			playerConfig.setString(Soar2DKeys.players.color, color);
		}
		
		// if we don't have a name, use our color
		if (!playerConfig.hasKey(Soar2DKeys.players.name)) {
			playerConfig.setString(Soar2DKeys.players.name, playerConfig.requireString(Soar2DKeys.players.color));
		}
		
		try {
			// check for duplicate name
			if (world.getPlayers().get(playerConfig.requireString(Soar2DKeys.players.name)) != null) {
				throw new CreationException("Failed to create player: " + playerConfig.requireString(Soar2DKeys.players.name) + " already exists.");
			}
			
			// check for human agent
			if (playerConfig.hasKey(Soar2DKeys.players.productions) == false) {
				
				// create a human agent
				Player player = null;
				
				// eater or tank depending on the setting
				boolean human = true;
				switch(Soar2D.simConfig.game()) {
				case EATERS:
					if (Soar2D.config.getBoolean(Soar2DKeys.general.tosca, false)) {
						player = new ToscaEater(playerId);
						human = false;
					} else {
						player = new Eater(playerId);
					}
					break;
					
				case TANKSOAR:
					player = new Tank(playerId);
					break;
					
				case ROOM:
					if (playerConfig.requireString(Soar2DKeys.players.name).equals(kDog)) {
						player = new Dog(playerId);
						human = false;
					} else if (playerConfig.requireString(Soar2DKeys.players.name).equals(kMouse)) {
						player = new Mouse(playerId);
						human = false;
					} else {
						player = new Robot(playerId);
					}
					break;

				case KITCHEN:
					if (Soar2D.config.getBoolean(Soar2DKeys.general.tosca, false)	) {
						player = new ToscaCook(playerId);
						human = false;
					} else {
						player = new Cook(playerId);
					}
					break;
					
				case TAXI:
					player = new Taxi(playerId);
					break;

				}
				
				assert player != null;
				
				// set its location if necessary
				java.awt.Point initialLocation = getInitialLocation(playerConfig);

				// This can fail if there are no open squares on the map, message printed already
				if (!world.addPlayer(player, initialLocation, human)) {
					throw new CreationException();
				}
				
			} else {
				
				// we need to create a soar agent, do it
				Agent agent = kernel.CreateAgent(playerConfig.requireString(Soar2DKeys.players.name));
				if (agent == null) {
					throw new CreationException("Agent " + playerConfig.requireString(Soar2DKeys.players.name) + " creation failed: " + kernel.GetLastErrorDescription());
				}
				
				try {
					// now load the productions
					File productionsFile = new File(playerConfig.requireString(Soar2DKeys.players.productions));
					if (!agent.LoadProductions(productionsFile.getAbsolutePath())) {
						throw new CreationException("Agent " + playerConfig.requireString(Soar2DKeys.players.name) + " production load failed: " + agent.GetLastErrorDescription());
					}
					
					// if requested, silence agent
					if (Soar2D.config.getBoolean(Soar2DKeys.general.soar.watch_0, false)) {
						agent.ExecuteCommandLine("watch 0");
					}
					
					// if requested, set max memory usage
					int maxmem = Soar2D.config.getInt(Soar2DKeys.general.soar.max_memory_usage, -1);
					if (maxmem > 0) {
						agent.ExecuteCommandLine("max-memory-usage " + Integer.toString(maxmem));
					}
			
					Player player = null;
					
					// create the tank or eater, soar style
					switch(Soar2D.simConfig.game()) {
					case EATERS:
						player = new SoarEater(agent, playerId); 
						break;
					case TANKSOAR:
						player = new SoarTank(agent, playerId);
						break;
					case ROOM:
						player = new SoarRobot(agent, playerId);
						break;
					case KITCHEN:
						player = new SoarCook(agent, playerId);
						break;
					case TAXI:
						player = new SoarTaxi(agent, playerId);

					}
					
					assert player != null;
					
					// handle the initial location if necesary
					java.awt.Point initialLocation = getInitialLocation(playerConfig);
					
					// This can fail if there are no open squares on the map, message printed already
					if (!world.addPlayer(player, initialLocation, false)) {
						throw new CreationException();
					}
		
					// Scott Lathrop --  register for print events
					if (Soar2D.config.getBoolean(Soar2DKeys.general.logging.soar_print, false)) {
						agent.RegisterForPrintEvent(smlPrintEventId.smlEVENT_PRINT, Soar2D.control.getLogger(), null,true);
					}
					
					// save the agent
					agents.put(player.getName(), agent);
					
					// spawn the debugger if we're supposed to
					String prefix = Soar2DKeys.clientKey(Names.kDebuggerClient) + ".";
					Soar2D.config.setString(prefix + Soar2DKeys.clients.command, getDebuggerCommand(player.getName()));

					if (Soar2D.config.getBoolean(Soar2DKeys.general.soar.spawn_debuggers, true) && !isClientConnected(Names.kDebuggerClient)) {
						spawnClient(Names.kDebuggerClient);
					}
					
				} catch (CreationException e) {
					// A problem in this block requires agent deletion
					kernel.DestroyAgent(agent);
					agent.delete();
					throw e;
				}
			}
		} catch (CreationException e) {
			// a problem in this block requires us to free up the color
			freeAColor(playerConfig.requireString(Soar2DKeys.players.color));
			if (e.getMessage() != null) {
				Soar2D.control.severeError(e.getMessage());
			}
			return;
		}
		
		// the agent list has changed, notify things that care
		Soar2D.control.playerEvent();
	}
	
	private Point getInitialLocation(Config playerConfig) {
		java.awt.Point initialLocation = null;
		if (playerConfig.hasKey(Soar2DKeys.players.pos)) {
			initialLocation = new Point();
			int [] pos = playerConfig.requireInts(Soar2DKeys.players.pos);
			if (pos != null && pos.length == 2) {
				initialLocation.x = pos[0];
				initialLocation.y = pos[1];
			}
		}
		return initialLocation;
	}

	/**
	 * @param client the client in question
	 * @return true if it is connected
	 * 
	 * check to see if the client specified by the client config is connected or not
	 */
	public boolean isClientConnected(String clientId) {
		boolean connected = false;
		kernel.GetAllConnectionInfo();
		for (int i = 0; i < kernel.GetNumberConnections(); ++i) {
			ConnectionInfo info =  kernel.GetConnectionInfo(i);
			if (info.GetName().equalsIgnoreCase(clientId)) {
				connected = true;
				break;
			}
		}
		return connected;
	}
	
	/**
	 * @param agentName tailor the command to this agent name
	 * @return a string command line to execute to spawn the debugger
	 */
	public String getDebuggerCommand(String agentName) {
		// Figure out whether to use java or javaw
		String os = System.getProperty("os.name");
		String commandLine;
		if (os.matches(".+indows.*") || os.matches("INDOWS")) {
			commandLine = "javaw -jar \"" + getBasePath() 
			+ "..\\..\\SoarLibrary\\bin\\SoarJavaDebugger.jar\" -cascade -remote -agent " 
			+ agentName + " -port " + Soar2D.config.getInt(Soar2DKeys.general.soar.port, 12121);
		} else {
			commandLine = System.getProperty("java.home") + "/bin/java -jar " + getBasePath()
			+ "../../SoarLibrary/bin/SoarJavaDebugger.jar -XstartOnFirstThread -cascade -remote -agent " 
			+ agentName + " -port " + Soar2D.config.getInt(Soar2DKeys.general.soar.port, 12121);
		}
		
		return commandLine;
	}

	/**
	 * @param player the player to remove
	 * 
	 * removes the player from the world and blows away any associated data, 
	 * frees up its color, etc.
	 */
	public void destroyPlayer(Player player) {
		// remove it from the world, can't fail
		world.removePlayer(player.getName());
		
		// free its color
		freeAColor(player.getColor());
		
		// call its shutdown
		player.shutdown();
		
		// get the agent (human agents return null here)
		Agent agent = agents.remove(player.getName());
		if (agent != null) {
			// there was an agent, destroy it
			if (!kernel.DestroyAgent(agent)) {
				Soar2D.control.severeError("Failed to destroy soar agent " + player.getName() + ": " + kernel.GetLastErrorDescription());
			}
			agent.delete();
			agent = null;
		}
		
		// the player list has changed, notify those who care
		Soar2D.control.playerEvent();
	}
	
	/**
	 * @param player the player to reload
	 * 
	 * reload the player. only currently makes sense to reload a soar agent.
	 * this re-loads the productions
	 */
	public void reloadPlayer(Player player) {
		Agent agent = agents.get(player.getName());
		if (agent == null) {
			return;
		}
		;
		Config playerConfig = Soar2D.config.getChild(Soar2DKeys.playerKey(player.getId()));
		assert playerConfig != null;
		assert playerConfig.hasKey(Soar2DKeys.players.productions);
		File productionsFile = new File(playerConfig.requireString(Soar2DKeys.players.productions));
		agent.LoadProductions(productionsFile.getAbsolutePath());
	}
	
	/**
	 * @param after do the clients denoted as "after" agent creation
	 * @return true if the clients all connected.
	 */
	private boolean doClients(boolean after) {
		if (Soar2D.config.hasKey(Soar2DKeys.clients.active_clients)) {
			for ( String clientId : Soar2D.config.getStrings(Soar2DKeys.clients.active_clients)) {
				Config clientConfig = Soar2D.config.getChild(Soar2DKeys.clientKey(clientId));
				if (clientConfig.hasKey(Soar2DKeys.clients.after) == after) {
					continue;
				}
				if (clientConfig.hasKey(Soar2DKeys.clients.command)) {
					spawnClient(clientId);
				} else {
					if (!waitForClient(clientId)) {
						Soar2D.control.severeError("Client spawn failed: " + clientId);
						return false;
					}
				}
			}
		}
		return true;
	}

	/**
	 * @author voigtjr
	 *
	 * This handles some nitty gritty client spawn stuff
	 */
	private class Redirector extends Thread {
		BufferedReader br;
		public Redirector(BufferedReader br) {
			this.br = br;
		}
		
		public void run() {
			String line;
			try {
				while ((line = br.readLine()) != null) {
					System.out.println(line);
				}
			} catch (IOException e) {
				System.err.println(e.getMessage());
			}
		}
	}
	
	/**
	 * @param client the client to spawn
	 * 
	 * spawns a client, waits for it to connect
	 */
	public void spawnClient(String clientId) {
		Config clientConfig = Soar2D.config.getChild(Soar2DKeys.clientKey(clientId));
		
		Runtime r = java.lang.Runtime.getRuntime();
		if (Soar2D.logger.isLoggable(Level.FINER)) Soar2D.logger.finer("Spawning client: " + clientId);

		try {
			Process p = r.exec(clientConfig.requireString(Soar2DKeys.clients.command));
			
			InputStream is = p.getInputStream();
			InputStreamReader isr = new InputStreamReader(is);
			BufferedReader br = new BufferedReader(isr);
			Redirector rd = new Redirector(br);
			rd.start();

			is = p.getErrorStream();
			isr = new InputStreamReader(is);
			br = new BufferedReader(isr);
			rd = new Redirector(br);
			rd.start();
			
			if (!waitForClient(clientId)) {
				Soar2D.control.severeError("Client spawn failed: " + clientId);
				return;
			}
			
		} catch (IOException e) {
			Soar2D.control.severeError("IOException spawning client: " + clientId + ": " + e.getMessage());
			shutdown();
			System.exit(1);
		}
	}
	
	/**
	 * @param client the client to wait for
	 * @return true if the client connected within the timeout
	 * 
	 * waits for a client to report ready
	 */
	public boolean waitForClient(String clientId) {
		Config clientConfig = Soar2D.config.getChild(Soar2DKeys.clientKey(clientId));

		boolean ready = false;
		// do this loop if timeout seconds is 0 (code for wait indefinitely) or if we have tries left
		for (int tries = 0; (clientConfig.getInt(Soar2DKeys.clients.timeout, 0) == 0) || (tries < clientConfig.getInt(Soar2DKeys.clients.timeout, 0)); ++tries) {
			kernel.GetAllConnectionInfo();
			if (kernel.HasConnectionInfoChanged()) {
				for (int i = 0; i < kernel.GetNumberConnections(); ++i) {
					ConnectionInfo info =  kernel.GetConnectionInfo(i);
					if (info.GetName().equalsIgnoreCase(clientId)) {
						if (info.GetAgentStatus().equalsIgnoreCase(sml_Names.getKStatusReady())) {
							ready = true;
							break;
						}
					}
				}
				if (ready) {
					break;
				}
			}
			try { 
				if (Soar2D.logger.isLoggable(Level.FINEST)) Soar2D.logger.finest("Waiting for client: "+ clientId);
				Thread.sleep(1000); 
			} catch (InterruptedException ignored) {}
		}
		return ready;
	}
	
	/**
	 * update the sim, or, in this case, the world
	 */
	public void update() {
		world.update();
	}

	/**
	 * run soar forever
	 */
	public void runForever() {
		if (runTilOutput) {
			kernel.RunAllAgentsForever(smlRunStepSize.sml_UNTIL_OUTPUT);
		} else {
			kernel.RunAllAgentsForever();
		}
		
	}

	/**
	 * run soar one step
	 */
	public void runStep() {
		if (runTilOutput) {
			kernel.RunAllTilOutput(smlRunStepSize.sml_UNTIL_OUTPUT);
		} else {
			kernel.RunAllAgents(1);
		}
	}

	/**
	 * @return true if the map reset was successful
	 * 
	 * resets the world, ready for a new run
	 */
	public boolean reset() {
		Soar2D.logger.info("Resetting simulation.");
		if (!world.load()) {
			File mapFile = new File(Soar2D.config.getString(Soar2DKeys.general.map));
			Soar2D.control.severeError("Error loading map " + mapFile.getAbsolutePath());
			return false;
		}
		return true;
	}

	/**
	 * shuts things down, including the kernel, in preparation for an exit to dos
	 */
	public void shutdown() {
		if (world != null) {
			world.shutdown();
		}
		
		assert this.agents.size() == 0;
		
		if (kernel != null) {
			if (Soar2D.logger.isLoggable(Level.FINEST)) Soar2D.logger.finest("Shutting down kernel.");
			kernel.Shutdown();
			kernel.delete();
		}
	}
	
	/**
	 * @return true if there are human agents present
	 */
	public boolean hasHumanAgents() {
		return agents.size() < world.getPlayers().size();
	}
	
	/**
	 * @return true if there are soar agents present
	 */
	public boolean hasSoarAgents() {
		return agents.size() > 0;
	}
	
	/**
	 * TODO
	 * @return true if the simulation has reached a terminal state
	 * 
	 * check to see if one of the terminal states has been reached
	 */
	public boolean isDone() {
		return world.isTerminal();
	}

	public String getBasePath() {
		return System.getProperty("user.dir") + System.getProperty("file.separator");
	}
	public String getMapPath() {
		return Soar2D.simulation.getBasePath() + "maps" + System.getProperty("file.separator");
	}
	public String getMapExt() {
		switch (Soar2D.simConfig.game()) {
		case TANKSOAR:
			return "tmap";
		case EATERS:
			return "emap";
		case ROOM:
			return "bmap";
		case KITCHEN:
		case TAXI:
			return "xml";
		}
		return null;
	}
	public String getAgentPath() {
		return Soar2D.simulation.getBasePath() + "agents" + System.getProperty("file.separator");
	}
}
