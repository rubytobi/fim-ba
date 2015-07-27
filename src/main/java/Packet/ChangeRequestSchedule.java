package Packet;

import java.net.URI;
import java.util.Map;
import java.util.GregorianCalendar;

import Util.DateTime;
import start.Loadprofile;

/**
 * Paket, mit welchem der Consumer sein Device um eine Anpassung des Lastprofils bittet
 *
 */
public class ChangeRequestSchedule {
	private GregorianCalendar startLoadprofile;
	private double[] changesLoadprofile;
	
	/**
	 * Erstellt neue ChangeRequest
	 * @param startLoadprofile		Start des zu aendernden Lastprofils
	 * @param changesLoadprofile	Werte, um die das Lastprofil geaendert werden soll
	 */
	public ChangeRequestSchedule (GregorianCalendar startLoadprofile, double[] changesLoadprofile) {
		this.startLoadprofile = startLoadprofile;
		this.changesLoadprofile = changesLoadprofile;
	}
	
	/**
	 * Liefert den Start des zu aendernden Lastprofils
	 * @return Start des zu aendernden Lastprofils als GregorianCalendar
	 */
	public GregorianCalendar getStartLoadprofile() {
		return startLoadprofile;
	}
	
	/**
	 * Liefert die Werte, um die das Lastprofil geaendert werden soll
	 * @return Array mit Werten, um die das Lastprofil geaendert werden soll
	 */
	public double[] getChangesLoadprofile() {
		return changesLoadprofile;
	}
}
