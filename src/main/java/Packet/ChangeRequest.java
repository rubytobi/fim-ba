package Packet;

import java.net.URI;
import java.util.Map;
import java.util.GregorianCalendar;

import Util.DateTime;
import start.Loadprofile;

public class ChangeRequest {
	GregorianCalendar startLoadprofile;
	double[] changesLoadprofile;
	
	public ChangeRequest (GregorianCalendar startLoadprofile, double[] changesLoadprofile) {
		this.startLoadprofile = startLoadprofile;
		this.changesLoadprofile = changesLoadprofile;
	}
	
	public GregorianCalendar getStartLoadprofile() {
		return startLoadprofile;
	}
	
	public double[] getChangesLoadprofile() {
		return changesLoadprofile;
	}
}
