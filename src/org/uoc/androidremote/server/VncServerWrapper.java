package org.uoc.androidremote.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;

import android.app.NotificationManager;
import android.content.Context;
import android.os.SystemClock;
import android.util.Log;

public class VncServerWrapper {
	private final static String LOGTAG = VncServerWrapper.class.getSimpleName();
	private Context context;
	
	private int listeningPort = 5901;
	
	public VncServerWrapper(Context context) {
		this.context = context;
	}
	
	public void stopVncServer() throws VncException {
		try {
			Process sh;

			sh = Runtime.getRuntime().exec("su");
			OutputStream os = sh.getOutputStream();

			if (Utils.hasBusybox()) {
				writeCommand(os, "busybox killall androidvncserver");
				writeCommand(os, "busybox killall -KILL androidvncserver");
			} else {
				writeCommand(os, "killall androidvncserver");
				writeCommand(os, "killall -KILL androidvncserver");
				if (Utils.findExecutableOnPath("killall") == null) {
					String msg = "I couldn't find the killall executable, " +
							"please install busybox or i can't stop server";
					Log.v(LOGTAG,msg);
					throw new IOException(msg);
				}
			}

			writeCommand(os, "exit");

			os.flush();
			os.close();

			// lets clear notifications
			String ns = Context.NOTIFICATION_SERVICE;
			NotificationManager mNotificationManager = (NotificationManager) context.getSystemService(ns);
			mNotificationManager.cancel(ServersControllerActivity.APP_ID);
			int numLoops = 0;
			do {
	        	SystemClock.sleep(100);
			}
			while (isVncServerRunning() && numLoops++ < 10);

		} catch (Exception e) {
			String msg = "stopServer():" + e.getMessage();
			Log.v(LOGTAG, msg);
			throw new VncException(msg);
		}

	}

//	public void startVncServerOnDifferentThread() {
//		if (isVncServerRunning())
//			showTextOnScreen("Server is already running, stop it first");
//		else {
//			Thread t = new Thread() {
//				public void run() {
//					startVncServer();
//				}
//			};
//			t.start();
//		}
//	}

	public void startVncServer() throws VncException {
		try {
			Process sh;

			String password = "";
			String password_check = "";
			if (!password.equals("")) {
				password_check = "-p " + password;
			}

			String rotation = "0";
			rotation = "-r " + rotation;

			String scaling = "100";

			String scaling_string = "";
			if (!scaling.equals("0")) {
				scaling_string = "-s " + scaling;
			}

			String tm = "0";
			String testmode = "-t " + tm;

			
			String port_string = "-P " + getListeningPort();

			sh = Runtime.getRuntime().exec("su");
			OutputStream os = sh.getOutputStream();

			writeCommand(os, "chmod 777 " + context.getFilesDir().getAbsolutePath()
					+ "/androidvncserver");
			writeCommand(os, context.getFilesDir().getAbsolutePath()
					+ "/androidvncserver " + password_check + " " + rotation
					+ " " + scaling_string + " " + port_string + " " + testmode);

			// dont show password on logcat
			Log.v(LOGTAG, "Starting " + context.getFilesDir().getAbsolutePath()
					+ "/androidvncserver " + " " + rotation + " "
					+ scaling_string + " " + port_string + " " + testmode);
			int numLoops = 0;
			do {
	        	SystemClock.sleep(100);
			}
			while (!isVncServerRunning() && numLoops++ < 10);
			
		} catch (Exception e) {
			String msg = "startServer():" + e.getMessage();
			Log.v(LOGTAG, msg);
			throw new VncException(msg);
		}
	}

	public static boolean isVncServerRunning() {
		String result = "";
		Process sh;
		try {
			if (Utils.hasBusybox()) {
				sh = Runtime.getRuntime().exec("busybox ps w");
			} else {
				if (Utils.findExecutableOnPath("ps") == null) {
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
					Log.v(LOGTAG, "isAndroidServerRunning? yes");
					return true;
				}
			}

		} catch (Exception e) {
			Log.v(LOGTAG, " isAndroidServerRunning():" + e.getMessage(), e);
		}

		Log.v(LOGTAG, "isAndroidServerRunning? no");
		return false;
	}
	
	static void writeCommand(OutputStream os, String command) throws Exception {
		os.write((command + "\n").getBytes("ASCII"));
	}

	public int getListeningPort() {
		return listeningPort;
	}

}
