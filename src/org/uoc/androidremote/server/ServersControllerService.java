package org.uoc.androidremote.server;

import java.util.Timer;

import org.uoc.androidremote.server.Constants.ServiceAction;
import org.uoc.androidremote.server.Constants.ServiceReply;

import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.os.SystemClock;
import android.util.Log;

/**
 * Service responsible for looking at the server, in case servers are down 
 * just starts them.
 * 
 * Is started when device starts in order to control its state.
 * 
 * Will receive 3 kind of Messages:
 *  <ul>
 *  <li>Start Service (Server id): Will start this server</li>
 *  <li>Stop Service (Server id): Will stop this server, if running</li>
 *  <li>boot process: Will start watch dog and ask him to start servers.</li>
 *  </ul>
 * 
 * Probably will try to reply the client with their corresponding replies,
 * <ul>
 * <li>Start finished</li>
 * <li>Stop finished</li>
 * <li>Init finished</li>
 * </ul>
 * 
 * <p>All the start and stop will be separate depending on the server is trying 
 * to be started.</p>
 * 
 * <p>The init process will also start a process to manage see take care of 
 * the processes and start or stop when is needed.</p>
 *  
 * @see ServiceAction
 * @see ServiceReply
 * 
 * @author robertrv [AT] gmail.com
 *
 */
public class ServersControllerService extends Service {

	/* Dog watch attributes*/
	/** Delay until first execution of the Watch Dog.*/
	private final long mDelay = 10000;
	/** Period of the Log task. */
	private final long mPeriod = 10000;
	private final String LOGTAG = ServersControllerService.class.getSimpleName();
	/** Timer to schedule the service. */
	private Timer mTimer;
	private ServersWatchDog mWathDogTask;
	
	private LocalSocketServer localSocketServer;

	/* Service communication state attributes */
	/** Unique client which we can/has to reply */
	public Messenger mClient;
    /** Target we publish for clients to send messages to IncomingHandler. */
    private final Messenger mMessenger = new Messenger(new IncomingHandler());

	/* business state attributes */
	private boolean cachedVncRunning = false;
	private boolean cachedMngRunning = false;
	
	private ManagementServer mngServer;
	private VncServerWrapper vncWrapper;
	
	@Override
	public void onCreate() {
		super.onCreate();
		Log.i(LOGTAG, "created");
		mTimer = new Timer();
		mWathDogTask = new ServersWatchDog(this);
		mTimer.schedule(mWathDogTask, mDelay, mPeriod);
		
		localSocketServer = new LocalSocketServer(getApplicationContext());
		localSocketServer.start();

	}
	
	@Override
	public void onDestroy() {
		super.onDestroy();
		mWathDogTask.cancel();
		mTimer.cancel();
		Log.v(LOGTAG, "Finished stopping watchdog. ScheduledExectionTime? "
				+ mWathDogTask.scheduledExecutionTime());

		Log.v(LOGTAG, "Executing the onDestroy, going to free all stuff");
		// Try to stop watch dog and stop servers
		stopVncServer();
		Log.v(LOGTAG, "Stopping vnc finished, is running ? "+ isVncRunning());
		stopMngServer();
		Log.v(LOGTAG, "Stopping mng finished, is running ? "+ isMngRunning());
		localSocketServer.stopListening();
		Log.v(LOGTAG,
				"Stopped local server. Listening ? "
						+ localSocketServer.isListening());
		
		this.stopSelf();
		System.runFinalizersOnExit(true);
		System.exit(0);
	}
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		// the stopping of this service is explicitly, so avoiding normal
		// stopping of service
		Log.d(LOGTAG, "onStartCommand called");
		if (intent != null && intent.getExtras() != null) {
			Bundle extras = intent.getExtras();
			if (extras.getBoolean("startVnc", false)) {
				initVnc(extras.getInt("vncPort", VncServerWrapper.DEFAULT_PORT),
						extras.getInt("scaleFactor",
								VncServerWrapper.DEFAULT_SCALE_FACTOR),
						extras.getInt("rotation",
								VncServerWrapper.DEFAULT_ROTATION_FACTOR)
						);
			} else {
				stopVnc();
			}
			if (extras.getBoolean("startMng", false)) {
				initMng(extras.getInt("mngPort", ManagementServer.DEFAULT_PORT));
			} else {
				stopMng();
			}
			
		}
		return START_STICKY;
	}
	
	/**
     * Handler of incoming messages from clients.
     */
    class IncomingHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
        	if (msg.replyTo != null) {
            	ServersControllerService.this.mClient = msg.replyTo;        		
        	}
            switch (ServiceAction.fromCode(msg.what)) {
                case START_VNC:
                	cachedVncRunning = false;
					int vncPort = getIntOrDefault(msg,
							VncServerWrapper.DEFAULT_PORT,
							ServersControllerActivity.PORT_BUNDLE_KEY);
					int scaleFactor = getIntOrDefault(msg,
							VncServerWrapper.DEFAULT_SCALE_FACTOR,
							ServersControllerActivity.SCALE_FACTOR_BUNDLE_KEY);
					int rotation = getIntOrDefault(msg,
							VncServerWrapper.DEFAULT_ROTATION_FACTOR,
							ServersControllerActivity.ROTATION_BUNDLE_KEY);

                	ServersControllerService.this.mWathDogTask
						.setToRestartVnc(true, vncPort, scaleFactor, rotation);
                	
                	ServersControllerService.this.tryStartVnc(vncPort, 
                			scaleFactor, rotation);
                    break;
                case STOP_VNC:
                	cachedVncRunning = true;
					ServersControllerService.this.mWathDogTask.setToRestartVnc(
							false, VncServerWrapper.DEFAULT_PORT,
							VncServerWrapper.DEFAULT_SCALE_FACTOR,
							VncServerWrapper.DEFAULT_ROTATION_FACTOR);
            		ServersControllerService.this.tryStopVnc();                	
                	break;
                case START_MNG:
                	cachedMngRunning = false;
					int mngPort = getIntOrDefault(msg,
							ManagementServer.DEFAULT_PORT,
							ServersControllerActivity.PORT_BUNDLE_KEY);

                	ServersControllerService.this.mWathDogTask
						.setToRestartMng(true, mngPort);

                	ServersControllerService.this.tryStartMng(mngPort);                	
                    break;
                case STOP_MNG:
                	cachedMngRunning = true;
                	ServersControllerService.this.mWathDogTask
						.setToRestartMng(false, ManagementServer.DEFAULT_PORT);
            		ServersControllerService.this.tryStopMng();                	
                	break;
                case GET_ALL_SERVER_STATUS:
            		ServersControllerService.this.sendServersState();                	
                	break;
                default:
                    super.handleMessage(msg);
            }
        }
    }
    
    private int getIntOrDefault(Message msg, int defaultValue, String key) {
    	int intFound = defaultValue;
		if (msg.getData() != null
				&& msg.getData().getInt(key) > 0) {
			intFound = msg.getData().getInt(key);
		}
		return intFound;

    }
	
	@Override
	public IBinder onBind(final Intent intent) {
		return mMessenger.getBinder();
	}
	
	@Override
	public boolean onUnbind(Intent intent) {
		// When all the clients (probably just one) does un-bind to the 
		// service, just avoid sending messages back.
		mClient = null;
		return super.onUnbind(intent);
	}

	/* Business methods */
	void tryStartMng(int port) {
    	int resId = R.string.managementServerStillRunning;
    	if (!isMngRunning()) {
        	Log.d(LOGTAG, "trying to start mng, was not started !");
        	startMngServer(port);
        	resId = R.string.mngStarted;
    	} else {
    		Log.d(LOGTAG, "trying to start mng, but already running !");
    	}
    	sendMngReply(getString(resId));
    	Log.d(LOGTAG, "mng started command finished");		
	}

    void tryStopMng() {
		int resId = R.string.managementNotRunning;
		if (isMngRunning()) {
	    	Log.d(LOGTAG, "trying to stop mng, was started !");
	    	stopMngServer();
	    	resId = R.string.mngStopped;
		}
    	sendMngReply(getString(resId));
    	Log.d(LOGTAG, "mng stop command finished");
	}

	void tryStartVnc(int port, int scaleFactor, int rotation) {
    	int resId = R.string.vncServerStillRunning;
    	if (!isVncRunning()) {
        	Log.d(LOGTAG, "trying to start vnc, was not started !");
        	startVncServer(port,scaleFactor, rotation);
        	resId = R.string.vncStarted;
    	}
    	sendVncReply(getString(resId));
    	Log.d(LOGTAG, "vnc started command finished");		
	}

	void tryStopVnc() {
		try {
			int resId = R.string.vncNotRunning;
			if (isVncRunning()) {
				Log.d(LOGTAG, "trying to stop vnc, was started !");

				stopVncServer();
				resId = R.string.vncStopped;
			}
			sendVncReply(getString(resId));
		} catch (VncException e) {
			sendClientMessage(e.getMessage());
		}
	}

	private void startVncServer(int port, int scaleFactor, int rotation)
			throws VncException {
		VncServerWrapper wrapper = getVncWrapper();
		wrapper.setListeningPort(port);
		wrapper.setScaleFactor(scaleFactor);
		wrapper.setRotation(rotation);
		wrapper.startVncServer();
	}

	private void stopVncServer() throws VncException {
		getVncWrapper().stopVncServer();
	}

	private void initVnc(int port, int factor, int rotation) {
		tryStartVnc(port,factor,rotation);
		mWathDogTask.setToRestartVnc(true, port, factor, rotation);
	}
	
	private void stopVnc() {
		mWathDogTask.setToRestartVnc(false, -1, -1, -1);
		tryStopVnc();
	}
	
	private void initMng(int port) {
		tryStartMng(port);
		mWathDogTask.setToRestartMng(true, port);
	}
	
	private void stopMng() {
		mWathDogTask.setToRestartMng(false, -1);		
		tryStopMng();
	}
	
	boolean isVncRunning() {
		boolean result = VncServerWrapper.isRunning();
		Log.d(LOGTAG, "Is vnc running? "+result);
		return result;
	}
	
	boolean isMngRunning() {
		boolean result = mngServer != null && mngServer.isListeining();
		Log.d(LOGTAG, "Is mng running? "+result);
		return result;
	}
	
	private void startMngServer(int port) {
		ManagementServer server = getMngServer();
		server.setListeningPort(port);
		server.start();
		int numLoops = 0;
		do {
	    	SystemClock.sleep(20);
		} while (!isMngRunning() && numLoops++<10);
		Log.i(LOGTAG, "Management server started");
	}
	
	private void stopMngServer() {
		if (mngServer != null) {
			mngServer.stopListening();
			mngServer = null;			
		}
		Log.i(LOGTAG, "Management server stopped");				
	}
	
	private void sendServersState() {
		cachedMngRunning = false;
		cachedVncRunning = false;
		sendMngReply(null);
		sendVncReply(null);
	}

	private int getMngPort(){
		return getMngServer().getListeningPort();
	}
	
	private int getVncPort(){
		return getVncWrapper().getListeningPort();
	}
	
	private void sendMngReply(String message) {
		boolean currentMngRunning = isMngRunning();
		if (cachedMngRunning != currentMngRunning) {
			sendClientSrvState(currentMngRunning, ServiceReply.MNG_STARTED.what,
					ServiceReply.MNG_STOPPED.what, getMngPort(), message);
		}
		cachedMngRunning = currentMngRunning;
	}

	private void sendVncReply(String message) {
		boolean currentVncRunning = isVncRunning();
		if (cachedVncRunning != currentVncRunning) {
			sendClientSrvState(currentVncRunning, ServiceReply.VNC_STARTED.what,
					ServiceReply.VNC_STOPPED.what, getVncPort(), message);
		}
		cachedVncRunning = currentVncRunning;
	}

	private void sendClientSrvState(boolean currentRunning, int startedState,
			int stoppedState, int port, String message) {
		// Should notify client if is present
		if (mClient != null) {
			int state;
			if (currentRunning) {
				state = startedState;
			} else {
				state = stoppedState;
			}
			
			try {
				mClient.send(Message.obtain(null, state, port, 0, message));
			} catch (RemoteException e) {
				Log.e(LOGTAG, "Error sending message back to client", e);
			}
		} else {
			Log.e(LOGTAG, "something strange happened because client is not " +
					"initialized !");
		}
	}
	
	private void sendClientMessage(String message) {
		if (mClient != null) {
			try {
				mClient.send(Message.obtain(null,
						ServiceReply.SHOW_MESSAGE.what, message));
			} catch (RemoteException e) {
				Log.e(LOGTAG, "Error sending message back to client", e);
			}
		} else {
			Log.e(LOGTAG, "something strange happened because client is not " +
					"initialized !");
		}
	}

	private ManagementServer getMngServer() {
		if (mngServer == null) {
			mngServer = new ManagementServer(getApplicationContext());
		}
		return mngServer;
	}
	
	private VncServerWrapper getVncWrapper() {
		if (vncWrapper == null) {
			vncWrapper = new VncServerWrapper(getApplicationContext());
		}
		return vncWrapper;
	}
}