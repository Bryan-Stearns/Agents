package edu.umich.soar.robot;

public interface WaypointInterface {
	void addWaypoint(double[] pos, String id, boolean useFloatYawWmes);
	boolean disableWaypoint(String id);
	boolean enableWaypoint(String id, OffsetPose splinter);
	boolean removeWaypoint(String id);
}