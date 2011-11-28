/*
 *  This file is part of Android Remote.
 *
 *  Android Remote is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Leeser General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  any later version.
 *
 *  Android Remote is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Leeser General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with Foobar.  If not, see <http://www.gnu.org/licenses/>.
 */

/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.uoc.androidremote.server;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;

import org.uoc.androidremote.operations.AndroidApplication;
import org.uoc.androidremote.operations.AndroidLocation;
import org.uoc.androidremote.operations.AndroidRunningApplication;
import org.uoc.androidremote.operations.AndroidService;
import org.uoc.androidremote.operations.ApplicationsInstalled;
import org.uoc.androidremote.operations.ApplicationsRunning;
import org.uoc.androidremote.operations.Operation;
import org.uoc.androidremote.operations.ServicesRunning;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.app.ActivityManager.RunningServiceInfo;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.Color;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.ClipboardManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Main android activity responsible for starting and stopping android servers
 */
public class ActivityServer extends Activity {
	
	/** The Constant MENU_QUIT. */
	private static final int MENU_QUIT = 0;
	
	/** The Constant APP_ID. */
	private static final int APP_ID = 1206;
	
	/** The SOCKE t_ address. */
	public static String SOCKET_ADDRESS = 
			"/data/data/org.uoc.androidremote.server.localsocket";

	/** The dialog. */
	ProgressDialog dialog = null;
	
	/** The start dialog. */
	AlertDialog startDialog;

	/** The SERVERIP. */
	public static String SERVERIP = "10.0.2.15";

	/** The Constant PORT. */
	private static final int PORT = 5000;

	/** The socket. */
	private ServerSocket socket;
	
	/** The send. */
	private Socket send;

	/** The management thread. */
	private Thread managementThread;
	
	@Override
	protected void onDestroy() {
		super.onDestroy();
		unregisterReceiver(mReceiver);
	}

	/**
	 * Called when the activity is first created.
	 * 
	 * @param savedInstanceState
	 *            the saved instance state
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.main);

		comprobarBusybox();

		if (!hasRootPermission()) {
			startDialog.dismiss();

			Log.v("VNC", "You don't have root permissions...!!!");
			startDialog = new AlertDialog.Builder(this).create();
			startDialog.setTitle("Cannot continue");
			startDialog
					.setMessage("You don't have root permissions.\nPlease root your phone first!\n\nDo you want to try out anyway?");
			startDialog.setIcon(R.drawable.icon);
			startDialog.setButton("Yes", new DialogInterface.OnClickListener() {

				@Override
				public void onClick(DialogInterface arg0, int arg1) {
					startDialog.dismiss();
				}
			});
			startDialog.setButton2("No", new DialogInterface.OnClickListener() {

				@Override
				public void onClick(DialogInterface arg0, int arg1) {
					System.exit(0);
				}
			});
			startDialog.show();
		}

		// register wifi event receiver
		registerReceiver(mReceiver, new IntentFilter(
				ConnectivityManager.CONNECTIVITY_ACTION));

		SocketListener s = new SocketListener();
		s.start();

		setStateLabels();

		findViewById(R.id.Button01).setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View arg0) {
				startVncServerOnDifferentThread();
				SystemClock.sleep(500);
				setStateLabels();
			}
		});
		findViewById(R.id.Button02).setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View arg0) {
				if (!isVncServerRunning()) {
					showTextOnScreen("Server is not running");
					return;
				}

				stopVncServer();
				SystemClock.sleep(500);
				setStateLabels();
			}
		});

		findViewById(R.id.ButtonGestionStart).setOnClickListener(
				new OnClickListener() {
					@Override
					public void onClick(View arg0) {
						if (!isManagementServerRunning()) {
							Log.i("AndroidRemote", "HILO NULL");
							startManagementServer();
							return;
						}
					}
				});
		findViewById(R.id.ButtonGestionStop).setOnClickListener(
				new OnClickListener() {
					@Override
					public void onClick(View arg0) {
						if (!isManagementServerRunning()) {
							showTextOnScreen("Server is not running");
							return;
						}

						// prepareWatchdog("Stopping server. Please wait...","Couldn't Stop server",false);

						managementThread.stop();
						managementThread = null;
						setStateLabels();
						return;
					}
				});
		findViewById(R.id.refreshButton).setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				setStateLabels();
			}
		});
	}

	/**
	 * Checks for busybox.
	 * 
	 * @return true, if successful
	 */
	public static boolean hasBusybox() {
		File busyboxFile = findExecutableOnPath("busybox");
		return busyboxFile != null;
	}

	/**
	 * Comprobar busybox.
	 * 
	 * @return true, if successful
	 */
	public boolean comprobarBusybox() {
		boolean has = hasBusybox();
		startDialog = new AlertDialog.Builder(this).create();
		if (!has) {
			Log.v("VNC", "Busybox not found...!!!");
			startDialog.setTitle("Cannot continue");
			startDialog
					.setMessage("I didn't found busybox in your device, do you want to install it from the market?\nYou can try to run without it.\n(I am not responsible for this application)");
			startDialog.setIcon(R.drawable.icon);
			startDialog.setButton("Yes, install it",
					new DialogInterface.OnClickListener() {

						@Override
						public void onClick(DialogInterface arg0, int arg1) {
							Intent myIntent = new Intent(
									Intent.ACTION_VIEW,
									Uri.parse("market://details?id=stericson.busybox"));
							startActivity(myIntent);
						}
					});
			startDialog.setButton2("No, let me try without it",
					new DialogInterface.OnClickListener() {

						@Override
						public void onClick(DialogInterface arg0, int arg1) {
							startDialog.dismiss();
						}
					});
			startDialog.show();

		}
		return has;
	}

	/**
	 * Show text on screen.
	 * 
	 * @param t
	 *            the t
	 */
	public void showTextOnScreen(final String t) {
		runOnUiThread(new Runnable() {
			public void run() {
				Toast.makeText(ActivityServer.this, t, Toast.LENGTH_LONG)
						.show();
			}
		});
	}

	/* (non-Javadoc)
	 * @see android.app.Activity#onCreateOptionsMenu(android.view.Menu)
	 */
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.menu, menu);

		menu.add(0, MENU_QUIT, 0, "Close");

		return true;
	}

	
	/**
	 * Set the state labels depending on current status of sockets and internal
	 * server information.
	 */
	public void setStateLabels() {
		setStateLabels(isVncServerRunning(), isManagementServerRunning());
	}
	/**
	 * Sets the state labels.
	 * 
	 * @param runningVnc
	 *            the state vnc
	 * @param runningManagement
	 *            the state gestion
	 */
	public void setStateLabels(boolean runningVnc, boolean runningManagement) {
		TextView stateLabel = (TextView) findViewById(R.id.stateLabel);
		stateLabel.setText(runningVnc ? "Running" : "Stopped");
		stateLabel.setTextColor(runningVnc ? Color.GREEN : Color.RED);

		Button btnStart = (Button) findViewById(R.id.Button01);
		Button btnStop = (Button) findViewById(R.id.Button02);
		btnStart.setEnabled(!runningVnc);
		btnStop.setEnabled(runningVnc);
		TextView t = (TextView) findViewById(R.id.TextView01);

		if (runningVnc) {
			String port = "5901";
			String httpport;
			try {
				int port1 = Integer.parseInt(port);
				port = String.valueOf(port1);
				httpport = String.valueOf(port1 - 100);
			} catch (NumberFormatException e) {
				port = "5901";
				httpport = "5801";
			}

			String ip = getIpAddress();
			if (ip.equals("")) {
				t.setText("http://localhost:" + httpport+"\n IP:localhost Port: "+port);
			} else {
				t.setText("http://" + ip + ":" + httpport+"\n IP:"+ip+" Port: "+port);
			}
		} else {
			t.setText("");			
		}

		TextView stateGestionLabel = (TextView) findViewById(R.id.stateGestionLabel);
		stateGestionLabel.setText(runningManagement ? "Running" : "Stopped");
		stateGestionLabel.setTextColor(runningManagement ? Color.GREEN : Color.RED);

		Button btnStartGestion = (Button) findViewById(R.id.ButtonGestionStart);
		Button btnStopGestion = (Button) findViewById(R.id.ButtonGestionStop);
		btnStartGestion.setEnabled(!runningManagement);
		btnStopGestion.setEnabled(runningManagement);
		TextView tGestion1 = (TextView) findViewById(R.id.TextViewGestion01);

		if (runningManagement) {

			String ip = getIpAddress();
			if (ip.equals("")) {
				tGestion1.setText("IP: localhost Puerto: " + PORT);
			} else {
				tGestion1.setText("IP: " + ip + " Puerto: " + PORT);
			}
		} else {
			tGestion1.setText("");			
		}
	}

	/**
	 * Gets the ip address.
	 * 
	 * @return the ip address
	 */
	public String getIpAddress() {
		try {
			for (Enumeration<NetworkInterface> en = NetworkInterface
					.getNetworkInterfaces(); en.hasMoreElements();) {
				NetworkInterface intf = en.nextElement();
				for (Enumeration<InetAddress> enumIpAddr = intf
						.getInetAddresses(); enumIpAddr.hasMoreElements();) {
					InetAddress inetAddress = enumIpAddr.nextElement();
					if (!inetAddress.isLoopbackAddress()) {
						return inetAddress.getHostAddress().toString();
					}
				}
			}
		} catch (SocketException ex) {
			Log.e("VNC", ex.toString());
		}
		return "";
	}

	/**
	 * Stop server.
	 */
	public void stopVncServer() {
		try {
			Process sh;

			sh = Runtime.getRuntime().exec("su");
			OutputStream os = sh.getOutputStream();

			if (hasBusybox()) {
				writeCommand(os, "busybox killall androidvncserver");
				writeCommand(os, "busybox killall -KILL androidvncserver");
			} else {
				writeCommand(os, "killall androidvncserver");
				writeCommand(os, "killall -KILL androidvncserver");
				if (findExecutableOnPath("killall") == null) {
					showTextOnScreen("I couldn't find the killall executable, please install busybox or i can't stop server");
					Log.v("VNC",
							"I couldn't find the killall executable, please install busybox or i can't stop server");
				}
			}

			writeCommand(os, "exit");

			os.flush();
			os.close();

			// lets clear notifications
			String ns = Context.NOTIFICATION_SERVICE;
			NotificationManager mNotificationManager = (NotificationManager) getSystemService(ns);
			mNotificationManager.cancel(APP_ID);
		} catch (IOException e) {
			showTextOnScreen("stopServer()" + e.getMessage());
			Log.v("VNC", "stopServer()" + e.getMessage());
		} catch (Exception e) {
			Log.v("VNC", "stopServer()" + e.getMessage());
		}

	}

	/**
	 * Start server button clicked.
	 */
	public void startVncServerOnDifferentThread() {
		if (isVncServerRunning())
			showTextOnScreen("Server is already running, stop it first");
		else {
			// prepareWatchdog("Starting server. Please wait...","Couldn't Start server",
			// true);

			Thread t = new Thread() {
				public void run() {
					startVncServer();
				}
			};
			t.start();
		}
	}

	/**
	 * Start server gestion button clicked.
	 */
	public void startManagementServer() {
		if (!isManagementServerRunning()) {
			managementThread = new Thread(new ServerThread());
			managementThread.start();
			Log.i("AndroidRemote", "Management server started");
			setStateLabels();			
		} else {
			showTextOnScreen(getString(R.string.managementServerStillRunning));
		}
	}

	/**
	 * Start server.
	 */
	public void startVncServer() {
		try {
			Process sh;

			String password = "";
			String password_check = "";
			if (!password.equals(""))
				password_check = "-p " + password;

			String rotation = "0";
			rotation = "-r " + rotation;

			String scaling = "100";

			String scaling_string = "";
			if (!scaling.equals("0"))
				scaling_string = "-s " + scaling;

			String port = "5901";

			String tm = "0";
			String testmode = "-t " + tm;
			try {
				int port1 = Integer.parseInt(port);
				port = String.valueOf(port1);
			} catch (NumberFormatException e) {
				port = "5901";
			}
			String port_string = "-P " + port;

			sh = Runtime.getRuntime().exec("su");
			OutputStream os = sh.getOutputStream();

			writeCommand(os, "chmod 777 " + getFilesDir().getAbsolutePath()
					+ "/androidvncserver");
			writeCommand(os, getFilesDir().getAbsolutePath()
					+ "/androidvncserver " + password_check + " " + rotation
					+ " " + scaling_string + " " + port_string + " " + testmode);

			// dont show password on logcat
			Log.v("VNC", "Starting " + getFilesDir().getAbsolutePath()
					+ "/androidvncserver " + " " + rotation + " "
					+ scaling_string + " " + port_string + " " + testmode);

		} catch (IOException e) {
			Log.v("VNC", "startServer():" + e.getMessage());
			showTextOnScreen("startServer():" + e.getMessage());
		} catch (Exception e) {
			Log.v("VNC", "startServer():" + e.getMessage());
			showTextOnScreen("startServer():" + e.getMessage());
		}

	}

	// This method is called once the menu is selected
	/* (non-Javadoc)
	 * @see android.app.Activity#onOptionsItemSelected(android.view.MenuItem)
	 */
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		
		case MENU_QUIT:
			System.exit(1);
			break;
		}
		return true;
	}
	
	private boolean isManagementServerRunning() {
		return managementThread != null;
	}

	private static boolean isVncServerRunning() {
		String result = "";
		Process sh;
		try {
			if (hasBusybox()) {
				sh = Runtime.getRuntime().exec("busybox ps w");
			} else {
				if (findExecutableOnPath("ps") == null) {
					new RuntimeException(
						"I cant find the ps executable, please install busybox " +
						"or i'm wont be able to check server state");					
				}
				sh = Runtime.getRuntime().exec("ps");
			}

			InputStream is = sh.getInputStream();
			InputStreamReader isr = new InputStreamReader(is);
			BufferedReader br = new BufferedReader(isr);
			String line;

			while ((line = br.readLine()) != null) {
				result += line;
				if (result.indexOf("androidvncserver") > 0) {
					Log.v("VNC", "isAndroidServerRunning? yes");
					return true;
				}
			}

		} catch (Exception e) {
			Log.v("VNC", " isAndroidServerRunning():" + e.getMessage());
		}

		Log.v("VNC", "isAndroidServerRunning? no");
		return false;
	}

	private boolean hasRootPermission() {
		boolean rooted = true;
		try {
			File su = new File("/system/bin/su");
			if (su.exists() == false) {
				su = new File("/system/xbin/su");
				if (su.exists() == false) {
					rooted = false;
				}
			}
		} catch (Exception e) {
			Log.v("VNC",
					"Can't obtain root - Here is what I know: "
							+ e.getMessage());
			rooted = false;
		}

		return rooted;
	}

	/** The Constant LOG_COLLECTOR_PACKAGE_NAME. */
	public static final String LOG_COLLECTOR_PACKAGE_NAME = "com.xtralogic.android.logcollector";//$NON-NLS-1$
	
	/** The Constant ACTION_SEND_LOG. */
	public static final String ACTION_SEND_LOG = "com.xtralogic.logcollector.intent.action.SEND_LOG";//$NON-NLS-1$
	
	/** The Constant EXTRA_SEND_INTENT_ACTION. */
	public static final String EXTRA_SEND_INTENT_ACTION = "com.xtralogic.logcollector.intent.extra.SEND_INTENT_ACTION";//$NON-NLS-1$
	
	/** The Constant EXTRA_DATA. */
	public static final String EXTRA_DATA = "com.xtralogic.logcollector.intent.extra.DATA";//$NON-NLS-1$
	
	/** The Constant EXTRA_ADDITIONAL_INFO. */
	public static final String EXTRA_ADDITIONAL_INFO = "com.xtralogic.logcollector.intent.extra.ADDITIONAL_INFO";//$NON-NLS-1$
	
	/** The Constant EXTRA_SHOW_UI. */
	public static final String EXTRA_SHOW_UI = "com.xtralogic.logcollector.intent.extra.SHOW_UI";//$NON-NLS-1$
	
	/** The Constant EXTRA_FILTER_SPECS. */
	public static final String EXTRA_FILTER_SPECS = "com.xtralogic.logcollector.intent.extra.FILTER_SPECS";//$NON-NLS-1$
	
	/** The Constant EXTRA_FORMAT. */
	public static final String EXTRA_FORMAT = "com.xtralogic.logcollector.intent.extra.FORMAT";//$NON-NLS-1$
	
	/** The Constant EXTRA_BUFFER. */
	public static final String EXTRA_BUFFER = "com.xtralogic.logcollector.intent.extra.BUFFER";//$NON-NLS-1$

	/**
	 * Write command.
	 * 
	 * @param os
	 *            the os
	 * @param command
	 *            the command
	 * @throws Exception
	 *             the exception
	 */
	static void writeCommand(OutputStream os, String command) throws Exception {
		os.write((command + "\n").getBytes("ASCII"));
	}

	/** The m receiver. */
	public BroadcastReceiver mReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			NetworkInfo info = intent
					.getParcelableExtra(ConnectivityManager.EXTRA_NETWORK_INFO);
			if (info.getType() == ConnectivityManager.TYPE_MOBILE
					|| info.getType() == ConnectivityManager.TYPE_WIFI) {
				setStateLabels();
			}

		}
	};

	/**
	 * Show client connected.
	 * 
	 * @param c
	 *            the c
	 */
	public void showClientConnected(String c) {
		String ns = Context.NOTIFICATION_SERVICE;
		NotificationManager mNotificationManager = (NotificationManager) getSystemService(ns);

		int icon = R.drawable.icon;
		CharSequence tickerText = c + " connected to VNC server";
		long when = System.currentTimeMillis();

		Notification notification = new Notification(icon, tickerText, when);

		Context context = getApplicationContext();
		CharSequence contentTitle = "Android Remote";
		CharSequence contentText = "Client Connected from " + c;
		Intent notificationIntent = new Intent();
		PendingIntent contentIntent = PendingIntent.getActivity(
				getApplicationContext(), 0, notificationIntent, 0);

		notification.setLatestEventInfo(context, contentTitle, contentText,
				contentIntent);

		notification.ledARGB = 0xff00ff00;
		notification.ledOnMS = 300;
		notification.ledOffMS = 1000;
		notification.flags |= Notification.FLAG_SHOW_LIGHTS;

		mNotificationManager.notify(APP_ID, notification);

	}

	/**
	 * Show client disconnected.
	 */
	void showClientDisconnected() {
		String ns = Context.NOTIFICATION_SERVICE;
		NotificationManager mNotificationManager = (NotificationManager) getSystemService(ns);
		mNotificationManager.cancel(APP_ID);
	}

	/**
	 * The listener interface for receiving socket events. The class that is
	 * interested in processing a socket event implements this interface, and
	 * the object created with that class is registered with a component using
	 * the component's <code>addSocketListener<code> method. When
	 * the socket event occurs, that object's appropriate
	 * method is invoked.
	 * 
	 * @see SocketEvent
	 */
	class SocketListener extends Thread {
		
		/* (non-Javadoc)
		 * @see java.lang.Thread#run()
		 */
		@Override
		public void run() {
			try {
				LocalServerSocket server = new LocalServerSocket(SOCKET_ADDRESS);
				while (true) {
					LocalSocket receiver = server.accept();
					if (receiver != null) {
						InputStream input = receiver.getInputStream();

						int readed = input.read();

						StringBuffer bytes = new StringBuffer(2048);
						while (readed != -1) {
							bytes.append((char) readed);
							readed = input.read();
						}
						// showTextOnScreen(bytes.toString());
						Log.v("VNC", bytes.substring(0, 6));

						if (bytes.substring(0, 6).equals("~CLIP|")) {
							bytes.delete(0, 6);
							ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);

							clipboard.setText(bytes.toString());
						} else {
							if (bytes.substring(0, 11).equals("~CONNECTED|")) {
								bytes.delete(0, 11);
								showClientConnected(bytes.toString());
							} else if (bytes.substring(0, 14).equals(
									"~DISCONNECTED|")) {
								showClientDisconnected();
							}
						}
					}
				}
			} catch (IOException e) {
				Log.e(getClass().getName(), e.getMessage());
			}
		}
	}

	/**
	 * Find executable on path.
	 * 
	 * @param executableName
	 *            the executable name
	 * @return the file
	 */
	private static File findExecutableOnPath(String executableName) {
		String systemPath = System.getenv("PATH");
		String[] pathDirs = systemPath.split(File.pathSeparator);

		File fullyQualifiedExecutable = null;
		for (String pathDir : pathDirs) {
			File file = new File(pathDir, executableName);
			if (file.isFile()) {
				fullyQualifiedExecutable = file;
				break;
			}
		}
		return fullyQualifiedExecutable;
	}

	/**
	 * The Class ServerThread.
	 */
	public class ServerThread implements Runnable {

		/** The battery level. */
		private Integer batteryLevel = Integer.valueOf(0);

		/**
		 * Gets the applications.
		 * 
		 * @return the applications
		 */
		private ApplicationsRunning getApplications() {
			ApplicationsRunning apps = new ApplicationsRunning();
			ActivityManager activityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
			List<RunningAppProcessInfo> applications = activityManager
					.getRunningAppProcesses();
			PackageManager pm = getPackageManager();
			for (RunningAppProcessInfo application : applications) {
				AndroidRunningApplication a = new AndroidRunningApplication();
				try {
					a.setName(pm.getApplicationLabel(
							pm.getApplicationInfo(application.processName,
									PackageManager.GET_META_DATA)).toString());
					a.setImportance(application.importance);
					apps.addApp(a);
				} catch (NameNotFoundException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
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

					int rawlevel = intent.getIntExtra(
							BatteryManager.EXTRA_LEVEL, -1);
					int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE,
							-1);
					int level = -1;
					if (rawlevel >= 0 && scale > 0) {
						level = (rawlevel * 100) / scale;
					}
					batteryLevel = level;

				}
			};
			IntentFilter batteryLevelFilter = new IntentFilter(
					Intent.ACTION_BATTERY_CHANGED);
			registerReceiver(batteryLevelReceiver, batteryLevelFilter);
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
			PackageManager pm = getPackageManager();
			// get a list of installed apps.
			List<ApplicationInfo> packages = pm
					.getInstalledApplications(PackageManager.GET_META_DATA);
			for (Iterator<ApplicationInfo> iterator = packages.iterator(); iterator.hasNext();) {
				ApplicationInfo applicationInfo = (ApplicationInfo) iterator
						.next();
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
			ActivityManager activityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
			List<RunningServiceInfo> services = activityManager
					.getRunningServices(30);
			PackageManager pm = getPackageManager();
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
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			return servicesRunning;
		}

		/**
		 * Gets the location.
		 * 
		 * @return the location
		 */
		private AndroidLocation getLocation() {
			LocationManager mlocManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
			Criteria crit = new Criteria();
			crit.setAccuracy(Criteria.ACCURACY_FINE);
			String provider = mlocManager.getBestProvider(crit, true);
			Location loc = mlocManager.getLastKnownLocation(provider);
			AndroidLocation location = new AndroidLocation(loc.getLatitude(),
					loc.getAltitude());
			return location;
		}

		/* (non-Javadoc)
		 * @see java.lang.Runnable#run()
		 */
		public void run() {
			try {
				socket = new ServerSocket(PORT);
				Log.i("AndroidRemote", "Puerto: " + PORT);
				batteryLevel();

				while (true) {

					try {
						// listen for incoming clients
						send = socket.accept();
						ObjectOutputStream os = new ObjectOutputStream(
								send.getOutputStream());
						os.flush();
						ObjectInputStream in = new ObjectInputStream(
								send.getInputStream());

						Operation o = (Operation) in.readObject();
						os.flush();
						switch (o.getId()) {
						case Operation.OP_OPEN:
							os.writeObject(new String("Conexi�n establecida"));
							showClientConnected(o.getMessage() + " "
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
							AndroidLocation location = getLocation();
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
							
						default:
							break;
						}
						Log.i("AndroidRemote", o.getMessage());

						os.flush();
						os.close();
						in.close();
						send.close();

					} catch (Exception e) {
						Log.e("AndroidRemote",
								"Unexpected error on management server", e);
					}

				}

			} catch (SocketException e) {
				Log.e("AndroidRemote", "Detectada socket exception");
			} catch (IOException e) {
				Log.e("AndroidRemote", "Detectada exception entrada salida "
						+ e.getMessage());
			}	
		}
	}
}