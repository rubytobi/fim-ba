package Packet;

import java.util.Arrays;
import java.util.GregorianCalendar;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonIgnore;

import Entity.Loadprofile;
import Util.DateTime;

/**
 * Paket, mit welchem der Consumer seine Partner-Consumers um eine Anpassung des
 * Lastprofils bittet
 *
 */
public class ChangeRequestLoadprofile {
	private UUID offer;
	private double[] change;
	private GregorianCalendar time;

	protected ChangeRequestLoadprofile() {
		// dummy
	}

	/**
	 * Anlegen einer Anfrage für eine Änderung. Übergeben werden die Angebots-ID
	 * des betroffenen Angebots und die gewünschte Änderung
	 * 
	 * @param offer
	 *            Angebots-ID
	 * @param change
	 *            Änderung
	 */
	public ChangeRequestLoadprofile(UUID offer, double[] change, GregorianCalendar time) {
		this.offer = offer;
		this.change = change;
		this.time = time;
	}

	public UUID getOffer() {
		return offer;
	}

	public double[] getChange() {
		return change;
	}

	@JsonIgnore
	public boolean isZero() {
		for (double d : change) {
			if (d != 0) {
				return false;
			}
		}
		return true;
	}

	public Loadprofile toLoadprofile() {
		return new Loadprofile(change, time, Loadprofile.Type.DELTA);
	}

	public String toString() {
		return "ChangeRequestLoadprofile [offer=" + offer + ", change=" + Arrays.toString(change) + ", time="
				+ DateTime.ToString(time) + "]";
	}

	public void sub(AnswerChangeRequestLoadprofile answer) {
		for (int i = 0; i < 4; i++) {
			change[i] -= answer.getLoadprofile().getValues()[i];
		}
	}

	public GregorianCalendar getTime() {
		return time;
	}
}
