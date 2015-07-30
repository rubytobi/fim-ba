package start;

import java.util.Calendar;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.UUID;
import java.util.ArrayList;

import Entity.Fridge;
import Entity.Offer;
import Entity.BHKW;
import Packet.AnswerToOfferFromMarketplace;
import Packet.ChangeRequestSchedule;
import Util.DateTime;
import Entity.Marketplace;
import Util.PossibleMatch;
import Util.Negotiation;
import Util.SimulationBHKW;

public class Main {
	public static void main(String[] args) {
		SimulationBHKW simulation = new SimulationBHKW(50);
		
		GregorianCalendar start = DateTime.now();
		start.set(Calendar.MINUTE, 0);
		start.set(Calendar.SECOND, 0);
		start.set(Calendar.MILLISECOND, 0);

		double[][] plan = simulation.getNewSchedule(start);
		for (int i=0; i<60; i++) {
			System.out.println((i+1)+ ". Minute: Füllstand: " +plan[0][i]+ ", Erzeugung: " +plan[1][i]);
		}
		
		start.add(Calendar.HOUR, 1);
		double[][] plan2 = simulation.getNewSchedule(start);
		for (int i=0; i<60; i++) {
			System.out.println((i+1)+ ". Minute: Füllstand: " +plan2[0][i]+ ", Erzeugung: " +plan2[1][i]);
		}
		
		BHKW bhkw = new BHKW(1, 0.5, 1, 10, 50);
		bhkw.sendNewLoadprofile();
		
		start.add(Calendar.HOUR_OF_DAY,  -1);
		double[] changes = {-1, 2, 10, -2};
		ChangeRequestSchedule cr = new ChangeRequestSchedule(start, changes);
		
		bhkw.changeLoadprofile(cr);
	}
}
