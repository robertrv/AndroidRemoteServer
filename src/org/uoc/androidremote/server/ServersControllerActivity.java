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

import java.io.File;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;

import org.uoc.androidremote.server.Constants.ServiceAction;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Main android activity responsible for gui management, will delegate starting
 * and stopping servers to another service
 * 
 * @author robertrv [at] gmail
 */
public class ServersControllerActivity extends Activity {

	/** MENU's constants. */
	private static final int QUIT = 0;
	private static final int FINISH = 1;

	private static final String LOGTAG = ServersControllerActivity.class
			.getSimpleName();
	
	static final String PORT_BUNDLE_KEY = "port";
	static final String SCALE_FACTOR_BUNDLE_KEY = "scale_factor";

	/** The Constant APP_ID. */
	static final int APP_ID = 1206;

	/** The start dialog. */
	AlertDialog startDialog;

	private boolean vncRunning = false;
	private Integer vncPort = VncServerWrapper.DEFAULT_PORT;
	private boolean mngRunning = false;
	private Integer mngPort = ManagementServer.DEFAULT_PORT;

	/** Messenger for communicating with service. */
	Messenger serviceMessenger = null;
	/** Flag indicating whether we have called bind on the service. */
	boolean serviceBound;

	/**
	 * Handler of incoming messages from service.
	 */
	class IncomingHandler extends Handler {
		@Override
		public void handleMessage(Message msg) {
			switch (Constants.ServiceReply.fromCode(msg.what)) {
			case VNC_STARTED:
				vncRunning = true;
				vncPort = msg.arg1;
				break;
			case VNC_STOPPED:
				vncRunning = false;
				break;
			case MNG_STARTED:
				mngRunning = true;
				mngPort = msg.arg1;
				break;
			case MNG_STOPPED:
				mngRunning = false;
				break;
			default:
				super.handleMessage(msg);
			}
			if (msg.obj != null) {
				showTextOnScreen(msg.obj.toString());
			}
			setStateLabels();
		}
	}

	/**
	 * Target we publish for clients to send messages to IncomingHandler.
	 */
	final Messenger mMessenger = new Messenger(new IncomingHandler());

	/**
	 * Class for interacting with the main interface of the service.
	 */
	private ServiceConnection mConnection = new ServiceConnection() {

		public void onServiceConnected(ComponentName className, IBinder service) {
			// This is called when the connection with the service has been
			// established, giving us the service object we can use to
			// interact with the service. We are communicating with our
			// service through an IDL interface, so get a client-side
			// representation of that from the raw service object.
			serviceMessenger = new Messenger(service);
			Log.d(this.getClass().getSimpleName(), "attached to service");
			sendMessageToService(ServiceAction.GET_ALL_SERVER_STATUS, null);
		}

		public void onServiceDisconnected(ComponentName className) {
			// This is called when the connection with the service has been
			// unexpectedly disconnected -- that is, its process crashed.
			serviceMessenger = null;
			Log.d(this.getClass().getSimpleName(), "dettached to service");
		}
	};

	protected Toast toast;

	void doBindService() {
		bindService(new Intent(ServersControllerActivity.this,
				ServersControllerService.class), mConnection,
				Context.BIND_AUTO_CREATE);
		serviceBound = true;
		Log.d(LOGTAG, "bound to service");
	}

	void doUnbindService() {
		if (serviceBound) {
			// Detach our existing connection.
			unbindService(mConnection);
			serviceBound = false;
			Log.d(this.getClass().getSimpleName(), "Unbinding.");
		}
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		Log.v(LOGTAG, "Executing the onDestroy, going to unbind from service");
		doUnbindService();
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

		checkBusybox();

		if (!hasRootPermission()) {
			startDialog.dismiss();

			Log.v(LOGTAG, "You don't have root permissions...!!!");
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

		setStateLabels();

		findViewById(R.id.Button01).setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View arg0) {
				Bundle data = new Bundle();
				vncPort = getIntIfPossibleAndUpdateCache(R.id.vncPort, vncPort);
				data.putInt(PORT_BUNDLE_KEY, vncPort);
				Spinner deviceType = (Spinner) findViewById(R.id.deviceType);
				int position = deviceType.getSelectedItemPosition(); 
				// 0 => mobile, 1 => tablet
				int scaleFactor = (position == 0)?100:50;
				data.putInt(SCALE_FACTOR_BUNDLE_KEY, scaleFactor);
				
				sendMessageToService(ServiceAction.START_VNC, data);
			}
		});
		findViewById(R.id.Button02).setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View arg0) {
				sendMessageToService(ServiceAction.STOP_VNC, null);
			}
		});

		findViewById(R.id.ButtonGestionStart).setOnClickListener(
				new OnClickListener() {
					@Override
					public void onClick(View arg0) {
						Bundle data = new Bundle(1);
						mngPort = getIntIfPossibleAndUpdateCache(R.id.mngPort, mngPort);
						data.putInt(PORT_BUNDLE_KEY, mngPort);

						sendMessageToService(ServiceAction.START_MNG, data);
					}
				});
		findViewById(R.id.ButtonGestionStop).setOnClickListener(
				new OnClickListener() {
					@Override
					public void onClick(View arg0) {
					 	sendMessageToService(ServiceAction.STOP_MNG, null);
					}
				});
		// TODO R: Set device type depending on the screen size 
	}

	private int getIntIfPossibleAndUpdateCache(int rId, Integer cache){
		EditText editText = (EditText) findViewById(rId);
		String inputPort = editText
				.getText().toString();
		try {
			return Integer.valueOf(inputPort);
		} catch (NumberFormatException ignored) {
			editText.setText(String.valueOf(cache));
			return cache;
		}
	}
	@Override
	protected void onResume() {
		super.onResume();
		// Start service, even probably is started, needed to avoid closing
		// himself.
		Utils.tryStartService(getApplicationContext(), LOGTAG,
				ServersControllerService.class.getCanonicalName());
		// Ask the service about information about servers !
		doBindService();
	}

	private void sendMessageToService(ServiceAction action, Bundle data) {
		try {
			Message msg = Message.obtain(null, action.what);
			msg.replyTo = mMessenger;
			if (data != null) {
				msg.setData(data);
			}
			serviceMessenger.send(msg);
		} catch (RemoteException e) {
			Log.e(LOGTAG, "error sending action " + action
					+ " to the android service", e);
			showTextOnScreen(e.getMessage());
		}
	}

	private boolean checkBusybox() {
		boolean has = Utils.hasBusybox();
		startDialog = new AlertDialog.Builder(this).create();
		if (!has) {
			Log.v(LOGTAG, "Busybox not found...!!!");
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

	private void showTextOnScreen(final String t) {
		runOnUiThread(new Runnable() {
			public void run() {
				if (ServersControllerActivity.this.toast != null) {
					ServersControllerActivity.this.toast.cancel();
				}
				ServersControllerActivity.this.toast = Toast.makeText(
						ServersControllerActivity.this, t, Toast.LENGTH_LONG);
				ServersControllerActivity.this.toast.show();
			}
		});
	}

	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.menu, menu);

		menu.add(0, QUIT, 0, "Close activity");
		menu.add(0, FINISH, 1, "Stop all");

		return true;
	}

	/**
	 * Set the state labels depending on current status of sockets and internal
	 * server information.
	 */
	private void setStateLabels() {
		setStateLabels(vncRunning, mngRunning);
	}

	/**
	 * Sets the state labels.
	 * 
	 * @param runningVnc
	 *            the state vnc
	 * @param runningManagement
	 *            the state gestion
	 */
	private void setStateLabels(boolean runningVnc, boolean runningManagement) {
		TextView stateLabel = (TextView) findViewById(R.id.stateLabel);
		stateLabel.setText(runningVnc ? "Running" : "Stopped");
		stateLabel.setTextColor(runningVnc ? Color.GREEN : Color.RED);

		Button btnStart = (Button) findViewById(R.id.Button01);
		Button btnStop = (Button) findViewById(R.id.Button02);
		btnStart.setEnabled(!runningVnc);
		btnStop.setEnabled(runningVnc);
		EditText vncPortText = (EditText) findViewById(R.id.vncPort);
		vncPortText.setEnabled(!runningVnc);
		Spinner deviceType = (Spinner) findViewById(R.id.deviceType);
		deviceType.setEnabled(!runningVnc);
		
		TextView t = (TextView) findViewById(R.id.TextView01);

		String host = "localhost";
		String ip = getIpAddress();
		if (ip != null) {
			host = ip;
		}

		if (runningVnc) {
			t.setText("http://" + host + ":" + vncPort);
		} else {
			t.setText("");
		}


		TextView stateGestionLabel = (TextView) findViewById(R.id.stateGestionLabel);
		stateGestionLabel.setText(runningManagement ? "Running" : "Stopped");
		stateGestionLabel.setTextColor(runningManagement ? Color.GREEN
				: Color.RED);

		Button btnStartGestion = (Button) findViewById(R.id.ButtonGestionStart);
		Button btnStopGestion = (Button) findViewById(R.id.ButtonGestionStop);
		btnStartGestion.setEnabled(!runningManagement);
		btnStopGestion.setEnabled(runningManagement);
		TextView tGestion1 = (TextView) findViewById(R.id.TextViewGestion01);
		EditText mngPortText = (EditText) findViewById(R.id.mngPort);
		mngPortText.setEnabled(!runningManagement);
		

		if (runningManagement) {
			tGestion1.setText("IP: " + host + " Puerto: " + mngPort);
		} else {
			tGestion1.setText("");
		}
	}

	private String getIpAddress() {
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
			Log.e(LOGTAG, ex.toString());
		}
		return null;
	}

	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case QUIT:
			// Just stop this application
			finish();
			break;
		case FINISH:
			stopService(new Intent(
					ServersControllerService.class.getCanonicalName()));
			finish();
			break;
		}
		return true;
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
			Log.v(LOGTAG,
					"Can't obtain root - Here is what I know: "
							+ e.getMessage());
			rooted = false;
		}

		return rooted;
	}

}