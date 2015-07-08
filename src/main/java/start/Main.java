package start;

public class Main {
	public static void main(String[] args) {
		int minuteCurrent = 20;
		int numSlots = 4;
		double[] valuesNew = {1.0, 5.0, 0.0, 0.0};
		
		int slot = (int) Math.floor(minuteCurrent/15)+1;
		double sum = 0;
		for (int i=slot; i<numSlots; i++) {
			sum = sum + valuesNew[i];
		}
		if (sum == 0) {
			System.out.println("BEENDET");
		}
		else {
			for (int j=0; j<slot; j++) {
				valuesNew[j] = 0;
			}
		}
		System.out.println("Summe: " +sum);
		System.out.println("Slot: " +slot);
		System.out.println("Werte: ");
		for (int n=0; n<4; n++) {
			System.out.println(valuesNew[n]);
		}
	}
}
