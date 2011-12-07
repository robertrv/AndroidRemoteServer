package org.uoc.androidremote.server;

import java.io.IOException;
import java.io.InputStream;
import java.net.SocketException;

import android.content.Context;
import android.net.LocalServerSocket;
import android.net.LocalSocket;
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
			"/data/data/org.uoc.androidremote.server.localsocket";
	
	private Context context;
	private LocalServerSocket server;
	private boolean listen;

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
			listen = true;
			while (listen) {
				try {				
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
					
					Log.v(LOGTAG, bytes.substring(0, 6));

					if (bytes.substring(0, 6).equals("~CLIP|")) {
						bytes.delete(0, 6);
						ClipboardManager clipboard = (ClipboardManager) context
								.getSystemService(Context.CLIPBOARD_SERVICE);

						clipboard.setText(bytes.toString());
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
			listen = false;
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
			listen = false;
			if (server != null) {
				server.close();
				interrupt();
			}
		} catch (IOException e) {
			Log.e(LOGTAG, "Error stopping server", e);
		}
	}
	
	public boolean isListening() {
		return listen;
	}
}
