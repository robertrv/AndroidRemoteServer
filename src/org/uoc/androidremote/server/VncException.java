package org.uoc.androidremote.server;

/**
 * Simple exception class to define exactly what we want to show to the 
 * client.
 * 
 * @author robertrv [at] gmail
 *
 */
public class VncException extends IllegalStateException {
	private static final long serialVersionUID = 1L;

	public VncException(String message) {
		super(message);
	}
	public VncException(String message, Throwable throwable) {
		super(message, throwable);
	}
}