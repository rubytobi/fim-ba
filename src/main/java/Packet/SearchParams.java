package Packet;

import java.util.GregorianCalendar;

import Util.DateTime;

public class SearchParams implements Cloneable {
	private double maxPrice;
	private double minPrice;
	private String date;

	public SearchParams() {
		// dummy
	}

	public SearchParams(String date, double minPrice, double maxPrice) {
		this.date = date;
		this.minPrice = minPrice;
		this.maxPrice = maxPrice;
	}

	public double getMaxPrice() {
		return maxPrice;
	}

	public double getMinPrice() {
		return minPrice;
	}

	public String getDate() {
		return date;
	}

	public String toString() {
		return "SearchParams [date=" + date + ",minPrice=" + minPrice + ",maxPrice=" + maxPrice + "]";
	}

	public SearchParams clone() {
		return new SearchParams(date, minPrice, maxPrice);
	}

}
