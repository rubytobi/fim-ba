package start;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.http.ResponseEntity;
import springfox.documentation.swagger2.annotations.EnableSwagger2;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.TimeZone;
import java.util.UUID;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import Container.DeviceContainer;
import Entity.Consumer;
import Entity.Device;
import Entity.Fridge;
import Entity.Identifiable;
import Packet.FridgeCreation;
import Util.API;
import Util.DateTime;

@Configuration
@EnableAutoConfiguration
@SpringBootApplication
@EnableScheduling
@EnableSwagger2
@ComponentScan(basePackageClasses = { GeneralController.class })
public class Application {
	public static class Params {
		// Bestimmt, welche Prognose der Marktplatz verwenden soll
		static double[] predictionScenario1f = {0, 0, 0, 0};
		static double[] predictionScenario2f = {-20, -20, -20, -20};
		static double[] predictionScenario3f = {20, 20, 20, 20};
		static double[] predictionScenario4f = {10, 10, 10, 10, 0, 0, 0, 0, -10, -10, -10, -10};
		public static final double[] prediction = predictionScenario1f;
		
		// Setze die Anzahl an Devices der Simulation
		static int scenariof = 51;
		static int scenario1t = 50;
		static int scenario2t = 750;
		static int scenario3t = 11250;
		static int scenario4t = 168750;
		public static final int maxDevices = scenario2t;

		// Jedes x-te Gerät ist ein BHKW
		public static final int bhkwQuota = 25;

		// Setze den Zeitfaktor, sodass die Simulationszeit schneller (>1) oder
		// langsamer (<1) als die reale Zeit vergeht
		// Beachte, dass bei einer sehr schnellen Simulationszeit die
		// Ping-Zeiten angepasst werden müssen!
		public static final double timeFactor = 4;

		// Lege fest, in welcher Minute die zweite Phase des Marktplatzes
		// starten soll
		public static final int marketplaceMinuteOfSecondPhase = 45;

		// Lege fest, ob mit DeltaLastprofilen gearbeitet werden soll
		public static final boolean enableDeltaLoadprofiles = true;

		// Gibt den Wert an, mit welchem das jeweilige BHKW gestartet werden
		// soll
		public static double startLoad = 0.8;

		public static final String URL = "http://localhost:8080";
		public static final String VERSION = "";
		public static final String BASE_URI = URL + VERSION;

		public final static DateFormat myDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");

		public final static TimeZone myTimeZone = TimeZone.getTimeZone("Europe/Berlin");

		public final static Identifiable root = new Identifiable() {
			private final UUID root = UUID.fromString("00000000-0000-0000-0000-0000");

			@Override
			public UUID getUUID() {
				return root;
			}
		};
	}

	public static void main(String[] args) {
		System.setProperty("org.graphstream.ui.renderer", "org.graphstream.ui.j2dviewer.J2DGraphRenderer");

		TimeZone.setDefault(Params.myTimeZone);
		Params.myDateFormat.setTimeZone(Params.myTimeZone);

		SpringApplicationBuilder builder = new SpringApplicationBuilder(Application.class);
		builder.headless(false).run(args);
	}

	@Bean
	public MappingJackson2HttpMessageConverter mappingJackson2HttpMessageConverter() {
		MappingJackson2HttpMessageConverter jsonConverter = new MappingJackson2HttpMessageConverter();
		ObjectMapper mapper = new ObjectMapper();

		mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
		mapper.configure(SerializationFeature.INDENT_OUTPUT, true);

		mapper.setTimeZone(Params.myTimeZone);
		mapper.setDateFormat(Params.myDateFormat);

		jsonConverter.setObjectMapper(mapper);
		return jsonConverter;
	}

	@Scheduled(fixedRate = 100)
	public static void init() {
		if (DeviceContainer.instance().size() >= Params.maxDevices) {
			return;
		}

		UUID uuid = null;

		if (DeviceContainer.instance().size() > 0 && DeviceContainer.instance().size() % Params.bhkwQuota == 0) {
			API<BHKWCreation, UUID> api = new API<BHKWCreation, UUID>(UUID.class);
			BHKWCreation bhkwCreation = new BHKWCreation(1, 1.02, 0.05, Params.startLoad);
			Params.startLoad = 0.4;
			api.devices().bhkw();
			api.call(Params.root, HttpMethod.POST, bhkwCreation);

			uuid = api.getResponse();
		} else {
			API<FridgeCreation, UUID> api = new API<FridgeCreation, UUID>(UUID.class);
			FridgeCreation fridgeCreation = new FridgeCreation(8, 9, 4, 2, -0.5, 0.2, 0.1, 5, 0.1);
			api.devices().fridge();
			api.call(Params.root, HttpMethod.POST, fridgeCreation);

			uuid = api.getResponse();
		}

		API<Void, UUID> api2 = new API<Void, UUID>(UUID.class);
		api2.consumers();
		api2.call(Params.root, HttpMethod.POST, null);

		API<Void, Void> api3 = new API<Void, Void>(Void.class);
		api3.consumers(api2.getResponse()).link(uuid);
		api3.call(Params.root, HttpMethod.POST, null);
		api3.clear();
		api3.devices(uuid).link(api2.getResponse());
		api3.call(Params.root, HttpMethod.POST, null);
	}

	@Scheduled(initialDelay = 5000, fixedRate = 1000)
	public static void pingAllDevices() {
		System.out.println("PING DEVICES:		" + DateTime.ToString(DateTime.now()));

		RestTemplate rest = new RestTemplate();

		ResponseEntity<Fridge[]> devices = rest.exchange(Params.BASE_URI + "/devices", HttpMethod.GET, null,
				Fridge[].class);

		for (Device d : devices.getBody()) {
			try {
				rest.exchange(Params.BASE_URI + "/devices/" + d.getUUID() + "/ping", HttpMethod.GET, null, Void.class);
			} catch (HttpServerErrorException e) {
			}
		}
	}

	@Scheduled(fixedRate = 1000)
	public static void pingAllConsumers() {
		System.out.println("PING CONSUMERS:		" + DateTime.ToString(DateTime.now()));
		RestTemplate rest = new RestTemplate();

		ResponseEntity<Consumer[]> consumers = rest.exchange(Params.BASE_URI + "/consumers", HttpMethod.GET, null,
				Consumer[].class);

		for (Consumer c : consumers.getBody()) {
			String url = Params.BASE_URI + "/consumers/" + c.getUUID() + "/ping";

			try {
				rest.exchange(url, HttpMethod.GET, null, Void.class);
			} catch (HttpServerErrorException e) {
			}
		}
	}

	@Scheduled(fixedRate = 5000)
	public static void pingMarketplace() {
		System.out.println("PING MARKETPLACE:	" + DateTime.ToString(DateTime.now()));
		RestTemplate rest = new RestTemplate();
		try {
			rest.exchange(Params.BASE_URI + "/marketplace/ping", HttpMethod.POST, null, Void.class);
		} catch (HttpServerErrorException e) {
		}
	}
}
