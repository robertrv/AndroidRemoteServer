package org.uoc.androidremote.server;

import java.io.IOException;
import java.io.InputStream;
import java.net.SocketException;

import android.content.Context;
import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.os.SystemClock;
import android.text.ClipboardManager;
import android.util.Log;

/**
 * Server taking care of clipboard and new client connection, mainly through a 
 * local socket opened by vncserver.
 * 
 * @author robertrv [at] gmail
 *
 */
public class LocalSocketServer extends Thread {

	public static final String LOGTAG = LocalSocketServer.class.getSimpleName();
	public static final String SOCKET_ADDRESS = 
			"org.uoc.androidremote.server.localsocket";
	
	private Context context;
	private LocalServerSocket server;
	private boolean keepListening;
	private boolean listening;

	public LocalSocketServer(Context context) {
		this.context = context;
		try {
			server = new LocalServerSocket(SOCKET_ADDRESS);
			Log.d(LOGTAG, "start listening !");
		} catch (IOException e) {
			Log.d(LOGTAG, "The LocalSocketServer created failed !!!", e);
		}
	}

	@Override
	public void run() {
		InputStream input = null;
		LocalSocket receiver = null; 
		try {
			keepListening = true;
			listening = true;
			while (keepListening) {
				try {
					if (server == null) {
						SystemClock.sleep(1000);
						continue;
					}
					receiver = server.accept();
				} catch (IOException e) {
					Log.d(LOGTAG, "Error while accepting new connections", e);
					continue;
				}
				if (receiver != null) {
					input = receiver.getInputStream();

					int readed = input.read();

					StringBuffer bytes = new StringBuffer(2048);
					while (readed != -1) {
						bytes.append((char) readed);
						readed = input.read();
					}
					
					Log.v(LOGTAG, "Readed from local socket Readed: " + readed
							+ " bytes: " + bytes);
					Log.v(LOGTAG, bytes.substring(0, 6));

					if (bytes.substring(0, 6).equals("~CLIP|")) {
						bytes.delete(0, 6);
						ClipboardManager clipboard = (ClipboardManager) context
								.getSystemService(Context.CLIPBOARD_SERVICE);

						String text = bytes.toString();
						clipboard.setText(text);
					} else {
						if (bytes.substring(0, 11).equals("~CONNECTED|")) {
							bytes.delete(0, 11);
							Utils.showClientConnected(context, bytes.toString());
						} else if (bytes.substring(0, 14).equals(
								"~DISCONNECTED|")) {
							Utils.showClientDisconnected(context);
						}
					}
				}
			}
		} catch (SocketException ex) {
			Log.d(LOGTAG, "Closing connection due problem with socket, " +
					"probably a normal close");
		} catch (IOException e) {
			Log.e(LOGTAG, "IO Exception while reading from local server " +
					"socket", e);
		} finally {
			listening = false;
			if (input != null) {
				try {
					input.close();
				} catch (IOException e) {
					Log.e(LOGTAG, "Error trying to close input stream", e);
				}
			}
			if (receiver != null) {
				try {
					receiver.close();
				} catch (IOException e) {
					Log.e(LOGTAG, "Error trying to close receiver", e);
				}
			}
			if (server != null) {
				try {
					server.close();
				} catch (IOException e) {
					Log.e(LOGTAG, "Error trying to close server", e);
				}
			}
		}
	}

	void stopListening() {
		try {
			Log.v(LOGTAG, "Starting to stop local socket server");
			keepListening = false;
			if (server != null) {
				Log.v(LOGTAG, "going to close local socket server!");
				server.close();
				int loops = 0;
				do {
					SystemClock.sleep(200);
				} while (listening && loops++ < 10);
			}
			interrupt();
			Log.v(LOGTAG, "Finished stopping local socket server");
		} catch (IOException e) {
			Log.e(LOGTAG, "Error stopping server", e);
		}
	}
	
	public boolean isListening() {
		return keepListening;
	}
}
