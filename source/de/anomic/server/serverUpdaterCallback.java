package de.anomic.server;

public interface serverUpdaterCallback {
	public boolean updateYaCyIsPossible();
	public void grantYaCyUpdate();
	public String getYaCyUpdateReleaseVersion();
	//public File getYaCyUpdateReleaseFile();
	public String getYaCyUpdateSource();
	public void signalYaCyShutdown();
	public void signalYaCyRestart();
}