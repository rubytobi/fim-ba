package start;

import java.util.Calendar;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.UUID;
import java.util.ArrayList;

import Entity.Fridge;
import Entity.Offer;
import Packet.AnswerToOfferFromMarketplace;
import Util.DateTime;
import start.Marketplace;
import Util.PossibleMerge;
import Util.Negotiation;

public class Main {
	public static void main(String[] args) {
		double minute = 5;
		int slot = (int) Math.floor(minute/15);
		System.out.println("Slot: " +slot);
		
		/*Marketplace marketplace = Marketplace.instance();
		UUID author = UUID.randomUUID();
		UUID author1 = UUID.randomUUID();
		GregorianCalendar date = DateTime.now();
		date.set(Calendar.MINUTE, 0);
		date.set(Calendar.SECOND, 0);
		date.set(Calendar.MILLISECOND, 0);
		
		double[] values1 = {10.0, 20.0, 30.0, 40.0};
		double[] values2 = {-10.0, -20.0, -30.0, -46.0};
		double[] values3 = {10.0, 22.0, 30, 39.5};

		Loadprofile loadprofile1 = new Loadprofile(values1, date, 2.0);
		Loadprofile loadprofile2 = new Loadprofile(values2, date, 2.0);
		Loadprofile loadprofile3 = new Loadprofile(values3, date, 3.0);
		
		Offer offer1 = new Offer(author, loadprofile1);
		Offer offer2 = new Offer(author1, loadprofile2);
		Offer offer3 = new Offer(author1, loadprofile3);
		
		System.out.println("Sum Offer1: " +offer1.getSumAggLoadprofile());
		System.out.println("Sum Offer2: " +offer2.getSumAggLoadprofile());
		System.out.println("Sum Offer3: " +offer3.getSumAggLoadprofile());

		
		ArrayList<Offer> offers = new ArrayList<Offer>();
		offers.add(offer1);
		offers.add(offer2);
		offers.add(offer3);
		Collections.sort(offers);
		System.out.println(offers.get(0).getAggLoadprofile().getValues()[0]);
		System.out.println(offers.get(1).getAggLoadprofile().getValues()[0]);
		System.out.println(offers.get(2).getAggLoadprofile().getValues()[0]);

		/*
		marketplace.putOffer(offer1);
		System.out.println(marketplace.marketplaceToString());
		marketplace.allOffersToString();
		marketplace.mergedToString();
		marketplace.possibleMergesToString();
		
		marketplace.putOffer(offer2);
		System.out.println(marketplace.marketplaceToString());
		marketplace.allOffersToString();
		marketplace.mergedToString();
		marketplace.possibleMergesToString();

		marketplace.putOffer(offer3);
		System.out.println(marketplace.marketplaceToString());
		marketplace.allOffersToString();
		marketplace.mergedToString();
		marketplace.possibleMergesToString();
		
		marketplace.confirmAllRemainingOffersWithOnePrice(date, true);
		
		ArrayList<Offer> offers = new ArrayList<Offer>();
		Collections.sort(offers);*/
		
		/*
		Negotiation negotiation = marketplace.getNegotiations().get(0);
		System.out.println("\nStart Verhandlung");
		negotiation.receiveAnswer(author, 2.5);
		negotiation.receiveAnswer(author, 2.5);
		//negotiation.receiveAnswer(author, 2.5);
		
		System.out.println(marketplace.marketplaceToString());
		marketplace.allOffersToString();
		marketplace.mergedToString();
		marketplace.possibleMergesToString();
		marketplace.blackListPossibleMergesToString();*/

		
		/*
		double[] sum = marketplace.getSumAllOffers(date);
		for (int i=0; i<4; i++) {
			System.out.println("Summe: " +sum[i]);
		}*/
		/*
		
		
		ArrayList<PossibleMerge> possibleMerges = new ArrayList<PossibleMerge>();
		PossibleMerge possibleMerge1 = new PossibleMerge(offer1, offer2);
		PossibleMerge possibleMerge2 = new PossibleMerge(offer1, offer3);
		PossibleMerge possibleMerge3 = new PossibleMerge(offer2, offer3);
		possibleMerges.add(possibleMerge1);
		possibleMerges.add(possibleMerge2);
		possibleMerges.add(possibleMerge3);
		Collections.sort(possibleMerges);
		for (PossibleMerge possibleMerge: possibleMerges) {
			System.out.println(possibleMerge.toString());
		}*/
		
		/*
		int numSlots = 4;
		double[] loadprofile = {1.0, -1.0, 0.0, -5.0};
		double[] currentDeviation = {1.0, -1.0, 0.0, 2.0};
		double[] newDeviation = new double[numSlots];
		double[] worsening = new double[numSlots];
		for (int i=0; i<numSlots; i++) {
			newDeviation[i] = -(currentDeviation[i] - loadprofile[i]);
			System.out.println("New Deviation: " +newDeviation[i]);
			if (Math.abs(newDeviation[i]) > Math.abs(currentDeviation[i])) {
				worsening[i] = Math.abs(newDeviation[i]) - Math.abs(currentDeviation[i]);
				if (newDeviation[i] < 0) {
					worsening[i] = -worsening[i];
				}
			}
			else {
				worsening[i] = 0;
			}
			System.out.println("Worsening: " +worsening[i]);
		}*/		
	}
}
