package de.anomic.server;

public interface serverUpdaterCallback {
	public boolean updateIsPossible();
	public String getUpdateReleaseFileName();
	public String getUpdateSource();	
	public void grantUpdate();
}
