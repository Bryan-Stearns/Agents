/**
 * 
 */
package edu.umich.soar.robot;

import org.apache.log4j.Logger;

import sml.Agent;
import sml.Identifier;

/**
 * @author voigtjr
 *
 * Removes a message from the received message list.
 */
final public class RemoveMessageCommand extends NoDDCAdapter implements Command {
	private static final Logger logger = Logger.getLogger(RemoveMessageCommand.class);
	public static final String NAME = "remove-message";

	public static Command newInstance(MessagesInterface messages) {
		return new RemoveMessageCommand(messages);
	}
	
	public RemoveMessageCommand(MessagesInterface messages) {
		this.messages = messages;
	}

	private final MessagesInterface messages;

	@Override
	public boolean execute(Agent agent, Identifier command) {

		int id = -1;
		try {
			id = Integer.parseInt(command.GetParameterValue("id"));
		} catch (NullPointerException ignored) {
			logger.warn(NAME + ": No id on command");
			CommandStatus.error.addStatus(agent, command);
			return false;
		} catch (NumberFormatException e) {
			logger.warn(NAME + ": Unable to parse id: " + command.GetParameterValue("id"));
			CommandStatus.error.addStatus(agent, command);
			return false;
		}

		logger.debug(String.format(NAME + ": %d", id));
		
		if (messages.removeMessage(id) == false) {
			logger.warn(NAME + ": Unable to remove message " + id + ", no such message");
			CommandStatus.error.addStatus(agent, command);
			return false;
		}

		CommandStatus.accepted.addStatus(agent, command);
		CommandStatus.complete.addStatus(agent, command);
		return true;
	}
}