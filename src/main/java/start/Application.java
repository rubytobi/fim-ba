package start;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.TimeZone;
import java.util.UUID;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
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

@SpringBootApplication
@EnableScheduling
public class Application {
	public static class Params {
		// Setze die Anzahl an Devices der Simulation
		public static final int maxDevices = 4;

		// Jedes x-te Gerät ist ein BHKW
		public static final int bhkwQuota = 5;

		// Setze den Zeitfaktor, sodass die Simulationszeit schneller (>1) oder
		// langsamer (<1) als die reale Zeit vergeht
		// Beachte, dass bei einer sehr schnellen Simulationszeit die
		// Ping-Zeiten angepasst werden müssen!
		public static final double timeFactor = 1;

		// Lege fest, in welcher Minute die zweite Phase des Marktplatzes
		// starten soll
		public static final int marketplaceMinuteOfSecondPhase = 55;

		// Lege fest, ob mit DeltaLastprofilen gearbeitet werden soll
		public static final boolean enableDeltaLoadprofiles = false;

		public static final String BASE_URI = "http://localhost:8080";

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

		if (DeviceContainer.instance().size() % Params.bhkwQuota == 0) {
			API<BHKWCreation, UUID> api = new API<BHKWCreation, UUID>(UUID.class);
			BHKWCreation bhkwCreation = new BHKWCreation(1, 1.02, 0.05);
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
		System.out.println("PING DEVICES");

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
		System.out.println("PING CONSUMERS");
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
		System.out.println("PING MARKETPLACE");
		RestTemplate rest = new RestTemplate();
		try {
			rest.exchange(Params.BASE_URI + "/marketplace/ping", HttpMethod.POST, null, Void.class);
		} catch (HttpServerErrorException e) {
		}
	}
}
