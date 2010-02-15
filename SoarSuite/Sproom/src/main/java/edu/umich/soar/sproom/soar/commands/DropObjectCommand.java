/**
 * 
 */
package edu.umich.soar.sproom.soar.commands;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import edu.umich.soar.sproom.Adaptable;
import edu.umich.soar.sproom.SharedNames;
import edu.umich.soar.sproom.command.Pose;
import edu.umich.soar.sproom.command.VirtualObject;
import edu.umich.soar.sproom.command.VirtualObjects;
import edu.umich.soar.sproom.soar.Cargo;

import sml.Identifier;

/**
 * @author voigtjr
 *
 * Removes all messages from message list.
 */
public class DropObjectCommand extends OutputLinkCommand {
	private static final Log logger = LogFactory.getLog(DropObjectCommand.class);
	static final String NAME = "drop-object";

	private final Identifier wme;
	private int id;
	
	public DropObjectCommand(Identifier wme) {
		super(wme);
		this.wme = wme;
	}

	@Override
	protected OutputLinkCommand accept() {
		String idString = wme.GetParameterValue(SharedNames.ID);
		try {
			id = Integer.parseInt(idString);
		} catch (NullPointerException e) {
			return new InvalidCommand(wme, "No " + SharedNames.ID);
		} catch (NumberFormatException e) {
			return new InvalidCommand(wme, "Error parsing " + SharedNames.ID + ": " + idString);
		}
		
		logger.debug(id);
		addStatus(CommandStatus.ACCEPTED);
		return this;
	}

	@Override
	public void update(Adaptable app) {
		Cargo cargo = (Cargo)app.getAdapter(Cargo.class);
		if (!cargo.isCarrying()) {
			addStatus(CommandStatus.ERROR, "Not carrying an object.");
			return;
		}

		VirtualObject object = cargo.getCarriedObject();
		Pose pose = (Pose)app.getAdapter(Pose.class);
		VirtualObjects vobjs = (VirtualObjects)app.getAdapter(VirtualObjects.class);

		object.setPos(pose.getPose().pos);
		vobjs.addObject(object);
		cargo.setCarriedObject(null);
		addStatus(CommandStatus.COMPLETE);
		return;
	}

}
