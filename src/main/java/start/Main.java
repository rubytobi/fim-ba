package start;

import Util.DateTime;
import Entity.Loadprofile;
import Entity.Offer;
import java.util.*;

public class Main {
	public static void main(String[] args) {
		double[] values1 = {9, 18, 12, 23};
		double[] values2 = {-1, -17, -10, -5};
		double priceSugg1 = 0.20;
		double priceSugg2 = 0.15;
		double minPrice1 = 0.15;
		double minPrice2 = Double.NEGATIVE_INFINITY;
		double maxPrice1 = Double.POSITIVE_INFINITY;
		double maxPrice2 = 0.20;
		String date = DateTime.ToString(DateTime.now());
		UUID uuid1 = UUID.randomUUID();
		UUID uuid2 = UUID.randomUUID();
		
		Loadprofile lp1 = new Loadprofile(values1, date, priceSugg1, minPrice1, maxPrice1, Loadprofile.Type.INITIAL);
		Loadprofile lp2 = new Loadprofile(values2, date, priceSugg2, minPrice2, maxPrice2, Loadprofile.Type.INITIAL);
		
		Offer offer1 = new Offer(uuid1, lp1);
		Offer offer2 = new Offer(uuid2, lp2);
		
		Offer offer;
		try {
			offer = new Offer(offer1, offer2);
			double minPrice = offer.getMinPrice();
			double maxPrice = offer.getMaxPrice();
			double priceSugg = offer.getPriceSugg();
			double[] values = offer.getAggLoadprofile().getValues();
			System.out.println(minPrice);
			System.out.println(maxPrice);
			System.out.println(priceSugg);
			System.out.println("[" +values[0] + "]["+values[1] + "]["+values[2] + "]["+values[3] + "]");

		}
		catch (Exception e) {
			
		}
		
		

	}
}
