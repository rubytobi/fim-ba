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
import Packet.FridgeCreation;
import Util.DateTime;
import Entity.Marketplace;
import Util.PossibleMatch;
import Util.Negotiation;
import Util.SimulationBHKW;

public class Main {
	public static void main(String[] args) {
		Fridge fridge = new Fridge(8, 9, 4, 2, -0.5, 0.2, 1, 5);
		fridge.sendNewLoadprofile();
		
		GregorianCalendar now = DateTime.now();
		now.set(Calendar.MINUTE, 0);
		now.set(Calendar.SECOND, 0);
		now.set(Calendar.MILLISECOND, 0);
		
		double[] change = {-40, -9, -10, -10};

		ChangeRequestSchedule cr = new ChangeRequestSchedule(now, change);
		fridge.changeLoadprofile(cr);
	}
}
