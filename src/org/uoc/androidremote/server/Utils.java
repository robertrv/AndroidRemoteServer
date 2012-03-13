package org.uoc.androidremote.server;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStream;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

/**
 * Utility methods mainly to manage starting and ending of our servers
 * 
 * @author robertrv [AT] gmail.com
 * 
 */
public class Utils {

	public static void tryStartService(Context context, String logTag,
			String serviceName, Bundle... bundles) {
		if (!isServiceRunning(context, serviceName)) {
			Log.i(logTag, "Service " + serviceName
					+ " not running and we are going to start it.");
			Intent mServiceIntent = new Intent();
			mServiceIntent.setAction(serviceName);
			if (bundles.length == 1) {
				mServiceIntent.putExtras(bundles[0]);
			}
			context.startService(mServiceIntent);
			Log.i(logTag, "Service " + serviceName + " started properly");
		} else {
			Log.i(logTag, "Service " + serviceName
					+ " already running, not needed to be started again");
		}
	}

	private static boolean isServiceRunning(Context context, String serviceName) {
		ActivityManager manager = (ActivityManager) context
				.getSystemService(Context.ACTIVITY_SERVICE);
		for (RunningServiceInfo service : manager
				.getRunningServices(Integer.MAX_VALUE)) {
			if (serviceName.equals(service.service.getClassName())) {
				return true;
			}
		}
		return false;
	}

	static void showClientConnected(Context context, String c) {
		String ns = Context.NOTIFICATION_SERVICE;
		NotificationManager mNotificationManager = (NotificationManager) context
				.getSystemService(ns);

		int icon = R.drawable.icon;
		CharSequence tickerText = c + " connected to VNC server";
		long when = System.currentTimeMillis();

		Notification notification = new Notification(icon, tickerText, when);

		CharSequence contentTitle = "Android Remote";
		CharSequence contentText = "Client Connected from " + c;
		Intent notificationIntent = new Intent();
		PendingIntent contentIntent = PendingIntent.getActivity(context, 0,
				notificationIntent, 0);

		notification.setLatestEventInfo(context, contentTitle, contentText,
				contentIntent);

		notification.ledARGB = 0xff00ff00;
		notification.ledOnMS = 300;
		notification.ledOffMS = 1000;
		notification.flags |= Notification.FLAG_SHOW_LIGHTS;

		mNotificationManager.notify(ServersControllerActivity.APP_ID,
				notification);
	}

	static void showClientDisconnected(Context context) {
		String ns = Context.NOTIFICATION_SERVICE;
		NotificationManager mNotificationManager = (NotificationManager) context
				.getSystemService(ns);
		mNotificationManager.cancel(ServersControllerActivity.APP_ID);
	}

	public static File findExecutableOnPath(String executableName) {
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

	public static boolean hasBusybox() {
		File busyboxFile = Utils.findExecutableOnPath("busybox");
		return busyboxFile != null;
	}

	public static void writeCommand(OutputStream os, String command)
			throws Exception {
		os.write((command + "\n").getBytes("ASCII"));
	}

	public static String executeCommand(String command) throws Exception {
		// Executes the command.
		Process process = Runtime.getRuntime().exec(command);

		// Reads stdout.
		// NOTE: You can write to stdin of the command using
		// process.getOutputStream().
		BufferedReader reader = new BufferedReader(new InputStreamReader(
				process.getInputStream()));
		int read;
		char[] buffer = new char[4096];
		StringBuffer output = new StringBuffer();
		while ((read = reader.read(buffer)) > 0) {
			output.append(buffer, 0, read);
		}
		reader.close();

		// Waits for the command to finish.
		process.waitFor();

		return output.toString();
	}

}
