package org.uoc.androidremote.server;

import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

/**
 * Service responsible for starting the server 
 * 
 * @author robertrv [AT] gmail.com
 *
 */
public class ServersWatchDog extends Service {

	/**
	 * Delay until first execution of the Log task.
	 */
	private final long mDelay = 10000;
	/**
	 * Period of the Log task.
	 */
	private final long mPeriod = 2000;
	/**
	 * Log tag for this service.
	 */
	private final String LOGTAG = "ServersWatchDog";
	/**
	 * Timer to schedule the service.
	 */
	private Timer mTimer;

	/**
	 * Implementation of the timer task.
	 */
	private class LogTask extends TimerTask {
		public void run() {
			// Check and start server
			Log.d(LOGTAG, "scheduled at "+ new Date());
		}
	}

	private LogTask mLogTask;

	@Override
	public IBinder onBind(final Intent intent) {
		return null;
	}

	@Override
	public void onCreate() {
		super.onCreate();
		Log.i(LOGTAG, "created");
		mTimer = new Timer();
		mLogTask = new LogTask();
	}

	@Override
	public void onStart(final Intent intent, final int startId) {
		super.onStart(intent, startId);
		Log.i(LOGTAG, "started");
		mTimer.schedule(mLogTask, mDelay, mPeriod);
	}
}