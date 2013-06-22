package org.uoc.androidremote.server;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Iterator;
import java.util.List;

import org.uoc.androidremote.operations.AndroidApplication;
import org.uoc.androidremote.operations.AndroidRunningApplication;
import org.uoc.androidremote.operations.AndroidService;
import org.uoc.androidremote.operations.ApplicationsInstalled;
import org.uoc.androidremote.operations.ApplicationsRunning;
import org.uoc.androidremote.operations.InstallApplication;
import org.uoc.androidremote.operations.LocationOperation;
import org.uoc.androidremote.operations.Operation;
import org.uoc.androidremote.operations.OperationResult;
import org.uoc.androidremote.operations.Reboot;
import org.uoc.androidremote.operations.ServicesRunning;
import org.uoc.androidremote.server.Utils.CommandResult;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.app.ActivityManager.RunningServiceInfo;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.os.BatteryManager;
import android.util.Log;

/**
 * Server thread responsible for management tasks
 * 
 * @author robertrv [at] gmail
 * 
 */
public class ManagementServer extends Thread {

	private static final String LOGTAG = ManagementServer.class.getSimpleName();

	private ServerSocket listeningSocket;
	private boolean keepListening = false;
	private Socket send;
	private Context context;

	private Integer batteryLevel = Integer.valueOf(0);
	static final int DEFAULT_PORT = 5000;
	private int listeningPort = DEFAULT_PORT;

	public ManagementServer(Context context) {
		this.context = context;
	}

	public int getListeningPort() {
		return listeningPort;
	}

	public void setListeningPort(int port) {
		listeningPort = port;
	}

	public void run() {
		try {
			listeningSocket = new ServerSocket(listeningPort);
			Log.i(LOGTAG, "Puerto: " + listeningPort);
			batteryLevel();

			keepListening = true;
			while (keepListening) {
				ObjectOutputStream os = null;
				ObjectInputStream in = null;
				try {
					// listen for incoming clients
					send = listeningSocket.accept();
					os = new ObjectOutputStream(send.getOutputStream());
					os.flush();
					in = new ObjectInputStream(send.getInputStream());

					Operation o = (Operation) in.readObject();
					os.flush();
					switch (o.getId()) {
					case Operation.OP_OPEN:
						os.writeObject(new String("Conexión establecida"));
						Utils.showClientConnected(context, o.getMessage() + " "
								+ send.getInetAddress());
						break;
					case Operation.OP_APPLICATIONS_RUNNING:
						ApplicationsRunning apps = getApplications();
						os.writeObject(apps);
						break;
					case Operation.OP_SERVICES_RUNNING:
						ServicesRunning services = getServicesRunning();
						os.writeObject(services);
						break;
					case Operation.OP_LOCATION_GPS:
						LocationOperation location = getLocation();
						os.writeObject(location);
						break;
					case Operation.OP_APPLICATIONS_INSTALLED:
						ApplicationsInstalled appsInstalled = getInstalledApplications();
						os.writeObject(appsInstalled);
						break;
					case Operation.OP_BATTERY_LEVEL:
						Integer level = getBatteryStatus();
						os.writeObject(level);
						break;
					case Operation.OP_REBOOT:
						Reboot reboot = getRebootResult();
						os.writeObject(reboot);
						break;
					case Operation.OP_INSTALL_APPLICATION:
						OperationResult result = 
							installApplication((InstallApplication) o);
						os.writeObject(result);
						break;
					case Operation.OP_CLOSE:
						os.writeObject(new String("Conexión cerrada"));
						Utils.showClientDisconnected(context);
						break;
					case Operation.OP_ADVICE_SESSION_END:
						// TODO: Clean current state, maybe save installed apps ?
						break;
					default:
						break;
					}
					Log.i(LOGTAG, o.getMessage());
				} catch (SocketException se) {
					if (se.getMessage().indexOf("Interrupted system call") >= 0
							|| se.getMessage().indexOf("Socket closed") >= 0) {
						Log.d(LOGTAG, "Normal closing of management server");
					} else {
						Log.e(LOGTAG,
								"Unexpected socket exception on management server",
								se);
					}
				} catch (Exception e) {
					Log.e(LOGTAG, "Unexpected error on management server", e);
				} finally {
					if (os != null) {
						os.flush();
						os.close();
					}
					if (in != null) {
						in.close();
					}
					if (send != null) {
						send.close();
					}
				}
			}
		} catch (SocketException e) {
			Log.e(LOGTAG, "Detectada socket exception", e);
		} catch (IOException e) {
			Log.e(LOGTAG,
					"Detectada exception entrada salida " + e.getMessage(), e);
		}
	}

	private OperationResult installApplication(InstallApplication installAppOperation) {
		OperationResult result = new OperationResult();
		String path = context.getFilesDir().getAbsolutePath()
				+ File.separator + installAppOperation.getFileName();
		try {
			byte[] buffer = installAppOperation.getFile();
			
			FileOutputStream fos = new FileOutputStream(path);
			fos.write(buffer);
			fos.close();

			Utils.executeAsSu("chmod 777 " + path);

			if (!checkCommandResultAndSetResultMessage(result, 
					Utils.executeAsSu("pm install -r "+path))) {
				return result;
			}

			result.setOkMessage("Sucessfully installed");
			Log.d(LOGTAG, "Sucessfully installed application on path: "+path);
		} catch (Exception e) {
			result.setKoException(e);
			Log.e(LOGTAG, "Error trying to reboot device", e);
		} finally {
			try {
				Utils.executeAsSu("rm "+path);
			} catch (Exception ignored) {
				ignored.printStackTrace();
			}
		}
		return result;
	}

	private boolean checkCommandResultAndSetResultMessage(OperationResult result,
			CommandResult execResult) {
		if (execResult.getStdOutput() == null || !execResult.getStdOutput().contains("Success")) {
			result.setKoMessage(execResult.getStdError()
					+ execResult.getStdOutput());
			return false;
		} else {
			return true;
		}
	}

	/**
	 * Gets the applications.
	 * 
	 * @return the applications
	 */
	private ApplicationsRunning getApplications() {
		ApplicationsRunning apps = new ApplicationsRunning();
		ActivityManager activityManager = (ActivityManager) context
				.getSystemService(Context.ACTIVITY_SERVICE);
		List<RunningAppProcessInfo> applications = activityManager
				.getRunningAppProcesses();
		PackageManager pm = context.getPackageManager();
		for (RunningAppProcessInfo application : applications) {
			AndroidRunningApplication a = new AndroidRunningApplication();
			try {
				a.setName(pm.getApplicationLabel(
						pm.getApplicationInfo(application.processName,
								PackageManager.GET_META_DATA)).toString());
				a.setImportance(application.importance);
				apps.addApp(a);
			} catch (NameNotFoundException e) {
				Log.e(LOGTAG, "Cannot find Application: " + application, e);
			}

		}
		return apps;
	}

	/**
	 * Battery level.
	 */
	private void batteryLevel() {

		BroadcastReceiver batteryLevelReceiver = new BroadcastReceiver() {
			public void onReceive(Context context, Intent intent) {

				int rawlevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL,
						-1);
				int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
				int level = -1;
				if (rawlevel >= 0 && scale > 0) {
					level = (rawlevel * 100) / scale;
				}
				batteryLevel = level;
			}
		};
		IntentFilter batteryLevelFilter = new IntentFilter(
				Intent.ACTION_BATTERY_CHANGED);
		context.registerReceiver(batteryLevelReceiver, batteryLevelFilter);
	}

	/**
	 * Gets the battery status.
	 * 
	 * @return the battery status
	 */
	private Integer getBatteryStatus() {
		return batteryLevel;
	}

	/**
	 * Gets the installed applications.
	 * 
	 * @return the installed applications
	 */
	private ApplicationsInstalled getInstalledApplications() {
		ApplicationsInstalled apps = new ApplicationsInstalled();
		PackageManager pm = context.getPackageManager();
		// get a list of installed apps.
		List<ApplicationInfo> packages = pm
				.getInstalledApplications(PackageManager.GET_META_DATA);
		for (Iterator<ApplicationInfo> iterator = packages.iterator(); iterator
				.hasNext();) {
			ApplicationInfo applicationInfo = (ApplicationInfo) iterator.next();
			AndroidApplication app = new AndroidApplication(
					applicationInfo.packageName, pm.getApplicationLabel(
							applicationInfo).toString());
			apps.addApp(app);
		}
		return apps;
	}

	/**
	 * Gets the services running.
	 * 
	 * @return the services running
	 */
	private ServicesRunning getServicesRunning() {
		ServicesRunning servicesRunning = new ServicesRunning();
		ActivityManager activityManager = (ActivityManager) context
				.getSystemService(Context.ACTIVITY_SERVICE);
		List<RunningServiceInfo> services = activityManager
				.getRunningServices(30);
		PackageManager pm = context.getPackageManager();
		for (Iterator<RunningServiceInfo> iterator = services.iterator(); iterator
				.hasNext();) {
			RunningServiceInfo runningServiceInfo = iterator.next();
			try {
				servicesRunning.addService(new AndroidService(
						runningServiceInfo.pid, pm.getApplicationLabel(
								pm.getApplicationInfo(
										runningServiceInfo.process,
										PackageManager.GET_META_DATA))
								.toString()));
			} catch (NameNotFoundException e) {
				throw new RuntimeException(e);
			}
		}
		return servicesRunning;
	}

	/**
	 * Gets the location.
	 * 
	 * @return the location
	 */
	private LocationOperation getLocation() {
		LocationManager mlocManager = (LocationManager) context
				.getSystemService(Context.LOCATION_SERVICE);
		Criteria crit = new Criteria();
		crit.setAccuracy(Criteria.ACCURACY_FINE);
		String provider = mlocManager.getBestProvider(crit, true);
		Location loc = mlocManager.getLastKnownLocation(provider);
		if (loc == null) {
			// Means GPS is inactive, try with other providers
			for (String currentProvider : mlocManager.getProviders(true)) {
				loc = mlocManager.getLastKnownLocation(currentProvider);
				if (loc != null) {
					break;
				}
			}
		}
		if (loc != null) {
			return new LocationOperation(loc.getLatitude(), loc.getAltitude());
		} else {
			return new LocationOperation(
					"Error trying to get location, no position available "
							+ "right now.");
		}
	}

	private Reboot getRebootResult() {
		Reboot result = new Reboot();
		try {
			Process sh = Runtime.getRuntime().exec("su");
			OutputStream os = sh.getOutputStream();

			/*
			 * TODO R: Think about options to reboot - Stop all the processes -
			 * Prepare processes to be started after reboot ? Maybe an start
			 * process which call wait-for-devices ?
			 */
			Utils.writeCommand(os, "reboot -n");

			result.setResult(true);
		} catch (Exception e) {
			result.setResult(false);
			result.setProblemMessage(e.getMessage());
			Log.e(LOGTAG, "Error trying to reboot device", e);
		}
		return result;
	}

	public void stopListening() {
		try {
			keepListening = false;
			if (listeningSocket != null) {
				listeningSocket.close();
			}
		} catch (IOException e) {
			Log.e(LOGTAG, "Error trying to close the socket", e);
		}
	}

	public boolean isListeining() {
		return keepListening;
	}

}