package Packet;

import java.util.Arrays;
import java.util.GregorianCalendar;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnore;

import Entity.Loadprofile;
import Util.DateTime;

/**
 * Paket, mit welchem der Consumer sein Device um eine Anpassung des Lastprofils
 * bittet
 *
 */
public class ChangeRequestSchedule implements Cloneable {
	private UUID uuid;
	private String startLoadprofile;
	private double[] changesLoadprofile;

	protected ChangeRequestSchedule() {
		// dummy
	}

	/**
	 * Erstellt neue ChangeRequest
	 * 
	 * @param startLoadprofile
	 *            Start des zu aendernden Lastprofils
	 * @param changesLoadprofile
	 *            Werte, um die das Lastprofil geaendert werden soll
	 */
	public ChangeRequestSchedule(String startLoadprofile, double[] changesLoadprofile) {
		this.startLoadprofile = startLoadprofile;
		this.changesLoadprofile = changesLoadprofile;
		uuid = UUID.randomUUID();
	}

	/**
	 * Liefert den Start des zu aendernden Lastprofils
	 * 
	 * @return Start des zu aendernden Lastprofils als GregorianCalendar
	 */
	public String getStartLoadprofile() {
		return startLoadprofile;
	}

	/**
	 * Liefert die Werte, um die das Lastprofil geaendert werden soll
	 * 
	 * @return Array mit Werten, um die das Lastprofil geaendert werden soll
	 */
	public double[] getChangesLoadprofile() {
		return changesLoadprofile;
	}

	/**
	 * Liefert die UUID der ChangeRequest
	 * 
	 * @return UUID der ChangeRequest
	 */
	public UUID getUUID() {
		return uuid;
	}

	@Override
	public boolean equals(Object o) {
		if (o instanceof ChangeRequestSchedule) {
			ChangeRequestSchedule cr = (ChangeRequestSchedule) o;
			if (cr.startLoadprofile.equals(startLoadprofile)) {
				for (int i = 0; i < 4; i++) {
					if (cr.getChangesLoadprofile()[i] != changesLoadprofile[i]) {
						return false;
					}
				}

				return true;
			} else {
				return false;
			}
		} else {
			return false;
		}
	}

	@Override
	public ChangeRequestSchedule clone() {
		return new ChangeRequestSchedule(startLoadprofile, changesLoadprofile.clone());
	}

	@JsonIgnore
	public boolean isZero() {
		for (int i = 0; i < 4; i++) {
			if (changesLoadprofile[i] != 0) {
				return false;
			}
		}
		return true;
	}

	public String toString() {
		return "ChangeRequestSchedule [startLoadprofile=" + startLoadprofile + ",changesLoadprofile="
				+ Arrays.toString(changesLoadprofile) + "]";
	}

	public Loadprofile toLoadprofile() {
		return new Loadprofile(this);
	}
}
