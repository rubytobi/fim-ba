package start;

public class Main {
	public static void main(String[] args) {
		double deviation = 1;
		double price1 = 0.5;
		double price2 = 0.8;
		double sum1 = -5;
		double sum2 = 5;
		double costsBKV = 10;
		double newPrice1, newPrice2;
		
		if (deviation == 0) {
			System.out.println("Keine Abweichung.");
			newPrice1 = (price1+price2)/2;
			newPrice2 = newPrice1;
			System.out.println("Preis 1: " +newPrice1);
			System.out.println("Preis 2: " +newPrice2);
		}
		else if (deviation > 0) {
			System.out.println("Abweichung Erzeuger.");
			// Wenn offer1 Erzeuger
			if (sum1 > 0) {
				System.out.println("1 Erzeuger");
				newPrice1 = costsBKV/(sum2 - sum1);
				newPrice2 = newPrice1 + costsBKV/sum2;
				System.out.println("Preis 1: " +newPrice1);
				System.out.println("Preis 2: " +newPrice2);
			}
			else {
				System.out.println("2 Erzeuger");
				newPrice1 = costsBKV/(sum1 - sum2);
				newPrice2 = newPrice1 + costsBKV/sum2;
				System.out.println("Preis 1: " +newPrice1);
				System.out.println("Preis 2: " +newPrice2);
			}
		}
		else {
			System.out.println("Abweichung Verbraucher.");
			// Wenn offer2 Erzeuger
			if (sum2 > 0) {
				System.out.println("1 Verbraucher");
				newPrice1 = costsBKV/(sum2 - sum1);
				newPrice2 = newPrice1 + costsBKV/sum2;
				System.out.println("Preis 1: " +newPrice1);
				System.out.println("Preis 2: " +newPrice2);
			}
			else {
				System.out.println("1 Verbraucher");
				newPrice1 = costsBKV/(sum1 - sum2);
				newPrice2 = newPrice1 + costsBKV/sum2;
				System.out.println("Preis 1: " +newPrice1);
				System.out.println("Preis 2: " +newPrice2);
			}
		}
	}
}
