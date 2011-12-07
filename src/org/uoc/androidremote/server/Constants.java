package org.uoc.androidremote.server;

public class Constants {

	public enum ServiceAction {
		START_VNC(1), 
		STOP_VNC(2), 
		START_MNG(3), 
		STOP_MNG(4),
		GET_ALL_SERVER_STATUS(5);
		
		public int what;
		private ServiceAction(int what) {
			this.what = what;
		}
		public static ServiceAction fromCode(int code) {
			for (ServiceAction value : values()) {
				if (value.what == code) {
					return value;
				}
			}
			return null;
		}
	}
	
	public enum ServiceReply {
		INIT_FINISHED(0),	// arg1 = mng port, arg2 = vnc port
		VNC_STARTED(1),		// arg1 = vnc port
		VNC_STOPPED(3), 
		MNG_STARTED(4), 	// arg1 = vnc port
		MNG_STOPPED(5), 
		SHOW_MESSAGE(6);
		//On each reply, the Object = string to show to the user
		
		public int what;
		private ServiceReply(int what) {
			this.what = what;
		}
		public static ServiceReply fromCode(int code) {
			for (ServiceReply value : values()) {
				if (value.what == code) {
					return value;
				}
			}
			return null;
		}
	}
}
