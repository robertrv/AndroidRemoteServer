package org.uoc.androidremote.server;

import java.util.Date;
import java.util.TimerTask;

import android.util.Log;

/**
 * Watchdog to start/stop servers when are not stopped
 * 
 * As far as cannot exists more than one watchdog on the same server at the 
 * same time we will make this class as a singleton
 * 
 * @author robertrv [at] gmail
 *
 */
public class ServersWatchDog extends TimerTask {

	/* AR Service state attributes */
	private Boolean shouldVncBeStarter = false;
	
	private Boolean shouldMngBeStarted = false;
	
	ServersControllerService service;
	
	public ServersWatchDog(ServersControllerService service) {
		super();
		this.service = service;
		Log.i(this.getClass().getSimpleName(), "Servers watch dog started");
	}
	
	private final String LOGTAG = ServersWatchDog.class.getSimpleName();

	public void run() {
		logServerStates("Starting to watch server at ");
		normalizeServersStates();
		logServerStates("Finished watching servers at ");
	}

	private void logServerStates(String initial) {
		StringBuilder msg = new StringBuilder(initial);
		msg.append(new Date());
		buildStatus(msg);
		Log.d(LOGTAG, msg.toString());
	}
	
	/**
	 * Real business logic, tries depending if is needed to restart, tries to 
	 * restart any server, otherwise try to stop this server.
	 */
	private void normalizeServersStates() {
		if (shouldVncBeStarter) {
			service.tryStartVnc();
		} else {
			service.tryStopVnc();
		}
		
		if (shouldMngBeStarted) {
			service.tryStartMng();
		} else {
			service.tryStopMng();
		}		
	}

	private void buildStatus(StringBuilder sb) {
		sb.append(". should/is vnc be running ? ");
		sb.append(shouldVncBeStarter);
		sb.append("/");
		sb.append(service.isVncRunning());
		sb.append(". should/is mng be running ? ");
		sb.append(shouldMngBeStarted);
		sb.append("/");
		sb.append(service.isMngRunning());
		sb.append(". ");
	}

	public boolean hasToRestartVnc() {
		synchronized (shouldVncBeStarter) {
			return shouldVncBeStarter;			
		}
	}
	public void setToRestartVnc(boolean whish) {
		synchronized (shouldVncBeStarter) {
			shouldVncBeStarter = whish;			
		}		
	}
	
	public boolean hasToRestartMng() {
		synchronized (shouldMngBeStarted) {
			return shouldMngBeStarted;			
		}
	}
	public void setToRestartMng(boolean whish) {
		synchronized (shouldMngBeStarted) {
			shouldMngBeStarted = whish;			
		}		
	}
	
}
